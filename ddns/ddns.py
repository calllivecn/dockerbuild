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

import pprint

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
    NoOptionError,
)

from libnetlink import NetLink

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
    """
    cache: 目前不需要 一个域名对应多个ip
    {
        "domain1": {
            "record_id1": "ip1",
            "record_id2": "ip2",
            ...
        },
        ...

        "domain2": {
            "record_id1": "ip1",
            "record_id2": "ip2",
            ...
        }
    }

    cache:
    {
        "domain1": "ip1",
        "domain2": "ip2",
        "domain3": "ip3",
    }
    """

    def __init__(self):

        self.filepath = PWD / (NAME + ".cache")

        self.cache = {}

        if self.is_file():
            if self.filepath.lstat().st_size <= (1<<20):
                with open(self.filepath, "r") as f:
                    self.cache = json.load(f)
            else:
                logger.warning(f"{self.filepath} 文件大小不正常。")
                raise ValueError(f"{self.filepath} 件大小不正常。")
        
        else:
            with open(self.filepath, "w") as f:
                json.dump(self.cache, f, ensure_ascii=False, indent=4)
            


    def get(self, domain) -> str:
        """
        return: ip_cache or ""
        """

        logger.debug(f"{self.cache=}")

        ip_cache = self.cache.get(domain)
        if ip_cache is None:
            return ""
        else:
            return ip_cache
    

    def set(self, domain, ip):
        self.cache[domain] = ip
        with open(self.filepath, "w") as f:
            json.dump(self.cache, f)


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
        if t is None:
            cfg = {}
            cfg["Type"] = self.conf.get(sdn, "Type")
            cfg["RR"] = self.conf.get(sdn, "RR")
            cfg["Domain"] = self.conf.get(sdn, "Domain")
            cfg["Secret"] = self.conf.get(sdn, "Secret")
            self.multidns[sdn] = [cfg]
        else:
            self.multidns[sdn] = self.__load_json(MultiDNS / t)
        
    
    def __clientids_cfg(self):
        self.clientids = [ int(x[1]) for x in self.conf.items("Clients") ]

        for cid in self.clientids:
            try:
                t = self.conf.get(str(cid), "multidns")
                self.multidns[cid] = self.__load_json(MultiDNS / t)
            except NoOptionError:
                cfg = {}
                try:
                    cfg["Type"] = self.conf.get(str(cid), "Type")
                    cfg["RR"] = self.conf.get(str(cid), "RR")
                    cfg["Domain"] = self.conf.get(str(cid), "Domain")
                    cfg["Secret"] = self.conf.get(str(cid), "Secret")
                    self.multidns[cid] = [cfg]
                except NoOptionError:
                    logger.warning(f"ClientID:{cid} 配置错误.")
                    sys.exit(1)


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
    


def update_dns(alidns, rr, typ, domain, ip):
    """
    return: False or dns_record_id 
    """

    dns = ".".join([rr, domain])

    result = alidns.describe_sub_domain(dns, typ)

    logger.debug(f"\nsub_domain_record: {pprint.pformat(result)}")

    # 这里可能会查询到0条或多条记录
    if len(result["DomainRecords"]["Record"]) == 0:
        logger.info(f"添加记录: {dns=} --> {ip=}")
        result = alidns.addDomainRecord(domain, rr, typ, ip)
        dns_record_id = result["RecordId"]
        
        # 更新缓存
        # ip_dnsid_cache.set(dns, dns_record_id, ip)
        ip_dnsid_cache.set(dns, ip)
        return dns_record_id

    elif len(result["DomainRecords"]["Record"]) == 1:
        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

        ip_value = result["DomainRecords"]["Record"][0]["Value"]

        logger.debug(f"{dns_record_id=}")

        if ip == ip_value:
            logger.debug(f"域名和ip相同，不用更新: {dns=} {ip=}")
        else:

            logger.info(f"更新记录: {dns=} --> {ip=}")

            try:
                result = alidns.updateDonameRecord(dns_record_id, rr, typ, ip)
            except Exception as e:
                logger.warning(f"异信息: {e}")
                logger.warning(f"updateDonameRecord() --> {result}")
                return False

        ip_dnsid_cache.set(dns, ip)

        return dns_record_id

    elif len(result["DomainRecords"]["Record"]) > 1:
        logger.warning(f"{typ=} {dns=} 有多个ip地址, 暂时还不支持一个域名对应多个地址")
        return False

    else:
        logger.warning(f"当前能查询到多条记录，可能需要更准确的查询，才能正常工作。")
        return False



