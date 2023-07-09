#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <c-all@qq.com>

import os
import sys
import time
import json
import socket
import logging
import argparse
import traceback
from pathlib import Path
from threading import Thread

from typing import List, Dict

from aliyunlib import AliDDNS

from utils import (
    PWD,
    CFG,
    NAME,
    Request,
    readcfg,
    get_self_ipv6,
    DDNSPacketError,
)

import logs

logger = logs.getlogger()

MultiDNS = PWD / "multidns"

if not MultiDNS.is_dir():
    os.mkdir(MultiDNS)

CONF="""\
[Ali]
# 阿里云
AccessKeyId=
AccessKeySecret=

[SelfDomainName]
# 检测server自己所在机器ip, 并更新指向自己的域名
# 例如域名是：dns.example.com
# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
Type=
# RR: dns
RR=
# Domain: example.com
Domain=

# 检查间隔时间单位秒
Interval=180

# 如果有多个记录需要更新为同一ip，使json文件配置(放在当前目录"multidns/")
# 和前面的 RR= Type= Domain= 一个。如果同时配置，优先使用 multidns 。
;multidns=client1.json

# json格式如下:
# [
#     {"Type": "AAAA", "RR": "dns1", "Domain": "example.com"},
#     {"Type": "AAAA", "RR": "dns2", "Domain": "example.com"},
#     {"Type": "AAAA", "RR": "dns3", "Domain": "example.com"},
# ]


[Server]
# 默认listen ipv4 ipv6 双栈
Address=::
Port=2022
# server 的 secret
Secret=

[Clients]
# 其他轻客户端的UUID (预计使用很少的 bash 就可以实现; bash 不行，不能接收UDP数据包。。。还是需要用golang和py写)
# 范围： 4字节 无符号
ClientID1=
ClientID2=
# 更多client一直添加...


# [上面的 clientID1 value]
# client 的 secret
Secret=

# 例如域名是：dns.example.com

# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
Type=

# RR: dns
RR=

# Domain: example.com
Domain=

# 如果有多个记录需要更新为同一ip，使json文件配置(放在当前目录"multidns/")
# 和前面的 RR= Type= Domain= 一个。如果同时配置，优先使用 multidns 。
;multidns=client1.json

# json格式如下:
# [
#     {"Type": "AAAA", "RR": "dns1", "Domain": "example.com"},
#     {"Type": "AAAA", "RR": "dns2", "Domain": "example.com"},
#     {"Type": "AAAA", "RR": "dns3", "Domain": "example.com"},
# ]

"""



class SelfIPCache:

    def __init__(self):

        self.filepath = PWD / (NAME + ".cache")

    def get(self):
        if self.filepath.lstat().st_size <= (1<<20):
            with open(self.filepath, "r") as f:
                cache = f.read()

            logger.debug(f"cache值：{cache}")
            ip_cache , dns_record_id = cache.split(" ")
            return ip_cache, dns_record_id
        else:
            logger.warning(f"{self.filepath} 文件大小不正常。")
            raise ValueError(f"{self.filepath} 件大小不正常。")
    
    def set(self, ipv6, dns_record_id):
        with open(self.filepath, "w") as f:
            f.write(" ".join([ipv6, dns_record_id]))


    def is_file(self):
        if self.filepath.is_file():
            return True
        else:
            return False

ip_dnsid_cache = SelfIPCache()


