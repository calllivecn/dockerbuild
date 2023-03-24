#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <c-all@qq.com>

import os
import sys
import time
import socket
import logging
import argparse
import traceback
from pathlib import Path
from threading import Thread
from turtle import update

from aliyunlib import AliDDNS

import logs
from utils import (
    Request,
    readcfg,
    get_self_ipv6,
    DDNSPacketError,
)


CONF="""\
[Ali]
# 阿里云
AccessKeyId=
AccessKeySecret=

[DomainName]
# 例如域名是：dns.example.com
# RR: dns
RR=
# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
Type=
# Domain: example.com
Domain=

# 检查间隔时间单位秒
Interval=180

[Server]
Address="::"
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
# RR: dns
RR=

# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
Type=

# Domain: example.com
Domain=

"""

PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

name, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (name + ".conf")
CACHE = PWD / (name + ".cache")


"""
class Cache:

    def __init__(self, filepath: Path):

        self.filepath = filepath

    def get()
"""


def get_cache(filepath):
    if filepath.lstat().st_size <= 4096:
        with open(filepath, "r") as f:
            cache = f.read()

        logger.debug(f"cache值：{cache}")
        ip_cache , dns_record_id = cache.split(" ")
        return ip_cache, dns_record_id
    else:
        logger.warning(f"{CACHE} 文件大小不正常。")
        raise ValueError(f"{CACHE} 件大小不正常。")
    

def set_cache(filepath, ipv6, dns_record_id):
    with open(filepath, "w") as f:
        f.write(" ".join([ipv6, dns_record_id]))


def update_dns(alidns, rr, typ, domain, ipv6):
    """
    return: False or dns_record_id 
    """

    dns = ".".join([rr, domain])

    result = alidns.describe_sub_domain(".".join([rr, domain]), typ)

    logger.debug(f"sub_domain_record: {result}, type: {type(result)}")

    # 这里可能会查询到0条或多条记录
    if len(result["DomainRecords"]["Record"]) == 0:
        logger.info(f"没有 {dns} 现在创建。 ip: {ipv6}")
        result = alidns.addDomainRecord(domain, rr, typ, ipv6)
        return result["RecordId"]

    elif len(result["DomainRecords"]["Record"]) == 1:
        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

    else:
        logger.error(f"当前能查询到多条记录，可能需要更准确的查询，才能正常工作。")
        return False

    logger.debug(f"dns_record_id: {dns_record_id}")
    
    logger.info(f"更新ipv6: {dns} --> {ipv6}")

    try:
        result = alidns.updateDonameRecord(dns_record_id, rr, typ, ipv6)
    except Exception as e:
        logger.debug(f"异信息: {e}")
        logger.debug(f"updateDonameRecord() --> {result}")

    return dns_record_id


def callddns(alidns, conf):

    domain = conf["DomainName"]
    dns = ".".join([domain["RR"], domain["Domain"]])
    
    ipv6 = get_self_ipv6()
    logger.info(f"获取本机ipv6地址：{ipv6}")

    if not CACHE.is_file():
        logger.warning(f"{CACHE} 不存在, 需要第一次更新。")

        logger.debug(f"dns_record_id: {dns_record_id}")
        
        logger.info(f"更新ipv6: {dns} --> {ipv6}")

        result = update_dns(alidns, domain["RR"], domain["Type"], domain["Domain"], ipv6)

        if result:
            logger.debug(f"写入缓存: {dns_record_id} ")
            # write cache
            with open(CACHE, "w") as f:
                f.write(" ".join([ipv6, dns_record_id]))
        else:
            return

    ip_cache , dns_record_id = get_cache(CACHE)
    
    if ipv6 == ip_cache:
        logger.debug("与缓存相同，不用更新.")
    else:

        logger.info(f"更新ipv6: {dns} --> {ipv6}")

        result = update_dns(alidns, domain["RR"], domain["Type"], domain["Domain"], ipv6)

        logger.debug(f"更新cache: {ipv6} {dns_record_id}")
        set_cache(CACHE, ipv6, dns_record_id)


