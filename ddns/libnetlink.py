#!/usr/bin/env python3
# coding=utf-8
# date 2023-12-29 15:04:24
# author calllivecn <calllivecn@outlook.com>

"""

"""

import time
import socket
import struct
import threading

__all__  = (
    "NetLink",
)


# 定义一些常量 
# 参考文件：include/linux/netlink.h
# 参考文件：include/linux/rtnetlink.h
# man 7 rtnetlink

RTMGRP_LINK = 0x1 # 监听网络接口变化的组
RTMGRP_IPV4_IFADDR = 0x10
RTMGRP_IPV6_IFADDR = 0x100

IF_TYPE_ADDR_IPV4 = 128
IF_TYPE_ADDR_IPV6 = 192

# 定义一些常量
NLMSG_NOOP = 1 # netlink消息类型：空操作
NLMSG_ERROR = 2 # netlink消息类型：错误
RTM_NEWLINK = 16 # netlink消息类型：新建网络接口
RTM_DELLINK = 17 # netlink消息类型：删除网络接口
RTM_NEWADDR = 20 # netlink消息类型：接口添加ip地址
RTM_DELADDR = 21 # netlink消息类型：接口删除ip地址

IFLA_IFNAME = 3 # 接口属性类型：名称

IFA_F_TENTATIVE = 0x40 # 接口标志：tentative; tentative表示地址还在进行重复地址检测（DAD）



class NetLink:

    # # 解析netlink消息头部（长度为16字节），得到长度、类型、标志、序列号、进程ID等信息
    msg_header = struct.Struct("=LHHLL")
    fields = ("msg_len", "msg_type", "flags", "seq", "pid")
    
    # 解析剩余数据中的前16个字节，得到协议族、设备类型、索引、标志、改变等信息
    msg_header2 = struct.Struct("=BBHiII")
    fields2 = ("family", "unknown", "if_type", "index", "flags", "change") 


    def __init__(self):
        # 创建netlink套接字并绑定本地地址
        self.sockfd = socket.socket(socket.AF_NETLINK, socket.SOCK_RAW, socket.NETLINK_ROUTE)
        # sockfd.bind((os.getpid(), RTMGRP_LINK | RTMGRP_IPV4_IFADDR | RTMGRP_IPV6_IFADDR))
        self.sockfd.bind((0, RTMGRP_LINK | RTMGRP_IPV4_IFADDR | RTMGRP_IPV6_IFADDR))


        # 更新标志
        self._updated = threading.Lock()
        self._updated.acquire()

        self.result = None
        self.timestamp = time.monotonic()
        self.time_interval = 3

        # 启动monitor线程
        self.th = threading.Thread(target=self.__start, name="NetLink monitor", daemon=True)
        self.th.start()


    def __start(self):
        """
        只要有新增ip地址(也是修改ip的意思，修改会删除旧地址，添加新地址)就更新标志。?
        """

        while True:
            # 接收消息
            data = self.sockfd.recv(8192)
            content = dict(zip(self.fields, self.msg_header.unpack(data[:16])))

            # 去掉netlink消息头部，得到剩余数据（长度为msg_len-16字节）
            content2 = dict(zip(self.fields2, self.msg_header2.unpack(data[16:32])))

            """
            # 检查消息类型并处理相应事件
            # if msg_type == 16: # RTM_NEWADDR

            ~~RTM_ADDADDR 目前 还有点问题，直接这样会 add 两次，头次会 tentative 地址冲突检测（DAD）~~

            这很可能是因为您添加的 IPv6 地址是双栈地址。双栈地址是同时支持 IPv4 和 IPv6 的地址。当您添加一个双栈地址时，会产生两个 msg_type == 20 and (if_type == 192, 128)的数据包。
            第一个数据包是添加 IPv4 地址的通知，第二个数据包是添加 IPv6 地址的通知。

            如果您想了解更多关于双栈地址的信息，您可以参考RFC 4213。
            """
            timestamp = time.monotonic()

            if content["msg_type"] == RTM_NEWADDR and content2["if_type"] == IF_TYPE_ADDR_IPV6:
                # print("IPv6 address added")
                if (timestamp - self.timestamp) > self.time_interval:
                    self.result = "ipv6"
                    self.timestamp = timestamp
                    if self._updated.locked():
                        self._updated.release()

            elif content["msg_type"] == RTM_NEWADDR and content2["if_type"] != IF_TYPE_ADDR_IPV6:
                # print("IPv4 address added")
                if (timestamp - self.timestamp) > self.time_interval:
                    self.result = "ipv4"
                    self.timestamp = timestamp
                    if self._updated.locked():
                        self._updated.release()

            elif content["msg_type"] == RTM_DELADDR:
                # print("IP address removed")
                pass

            else:
                # print(f"""{content["msg_type"]=}""")
                pass


    def monitor(self):
        """
        只要有网络接口的ip地址发生更新，并且间隔上次更新大于3秒就返回。
        """
        with self._updated:
            self._updated.acquire()
        
        return 
    

    def close(self):
        self.sockfd.close()
        

def get_self_ipv6():
    """
    这样可以拿到， 默认出口ip。
    不过ipv6拿到的是临时动态地址。 直接做ddns, ~~会频繁更新。~~ 不会频繁更新。
    """
    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.connect(("2400:3200:baba::1", 2022))
    addr = sock.getsockname()[0]
    sock.close()
    return addr


def test():
    netlink = NetLink()

    while True:
        netlink.monitor()
        while True:
            try:
                ipv6 = get_self_ipv6()
            except OSError:
                time.sleep(1)
                continue
            break

        print(time.localtime(), "有新地址！这是访问外网的默认地址:", ipv6)
    

if __name__ == "__main__":
    test()

