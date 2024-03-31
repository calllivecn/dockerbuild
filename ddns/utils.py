#!/usr/bin/env python3
# coding=utf-8
# date 2022-05-13 08:32:37
# author calllivecn <calllivecn@outlook.com>


import os
import sys
import time
import socket
import struct
import hashlib
import logging
import ipaddress
import configparser
from pathlib import Path
from configparser import NoOptionError, NoSectionError


import logs


logger = logging.getLogger(logs.LOGNAME)


PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

NAME, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (NAME + ".conf")


# 回环地址：127.0.0.0/8
# 私有地址：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
# 广播地址：224.0.0.0/4
# 测试地址：192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24

IPV4_NETWORK_reserved = (
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    # ipaddress.ip_address("224.0.0.0/4"),
)

def check_ipv4_network_reserved(ipv4: str):
    """
    检测ip是不是，保留地址
    """
    addr = ipaddress.ip_address(ipv4)
    for network in IPV4_NETWORK_reserved:
        if addr in network:
            return True
    
    return False

def get_self_ip():
    """
    这样可以拿到， 默认出口ip。
    """
    while True:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("223.5.5.5", 2022))
        addr = sock.getsockname()[0]
        sock.close()

        if check_ipv4_network_reserved(addr):
            time.sleep(1)
        else:
            break

    logger.debug(addr)
    return addr


# 唯一本地地址：
FC00 = ipaddress.ip_network("fc00::/7")
# 链路本地地址：
FE80 = ipaddress.ip_network("fe80::/10")
# 多播地址：
FF00 = ipaddress.ip_network("ff00::/8")

IPV6_NETWORK_reserved = (FC00, FE80, FF00)

def check_ipv6_network_reserved(ipv6: str):
    """
    检测ip是不是，保留地址
    """
    addr = ipaddress.ip_address(ipv6)
    for network in IPV6_NETWORK_reserved:
        if addr in network:
            return True
    
    return False

def get_self_ipv6():
    """
    这样可以拿到， 默认出口ip。
    不过ipv6拿到的是临时动态地址。 直接做ddns, ~~会频繁更新。~~ 不会频繁更新。
    """
    while True:
        sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
        sock.connect(("2400:3200:baba::1", 2022))
        addr = sock.getsockname()[0]
        sock.close()

        if check_ipv6_network_reserved(addr):
            time.sleep(1)
        else:
            break

    logger.debug(addr)
    return addr

# print(get_self_ip())

def readcfg(f, cfg):
    if f.exists() and f.is_file():
        conf = configparser.ConfigParser()
        conf.read(str(f))
    else:
        with open(f, "w") as fp:
            fp.write(cfg)
        
        logger.warning(f"需要配置 {f} 文件")
        sys.exit(1)
    
    return conf


class DDNSPacketError(Exception):
    pass


class Request:
    """
    client:
    make(id_, secret_client) --> sendto() --> recv() -- verifyack(buf, secret_secret)
    server:
    recv() --> get_id_conf --> frombuf(buf) --> verify(secret_client) --> ack(secret_server) --> sendto()
    """

    def make(self, id_, secret):
        """
        secret: client secret
        """
        t = int(time.time())

        id_byte = struct.pack("!I", id_)

        sha = hashlib.sha256(
            id_byte + secret.encode("ascii") + struct.pack("!Q", t)
        )

        self.__buf = id_byte + sha.digest()
        return self.__buf

    def frombuf(self, buf):

        if len(buf) != (4+32):
            raise DDNSPacketError("packet invalid")
        
        self.__buf = buf

        self.id_byte = buf[:4]
        self.id_client = struct.unpack("!I", self.id_byte)[0]
        self.sha_client = buf[4:]

    def verify(self, secret):
    
        cur = int(time.time())
        shas = []
        for t in range(cur - 10, cur + 10):
            sha256 = hashlib.sha256(
                self.id_byte + secret.encode("ascii") + struct.pack("!Q", t)
            )
            shas.append(sha256.digest())
        
        if self.sha_client in shas:
            return True
        else:
            return False

    def ack(self, secret):
        """
        secret: server secret
        """
        sha256 = hashlib.sha256(
            self.__buf + secret.encode("ascii")
        )
        return sha256.digest()

    def verifyAck(self, buf, secret):
        """
        secret: server secret
        """
        sha256 = hashlib.sha256(
            self.__buf + secret.encode("ascii")
        )

        if buf == sha256.digest():
            return True
        else:
            return False