def serverdns(conf):

    interval = conf.getint("DomainName", "Interval")
    keyid = conf.get("Ali", "AccessKeyId")
    keysecret = conf.get("Ali", "AccessKeySecret")

    alidns = AliDDNS(keyid, keysecret)

    while True:
        logger.debug(f"检查和更新...")

        try:
            callddns(alidns, conf)
        except Exception:
            logger.warning(f"异常:")
            traceback.print_exc()

        logger.debug(f"sleep({interval}) ...")
        time.sleep(interval)


class ClientCache:
    """
    放内存吧。
    return: 0: 更新， 1: 没有对应client, 2: 更新太频繁, 3：ip 没变化
    """

    def __init__(self, clientids):
        self.cache = {}

        for c in clientids:
            # timestamp, ip
            self.cache[c] = [0, None]
        
    def check(self, id_client, cur_ip):
        result = self.cache.get(id_client)

        if result is None:
            return 1

        t, cache_ip = result

        cur = time.time()
        if t != 0 and (cur - t) <= 30:
            return 2

        if cur_ip != cache_ip:
            self.cache[id_client] = [cur, cur_ip]
            return 0
        else:
            return 3



def server(conf):

    # 拿到client id
    clientids = [ int(x[1]) for x in conf.items("Clients") ]
    Cache = ClientCache(clientids)
    logger.debug(f"init Cache: {Cache.cache}")

    server_addr = conf.get("Server", "Address")
    port = conf.getint("Server", "Port")
    server_secret = conf.get("Server", "Secret")

    logger.debug(f"server listen: [{server_addr}]:{port}")

    keyid = conf.get("Ali", "AccessKeyId")
    keysecret = conf.get("Ali", "AccessKeySecret")

    alidns = AliDDNS(keyid, keysecret)

    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.bind((server_addr, port))

    while True:
        data, addr = sock.recvfrom(1024)
        ip = addr[0]
        req = Request()
        try:
            req.frombuf(data)
        except DDNSPacketError as e:
            logger.warning(f"{addr}: 请求验证失败，可能有人在探测。Error: {e}")
            continue

        logger.debug(f"Cache: {Cache.cache}")
        c_check = Cache.check(req.id_client, ip)

        if c_check == 0:
            logger.debug(f"接收到 clientID:{req.id_client} {ip} 的请求")

            secret = conf.get(str(req.id_client), "Secret")

            if req.verify(secret):
                # 回复client ACK
                logger.debug(f"回复ACK")
                sock.sendto(req.ack(server_secret), addr)

                rr = conf.get(str(req.id_client), "RR")
                typ = conf.get(str(req.id_client), "Type")
                domain = conf.get(str(req.id_client), "Domain")

                dns = ".".join([rr, domain])
                logger.info(f"更新ip: {dns} --> {ip}")
                # 使用线程更新
                th = Thread(target=update_dns, args=(alidns, rr, typ, domain, ip), daemon=True)
                th.start()

            else:
                logger.warning(f"{ip}: 请求验证失败，可能有人在探测。")

        elif c_check == 1:
            logger.warning(f"{ip}: 请求验证失败，可能有人在探测。")
            
        elif c_check == 2:
            sock.sendto(req.ack(server_secret), addr)
            logger.debug(f"{ip}: 请求太频繁(间隔小小于30秒)。")

        elif c_check == 3:
            sock.sendto(req.ack(server_secret), addr)
            logger.debug(f"{ip}: 当前ip没有改变。")




def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="使用阿里 DNS 做 DDNS.",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)
    parse.add_argument("--without-logtime", dest="logtime", action="store_false", help="默认日志输出时间戳，用systemd时可以取消。")

    args = parse.parse_args()

    global logger
    logger = logs.getlogger(logtime=args.logtime)

    if args.debug:
        logger.setLevel(logging.DEBUG)

    conf = readcfg(CFG, CONF)

    th_serverdns = Thread(target=serverdns, args=(conf,), daemon=True, name="Server DDNS")
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
