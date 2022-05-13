#!/usr/bin/env python3
# coding=utf-8
# date 2022-05-13 08:32:37
# author calllivecn <c-all@qq.com>


import os
import sys
import time
import socket
import argparse
import logging
from pathlib import Path

from utils import (
    logger,
    readcfg,
    Request,
    DDNSPacketError,
)

CONF="""\
[Client]
# 可以是域名，和ipv6
Address=
Port=2022

# 检查间隔时间单位秒
Interval=180

# 在服务端需要是唯一的
ClientId=

# client 的 secret
Secret=

# server 的 secret
ServerSecret=

# 等待ACK的超时时间
TimeOut=10

# 没有收到ACK时，重试次数
Retry=3
"""

PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

name, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (name + ".conf")

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


def client(addr, port, id, secret, server_secret, retry, timeout):
    req = Request()
    buf = req.make(id, secret)

    sock = makesock(addr, port)
    sock.settimeout(timeout)

    for i in range(1, retry+1):
        logger.info(f"retry {i}/{retry}")
        sock.sendto(buf, (addr, port))
        try:
            data_ack, c_addr = sock.recvfrom(512)
        except TimeoutError:
            continue
        
        if req.verifyAck(data_ack, server_secret):
            logger.info(f"Server:{c_addr[0]}  verify ACK ok")
            break
        else:
            logger.warning(f"{c_addr[0]}: 收到的回复验证不通过！可能正在被探测。")
    
    sock.close()



def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="DDNS client, "
        f"使用 {CFG} 为配置文件",
        epilog="",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)
    # parse.add_argument("--config", required=True, help="配置文件")
    parse.add_argument("--parse", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.parse:
        print(args)
        sys.exit(0)
    
    if args.debug:
        logger.setLevel(logging.DEBUG)
    
    cfg = readcfg(CFG, CONF)

    addr = cfg.get("Client", "Address")
    port = cfg.getint("Client", "Port")

    interval = cfg.getint("Client", "Interval")
    clientid = cfg.getint("Client", "ClientId")

    secret = cfg.get("Client", "Secret")
    server_secret = cfg.get("Client", "ServerSecret")

    timeout = cfg.getfloat("Client", "TimeOut")
    retry = cfg.getint("Client", "Retry")
    

    while True:
        client(addr, port, clientid, secret, server_secret, retry, timeout)
        logger.info(f"sleep({interval}) ...")
        time.sleep(interval)


if __name__ == "__main__":
    main()