class Conf:

    def __init__(self):
        self.conf = readcfg(CFG, CONF)

        self.clientids = []

        self.multidns = {}

        self.__server_cfg()

        self.__clientids_cfg()
    

        self.client_cache = {}
        for c in self.clientids:
            # timestamp, ip
            self.client_cache[c] = [0, None]


    def __server_cfg(self):
        self.ali_keyid = self.conf.get("Ali", "AccessKeyId")
        self.ali_keysecret = self.conf.get("Ali", "AccessKeySecret")

        self.server_addr = self.conf.get("Server", "Address")
        self.server_port = self.conf.getint("Server", "Port")
        self.server_secret = self.conf.get("Server", "Secret")

        sdn="SelfDomainName"
        self.server_interval = self.conf.getint(sdn, "Interval")
        # 可能使用 multidns json
        t = self.conf.get(sdn, "multidns")
        if t is not None:
            self.multidns[sdn] = self.__load_json(MultiDNS / t)
        else:
            cfg = {}
            cfg["Type"] = self.conf.get(sdn, "Type")
            cfg["RR"] = self.conf.get(sdn, "RR")
            cfg["Domain"] = self.conf.get(sdn, "Domain")
            cfg["Secret"] = self.conf.get(sdn, "Secret")
            self.multidns[sdn] = [cfg]
        
    
    def __clientids_cfg(self):
        self.clientids = [ int(x[1]) for x in self.conf.items("Clients") ]

        for cid in self.clientids:
            t = self.conf.get(str(cid), "multidns")
            if t is not None:
                self.multidns[cid] = self.__load_json(MultiDNS / t)
            else:
                cfg = {}
                cfg["Type"] = self.conf.get(str(cid), "Type")
                cfg["RR"] = self.conf.get(str(cid), "RR")
                cfg["Domain"] = self.conf.get(str(cid), "Domain")
                cfg["Secret"] = self.conf.get(str(cid), "Secret")
                self.multidns[cid] = [cfg]


    def __load_json(self, filepath: Path):
        if filepath.exists():
            with open(filepath) as f:
                return json.load(f)
        else:
            raise FileNotFoundError(filepath)
    
    def get(self, *args, **kwargs):
        return self.conf.get(*args, **kwargs)

    def getint(self, *args, **kwargs):
        return self.conf.getint(*args, **kwargs)

    def get_multidns_info(self, id: int) -> List[Dict[str, str]]:
        return self.multidns.get(id)


    def cache_check(self, id_client, cur_ip):
        """
        放内存吧。
        return: 0: 更新， 1: 没有对应client, 2: 更新太频繁, 3：ip 没变化
        """
        result = self.client_cache.get(id_client)

        if result is None:
            return 1

        t, cache_ip = result

        cur = time.time()
        if t != 0 and (cur - t) <= 30:
            return 2

        if cur_ip != cache_ip:
            self.client_cache[id_client] = [cur, cur_ip]
            return 0
        else:
            return 3
    
    def cache_update(self, id_client, cur_ip):
        cur = time.time()
        self.cache_check[id_client] = [cur, cur_ip]


def update_dns(alidns, rr, typ, domain, ip):
    """
    return: False or dns_record_id 
    """

    dns = ".".join([rr, domain])

    result = alidns.describe_sub_domain(".".join([rr, domain]), typ)

    logger.debug(f"sub_domain_record: {result}, type: {type(result)}")

    # 这里可能会查询到0条或多条记录
    if len(result["DomainRecords"]["Record"]) == 0:
        logger.info(f"没有 {dns} 现在创建。 ip: {ip}")
        result = alidns.addDomainRecord(domain, rr, typ, ip)
        return result["RecordId"]

    elif len(result["DomainRecords"]["Record"]) == 1:
        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

    else:
        logger.warning(f"当前能查询到多条记录，可能需要更准确的查询，才能正常工作。")
        return False

    logger.debug(f"dns_record_id: {dns_record_id}")
    
    logger.info(f"更新ip: {dns} --> {ip}")

    try:
        result = alidns.updateDonameRecord(dns_record_id, rr, typ, ip)
    except Exception as e:
        logger.warning(f"异信息: {e}")
        logger.warning(f"updateDonameRecord() --> {result}")
        return False

    return dns_record_id


def multi_update_dns(alidns: AliDDNS, multidns: list, ip: str):
    for info in multidns:
        dns = ".".join([info["RR"], info["Domain"]])
        logger.info(f"更新ip: {dns} --> {ip}")
        update_dns(alidns, info["RR"], info["Type"], info["Domain"], ip)


