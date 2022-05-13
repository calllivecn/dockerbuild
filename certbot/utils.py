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
import configparser


def getlogger(level=logging.INFO):
    logger = logging.getLogger("ddns")
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(filename)s:%(funcName)s:%(lineno)d %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")
    consoleHandler = logging.StreamHandler(stream=sys.stdout)
    #logger.setLevel(logging.DEBUG)

    consoleHandler.setFormatter(formatter)

    # consoleHandler.setLevel(logging.DEBUG)
    logger.addHandler(consoleHandler)
    logger.setLevel(level)
    return logger

logger = getlogger()


def get_self_ip():
    """
    这样可以拿到， 默认出口ip。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.connect(("223.5.5.5", 2022))
    addr = sock.getsockname()[0]
    sock.close()
    logger.debug(addr)
    return addr


def get_self_ipv6():
    """
    这样可以拿到， 默认出口ip。
    不过ipv6拿到的是临时动态地址。 直接做ddns, ~~会频繁更新。~~ 不会频繁更新。
    """
    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.connect(("2400:3200:baba::1", 2022))
    addr = sock.getsockname()[0]
    sock.close()
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
        cur = int(time.time())

        id_byte = struct.pack("!I", id_)

        sha = hashlib.sha256(
            id_byte + secret.encode("ascii") + struct.pack("!Q", cur)
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


