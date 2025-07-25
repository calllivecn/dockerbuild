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
from pathlib import Path
from dataclasses import dataclass

try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib

import logs


logger = logging.getLogger(logs.LOGNAME)


PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

NAME, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (NAME + ".toml")


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


def readcfg2(f: Path, cfg: str) -> dict:
    if f.exists() and f.is_file():
        with open(f, "rb") as fp:
            conf = tomllib.load(fp)
    else:
        with open(f, "w") as fp:
            fp.write(cfg)
        
        logger.warning(f"需要配置 {f} 文件")
        sys.exit(1)
    
    return conf


class DDNSPacketError(Exception):
    pass

@dataclass
class DDNSPacket:

    id: int
    secret: str
    timestamp: int
    ip: str|None

    def __post_init__(self):
        if not isinstance(self.id, int) or not (0 <= self.id < 2**32):
            raise ValueError("id must be an unsigned 32-bit integer.")

        if not isinstance(self.secret, str) or len(self.secret) == 0:
            raise ValueError("secret must be a non-empty string.")

        if self.ip is not None:
            if not isinstance(self.ip, str) or not self.is_valid_ip(self.ip):
                raise ValueError("ip must be a valid IPv4 or IPv6 address.")

    @staticmethod
    def is_valid_ip(ip: str) -> bool:
        try:
            ipaddress.ip_address(ip)
            return True
        except ValueError:
            return False
    
    def to_bytes(self) -> bytes:
        """
        将数据包转换为字节格式。
        """
        self.id_bytes = struct.pack("!I", self.id)
        secret_bytes = self.secret.encode("ascii")
        timestamp_bytes = struct.pack("!Q", self.timestamp)

        if self.ip is None:
            self.ip_bytes = b""
        else:
            self.ip_bytes = ipaddress.ip_address(self.ip).packed

        return self.id_bytes + secret_bytes + timestamp_bytes + self.ip_bytes


class Request:
    """
    需要打包发送的字段：
    - 4字节：客户端ID
    - 32字节：SHA256哈希值
    - ipaddress: IPv4或IPv6地址, 4byte or 16byte

    client:
    make(id_, secret_client) --> sendto() --> recv() -- verifyack(buf, secret_secret)
    server:
    recv() --> get_id_conf --> frombuf(buf) --> verify(secret_client) --> ack(secret_server) --> sendto()
    """

    def __init__(self, timestamp_range=60):
        self.timestamp_range = timestamp_range
        self.__buf: bytes
        self.id_client: int
        self.id_byte: bytes
        self.sha_client: bytes
        self.ip: str|None
        self.ip_bytes: bytes

    def make(self, id_: int, secret: str, ip: str|None) -> bytes:
        """
        secret: client secret
        """
        dp = DDNSPacket(id_, secret, int(time.time()), ip)

        sha = hashlib.sha256(dp.to_bytes())

        self.__buf = dp.id_bytes + sha.digest() + dp.ip_bytes
        return self.__buf

    def frombuf(self, buf: bytes):
        data_len = len(buf)
        if data_len not in (4+32, 4+32+4, 4+32+16):
            raise DDNSPacketError("packet invalid")
        
        self.__buf = buf
        
        self.id_byte = buf[:4]
        self.id_client = struct.unpack("!I", buf[:4])[0]
        self.sha_client = buf[4:36]
        self.ip_bytes = buf[36:]

        if self.ip_bytes:
            try:
                self.ip = ipaddress.ip_address(self.ip_bytes).exploded
            except ValueError:
                raise DDNSPacketError("DDNSPacket IP address length invalid")
        else:
            self.ip = ""

    def verify(self, secret: str) -> bool:
    
        cur = int(time.time())
        for t in range(cur - self.timestamp_range, cur + self.timestamp_range):
            sha256 = hashlib.sha256(self.id_byte + secret.encode("ascii") + struct.pack("!Q", t) + self.ip_bytes)

            if self.sha_client == sha256.digest():
                return True
        
        return False

    def ack(self, secret: str) -> bytes:
        """
        secret: server secret
        """
        sha256 = hashlib.sha256(self.__buf + secret.encode("ascii"))
        return sha256.digest()

    def verifyAck(self, buf: bytes, secret: str) -> bool:
        """
        secret: server secret
        """
        sha256 = hashlib.sha256(self.__buf + secret.encode("ascii"))

        if buf == sha256.digest():
            return True
        else:
            return False


