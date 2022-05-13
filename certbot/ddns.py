#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <c-all@qq.com>

import os
import io
import sys
import time
import socket
import struct
import hashlib
import logging
import argparse
import traceback
import configparser
from pathlib import Path


from utils import (
    logger,
    get_self_ipv6,
    AliDDNS,
    DDNSPacketError,
)


def server():
    pass


CONF="""\
[DDNS]
# 检查间隔时间单位秒
Interval=180
# server 的 secret
Secret=

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

[Clients]
# 其他轻客户端的UUID (预计使用很少的 bash 就可以实现; bash 不行，不能接收UDP数据包。。。还是需要用golang和py写)
clientID1=
clientID2=
# 更多client一直添加...


[clientID1]
# client 的 secret
secret=
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

def readcfg():
    if CFG.exists() and CFG.is_file():
        conf = configparser.ConfigParser()
        conf.read(str(CFG))
    else:
        with open(CFG, "w") as f:
            f.write(CONF)
        
        logger.warning(f"需要配置{CFG}文件")
        sys.exit(1)
    
    return conf


def callddns(conf):

    ali = conf["Ali"]

    domain = conf["DomainName"]
    dns = ".".join([domain["RR"], domain["Domain"]])
    
    ipv6 = get_self_ipv6()
    logger.info(f"获取本机ipv6地址：{ipv6}")

    if not CACHE.is_file():
        logger.warning(f"{CACHE} 不存在, 需要第一次更新。")

        alidns = AliDDNS(ali["AccessKeyId"], ali["AccessKeySecret"])

        result = alidns.describe_sub_domain(".".join([domain["rr"], domain["Domain"]]), domain["Type"])

        logger.debug(f"sub_domain_record: {result}, type: {type(result)}")

        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

        logger.debug(f"dns_record_id: {dns_record_id}")
        
        logger.info(f"更新ipv6: {dns} --> {ipv6}")
        alidns.updateDonameRecord(dns_record_id, domain["RR"], domain["Type"], ipv6)

        logger.debug(f"写入缓存: {dns_record_id} ")
        # write cache
        with open(CACHE, "w") as f:
            f.write(" ".join([ipv6, dns_record_id]))
        
        return None


    if CACHE.lstat().st_size <= 4096:
        with open(CACHE, "r") as f:
            cache = f.read()

        logger.debug(f"cache值：{cache}")
        ip_cache , dns_record_id = cache.split(" ")

    else:
        logger.warning(f"{CACHE} 不存在，或者文件大小不正常。")

        raise ValueError(f"{CACHE} 不存在，或者文件大小不正常。")
    
    
    if ipv6 == ip_cache:
        logger.info("与缓存相同，不用更新.")
    else:
        alidns = AliDDNS(ali["AccessKeyId"], ali["AccessKeySecret"])

        logger.info(f"更新ipv6: {dns} --> {ipv6}")
        alidns.updateDonameRecord(dns_record_id, domain["RR"], domain["Type"], ipv6)

        logger.debug(f"更新cache: {ipv6} {dns_record_id}")
        with open(CACHE, "w") as f:
            f.write(" ".join([ipv6, dns_record_id]))


def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="使用阿里 DNS 做 DDNS.",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.debug:
        logger.setLevel(logging.DEBUG)

    conf = readcfg()

    INTERVAL = conf.getint("DDNS", "Interval")

    while True:
        logger.info(f"检查和更新...")

        try:
            callddns(conf)
        except Exception:
            logger.warning(f"异常:")
            traceback.print_exc()

        logger.info(f"sleep({INTERVAL}) ...")
        time.sleep(90)


if __name__ == '__main__':
    main()
