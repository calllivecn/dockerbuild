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
from libnetlink import DefaultRouteIP
import getipcmd
import logs

logger = logs.getlogger()


CONF="""\
[Client]
# 服务端地址，可以是域名，或者ipv6 ipv4
Address=""
Port=2022

# 检查间隔时间单位秒
Interval=180

# 在服务端需要是唯一的
ClientId=""

# client 的 secret
Secret=""

# server 的 secret
ServerSecret=""

# 等待ACK的超时时间
TimeOut=10

# 没有收到ACK时，重试次数
Retry=3

# 获取IP的命令行脚本
# 例如：/usr/local/bin/getip6.sh
# 如果不设置, 则使用默认内部方法,拿到默认路由接口的ip, 只支持ipv6。
# Cmd=""
"""


def makesock(host: str, port: int=2022):
    # 自动检测服务端地址是ipv4 ipv6
    sock = None
    for res in socket.getaddrinfo(host, port, socket.AF_UNSPEC, socket.SOCK_DGRAM, 0, socket.AI_PASSIVE):
        af, socktype, proto, canonname, sa = res
        try:
            sock = socket.socket(af, socktype, proto)
        except OSError:
            sock = None
            continue
        break

    if sock is None:
        logger.critical('could not open socket')
        sys.exit(1)

    return sock


def client(host: str, port: int, id: int, secret: str, server_secret: str, retry: int, timeout: int, ip: str):
    """
    当ip为""空字符串时。请求包里就不带上ip。让系统自动选择。
    """
    req = Request()
    buf = req.make(id, secret, ip)

    sock = makesock(host, port)
    sock.settimeout(timeout)

    for i in range(1, retry+1):
        logger.info(f"retry {i}/{retry}")
        sock.sendto(buf, (host, port))
        try:
            data_ack, c_addr = sock.recvfrom(4096)
        except TimeoutError:
            continue
        
        if req.verifyAck(data_ack, server_secret):
            logger.info(f"Server: {host} Addr: {c_addr[0]}  verify ACK ok")
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

    cmd = c.get("Cmd")

    while True:

        if cmd is None:
            # 使用默认方法获取ipv6地址
            try:
                with DefaultRouteIP() as default_route:
                    ip = default_route.get_iface_ipv6()
            except ValueError as e:
                logger.error(f"获取默认 IPv6 地址失败: {e}")
                time.sleep(interval)
                continue


        else:
            try:
                ip = getipcmd.run(cmd)
            except Exception as e:
                logger.error(f"执行命令 {cmd} 获取 IP 失败: {e}")
                logger.error(traceback.format_exc())
                time.sleep(interval)
                continue

        if ip == "":
            logger.error("获取IP为空，请检查命令行脚本是否正确。")
            # 当ip为""空字符串时。请求包里就不带上ip。让系统自动选择。
            logger.info("使用最后的 UDP connect 方法, 需要当前机器上有可使用的ipv6地址。")

        try:
            client(addr, port, clientid, secret, server_secret, retry, timeout, ip)
        except Exception:
            logger.warning("有异常：")
            traceback.print_exc()

        logger.debug(f"sleep({interval}) ...")
        time.sleep(interval)


if __name__ == "__main__":
    main()

