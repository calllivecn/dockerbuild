#!/usr/bin/env python3
# coding=utf-8
# date 2022-05-13 08:32:37
# author calllivecn <calllivecn@outlook.com>


import sys
import time
import socket
import logging
import argparse
import traceback

from utils import (
    CFG,
    readcfg2,
    Request,
)

import logs

logger = logs.getlogger()


CONF="""\
[Client]
# 服务端地址，可以是域名，或者ipv6 ipv4
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
            logger.info(f"Server: {addr} Addr: {c_addr[0]}  verify ACK ok")
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

    parse.add_argument("--not-logtime", dest="logtime", action="store_false", help="默认日志输出时间戳，用systemd时可以取消。")

    args = parse.parse_args()

    if args.parse:
        print(args)
        sys.exit(0)
    
    if not args.logtime:
        logs.set_handler_fmt(logs.stdoutHandler, logs.FMT)
    
    if args.debug:
        logger.setLevel(logging.DEBUG)
    
    cfg = readcfg2(CFG, CONF)

    c = cfg["Client"]
    addr = c["Address"]
    port = c["Port"]

    interval = c["Interval"]
    clientid = c["ClientId"]

    secret = c["Secret"]
    server_secret = c["ServerSecret"]

    timeout = c["TimeOut"]
    retry = c["Retry"]
    

    while True:
        try:
            client(addr, port, clientid, secret, server_secret, retry, timeout)
        except Exception:
            logger.warning(f"有异常：")
            traceback.print_exc()

        logger.debug(f"sleep({interval}) ...")
        time.sleep(interval)


if __name__ == "__main__":
    main()

