#!/usr/bin/env python3
# coding=utf-8
# date 2022-05-13 08:32:37
# author calllivecn <c-all@qq.com>


import os
import sys
import time
import socket
import argparse
from pathlib import Path

from utils import (
    logger,
    readcfg,
    Request,
    DDNSPacketError,
)

CONF="""\
[client]
# 可以是域名，和ipv6
Address=
Port=2022

# 检查间隔时间单位秒
Interval=180

# 在服务商端是唯一的
ClientId=

# client 的 secret
Secret=

# 等待ACK的超时时间
TimeOut=10

# 没有收到ACK时，重试次数
Retry=3
"""

PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

name, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (name + ".conf")
CACHE = PWD / (name + ".cache")


def makesock(addr, port=2022):
    # 自动检测是ipv4 ipv6
    sock = None
    for res in socket.getaddrinfo(addr, port, socket.AF_UNSPEC, socket.SOCK_DGRAM, 0, socket.AI_PASSIVE):
        af, socktype, proto, canonname, sa = res
        try:
            sock = socket.socket(af, socktype, proto)
        except OSError as msg:
            sock = None
            continue
        break

    if sock is None:
        logger.critical('could not open socket')
        sys.exit(1)

    return sock


def client(addr, port, id, secret, retry, timeout):
    req = Request()
    buf = req.make(id, secret)

    sock = makesock(addr, port)
    sock.settimeout(timeout)

    for i in range(1, retry+1):
        sock.sendto(buf, (addr, port))
        try:
            data_ack, addr = sock.recvfrom(512)
        except TimeoutError:
            logger.warning(f"retry {i}/{retry}")
        
        if req.verifyAck(data_ack, secret):
            logger.info(f"{addr}: update ok")
            break
        else:
            logger.warning(f"{addr}: 收到的回复验证不通过！可能正在被探测。")
    
    sock.close()



def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="DDNS client, "
        f"使用 {CFG} 为配置文件, "
        f" {CACHE} 为缓存文件, ",
        epilog="",
    )

    # parse.add_argument("--config", required=True, help="配置文件")
    parse.add_argument("--parse", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.parse:
        print(args)
        sys.exit(0)
    
    cfg = readcfg(CFG, CONF)

    addr = cfg.get("Client", "Address")
    port = cfg.getint("Client", "Port")
    interval = cfg.getint("Client", "Interval")
    clientid = cfg.get("Client", "ClientId")
    secret = cfg.get("Client", "Secret")
    timeout = cfg.getfloat("Client", "TimeOut")
    retry = cfg.getint("Client", "Retry")

    while True:
        client(addr, port, clientid, secret, retry, timeout)
        time.sleep(interval)


if __name__ == "__main__":
    main()