def self_ddns(alidns, conf):

    self_domains = conf.multidns["SelfDomainName"]
    
    ipv6 = get_self_ipv6()
    logger.info(f"获取本机ipv6地址：{ipv6}")

    if not ip_dnsid_cache.is_file():
        logger.debug(f"{ip_dnsid_cache.filepath} 不存在, 需要第一次更新。")

        logger.debug(f"dns_record_id: {dns_record_id}")
        
        result = multi_update_dns(alidns, self_domains, ipv6)

        if result:
            logger.debug(f"写入缓存: {dns_record_id} ")
            # write cache
            ip_dnsid_cache.set(ipv6, dns_record_id)
        else:
            return

    ip_cache , dns_record_id = ip_dnsid_cache.get()
    
    if ipv6 == ip_cache:
        logger.debug("与缓存相同，不用更新.")
    else:

        result = multi_update_dns(alidns, self_domains, ipv6)

        logger.debug(f"更新cache: {ipv6} {dns_record_id}")
        ip_dnsid_cache.set(ipv6, dns_record_id)


def server_self_ddns(conf: Conf):

    interval = conf.server_interval

    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)

    while True:
        logger.debug(f"检查和更新...")

        try:
            self_ddns(alidns, conf)
        except Exception:
            logger.warning(f"异常:")
            traceback.print_exc()

        logger.debug(f"sleep({interval}) ...")
        time.sleep(interval)



def server(conf: Conf):

    logger.debug(f"server listen: [{conf.server_addr}]:{conf.server_port}")

    keyid = conf.get("Ali", "AccessKeyId")
    keysecret = conf.get("Ali", "AccessKeySecret")

    alidns = AliDDNS(keyid, keysecret)

    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.bind((conf.server_addr, conf.server_port))

    while True:
        data, addr = sock.recvfrom(8192)
        ip = addr[0]
        req = Request()
        try:
            req.frombuf(data)
        except DDNSPacketError as e:
            logger.warning(f"请求验证失败，可能有人在探测。Error: {e} ip:{ip}")
            continue
        
        client_secret = conf.get(str(req.id_client), "Secret")

        if client_secret is not None and req.verify(client_secret):
            logger.debug(f"Cache: {conf.client_cache}")
            c_check = conf.cache_check(req.id_client, ip)

            if c_check == 0:
                logger.debug(f"接收到 clientID:{req.id_client} {ip} 的请求")

                # 回复client ACK
                logger.debug(f"回复ACK")
                sock.sendto(req.ack(conf.server_secret), addr)

                domains = conf.get_multidns_info(req.id_client)

                # 使用线程更新
                th = Thread(target=multi_update_dns, args=(alidns, domains, ip), daemon=True)
                th.start()

            elif c_check == 1:
                logger.warning(f"没有对应的clientID: {req.id_client} ip:{ip}")

            elif c_check == 2:
                sock.sendto(req.ack(conf.server_secret), addr)
                logger.debug(f"请求太频繁(间隔小小于30秒): ip:{ip}")

            elif c_check == 3:
                sock.sendto(req.ack(conf.server_secret), addr)
                logger.debug(f"当前ip没有改变: {ip}")

        else:
            logger.warning(f"请求验证失败，可能有人在探测: ip:{ip}")




def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="使用阿里 DNS 做 DDNS.",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)
    parse.add_argument("--parse", action="store_true", help=argparse.SUPPRESS)
    parse.add_argument("--not-logtime", dest="logtime", action="store_false", help="默认日志输出时间戳，用systemd时可以取消。")

    args = parse.parse_args()

    if args.parse:
        print(args)
        sys.exit(0)

    if not args.logtime:
        logs.set_handler_fmt(logs.stdoutHandler, logs.FMT)

    if args.debug:
        logger.setLevel(logging.DEBUG)

    conf = Conf()

    th_serverdns = Thread(target=server_self_ddns, args=(conf,), daemon=True, name="Server DDNS")
    th_serverdns.start()

    th_server = Thread(target=server, args=(conf,), daemon=True, name="Server")
    th_server.start()

    logger.debug(f"服务端启动完成...")

    try:
        th_serverdns.join()
        th_server.join()
    except Exception as e:
        raise e

if __name__ == '__main__':
    main()