def multi_update_dns(alidns: AliDDNS, multidns: list, ip: str):
    for info in multidns:
        update_dns(alidns, info["RR"], info["Type"], info["Domain"], ip)


def self_ddns(alidns, conf):

    multidns = conf.multidns["SelfDomainName"]

    # 多个域名都是指向同一个ip的，只需要拿第一个对比就行
    dns = ".".join([multidns[0]["RR"], multidns[0]["Domain"]])
    
    ipv6 = get_self_ipv6()
    logger.info(f"获取本机ipv6地址：{ipv6}")

    ip_cache = ip_dnsid_cache.get(dns)
    
    if ipv6 == ip_cache:
        logger.debug("与缓存相同，不用更新.")
    else:
        multi_update_dns(alidns, multidns, ipv6)


def server_self_ddns(conf: Conf):

    interval = conf.server_interval

    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)

    while True:
        try:
            self_ddns(alidns, conf)
        except Exception:
            logger.warning(f"异常:")
            traceback.print_exc()

        logger.debug(f"sleep({interval}) ...")
        time.sleep(interval)



# 使用新的检测IP变化的方式，是阻塞式的。
def server_self_ddns_v2(conf: Conf):

    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)
    netlink = NetLink()

    multidns = conf.multidns["SelfDomainName"]

    # 多个域名都是指向同一个ip的，只需要拿第一个对比就行
    dns = ".".join([multidns[0]["RR"], multidns[0]["Domain"]])
    
    while True:

        # 阻塞式，等待系统IP地址更新。
        netlink.monitor()

        while True:
            try:
                ipv6 = get_self_ipv6()
            except OSError:
                time.sleep(1)
            
            break

        logger.info(f"获取本机ipv6地址：{ipv6}")

        ip_cache = ip_dnsid_cache.get(dns)

        if ipv6 == ip_cache:
            logger.debug("与缓存相同，不用更新.")
        else:
            multi_update_dns(alidns, multidns, ipv6)


    netlink.close()

def server(conf: Conf):

    logger.debug(f"server listen: [{conf.server_addr}]:{conf.server_port}")

    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)

    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.bind((conf.server_addr, conf.server_port))

    while True:
        data, addr = sock.recvfrom(8192)
        ip = addr[0]
        req = Request()
        try:
            req.frombuf(data)
        except DDNSPacketError as e:
            logger.warning(f"Error: {e}\n请求验证失败，可能有人在探测: {ip=}")
            continue
        
        client_secret = conf.get(str(req.id_client), "Secret")

        if client_secret is not None and req.verify(client_secret):
            logger.debug(f"Cache={conf.client_cache}")
            c_check = conf.cache_check(req.id_client, ip)
            logger.info(f"接收到请求: ClientID={req.id_client} {ip=}")

            if c_check == 0:

                # 回复client ACK
                logger.debug(f"回复ACK")
                sock.sendto(req.ack(conf.server_secret), addr)

                domains = conf.get_multidns_info(req.id_client)

                # 使用线程更新
                th = Thread(target=multi_update_dns, args=(alidns, domains, ip), daemon=True)
                th.start()

            elif c_check == 1:
                logger.warning(f"没有对应的: ClientID={req.id_client} {ip=}")

            elif c_check == 2:
                sock.sendto(req.ack(conf.server_secret), addr)
                logger.debug(f"请求太频繁(间隔小小于30秒): ClientID={req.id_client} {ip=}")

            elif c_check == 3:
                sock.sendto(req.ack(conf.server_secret), addr)
                logger.debug(f"当前ip没有改变: ClientID={req.id_client} {ip=}")

        else:
            logger.warning(f"请求验证失败，可能有人在探测: {ip=}")




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

    # th_server_self_ddns = Thread(target=server_self_ddns, args=(conf,), daemon=True, name="Server Self DDNS")
    th_server_self_ddns = Thread(target=server_self_ddns_v2, args=(conf,), daemon=True, name="Server Self DDNS")
    th_server_self_ddns.start()

    th_server = Thread(target=server, args=(conf,), daemon=True, name="Server")
    th_server.start()

    logger.debug(f"服务端启动完成...")

    try:
        th_server_self_ddns.join()
        th_server.join()
    except Exception as e:
        raise e

if __name__ == '__main__':
    main()
