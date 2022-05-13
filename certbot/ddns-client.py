#!/usr/bin/env python3
# coding=utf-8
# date 2022-05-13 08:32:37
# author calllivecn <c-all@qq.com>


import sys
import time
import socket
import struct
import hashlib
import logging
import argparse

from utils import (
    logger,
    Request,
    DDNSPacketError,
)


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


def client(id, secret, args):
    req = Request()
    buf = req.make(id, secret)

    sock = makesock(addr, port)
    sock.settimeout(timeout)

    for i in range(retry):
        sock.sendto(buf, (addr, port))
        try:
            data_ack, addr = sock.recvfrom(512)
        except TimeoutError:
            logger.warning(f"retry {i}/{retry}")


def main():
    pass