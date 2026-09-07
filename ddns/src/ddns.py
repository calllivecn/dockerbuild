#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <calllivecn@outlook.com>

import sys
import time
import json
import socket
import pprint
import logging
import argparse
import ipaddress
import asyncio
from threading import Thread

from typing import Any


from aliyunlib import AliDDNS
from utils import (
    PWD,
    CFG,
    NAME,
    Request,
    readcfg2,
    DDNSPacketError,
    verify_https_signature,
    parse_address,
)
import logs
from logs import logger
from quart import Quart, jsonify, request
from hypercorn.asyncio import serve
from hypercorn.config import Config as HypercornConfig

CONF="""\
[Ali]
# 阿里云
AccessKeyId="xxxxxxxxxxxxxxxxx"
AccessKeySecret="xxxxxxxxxxxxxxxxx"

[Server]
# UDP 和 HTTP/TCP 分别配置；未写端口时，UDP 默认 2022、HTTP 默认 80、HTTPS 默认 443
UDPAddress="udp://[::]:2022"
Address="http://[::]"
# server 的 secret
Secret="xxxxxxxxxxxxxxxxxxxxxxxxx"
CertFile="/path/to/server.crt"
KeyFile="/path/to/server.key"

[[Clients]]
# 其他轻客户端的UUID (预计使用很少的 bash 就可以实现; bash 不行，不能接收UDP数据包。。。还是需要用golang和py写)
# 范围：1 ~ 4字节 无符号
# 更多client一直添加...
ClientID=1234

# client 的 secret
Secret="xxxxxxxxxxxxxxxxxxxxxxxxx"

# 例如域名是：dns.example.com
# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
# RR: dns; Domain: example.com;
multidns = [
    {Type="AAAA", RR="client", Domain="example.com"},
]

"""



class SelfIPCache:
    """
    cache: 目前不需要 一个域名对应多个ip
    {
        "domain1": {
            "record_id1": "ip1",
            "record_id2": "ip2",
            ...
        },
        ...

        "domain2": {
            "record_id1": "ip1",
            "record_id2": "ip2",
            ...
        }
    }

    cache:
    {
        "domain1": "ip1",
        "domain2": "ip2",
        "domain3": "ip3",
    }
    """

    def __init__(self):

        self.filepath = PWD / (NAME + ".cache")

        self.cache = {}

        if self.is_file():
            if self.filepath.lstat().st_size <= (1<<20):
                with open(self.filepath, "r") as f:
                    self.cache = json.load(f)
            else:
                logger.warning(f"{self.filepath} 文件大小不正常。")
                raise ValueError(f"{self.filepath} 件大小不正常。")
        
        else:
            with open(self.filepath, "w") as f:
                json.dump(self.cache, f, ensure_ascii=False, indent=4)
            


    def get(self, domain) -> str:
        """
        return: ip_cache or ""
        """

        logger.debug(f"{self.cache=}")

        ip_cache = self.cache.get(domain)
        if ip_cache is None:
            return ""
        else:
            return ip_cache
    

    def set(self, domain, ip):
        self.cache[domain] = ip
        with open(self.filepath, "w") as f:
            json.dump(self.cache, f)


    def is_file(self) -> bool:
        return self.filepath.is_file()


ip_dnsid_cache = SelfIPCache()


class Conf:

    def __init__(self):
        self.conf = readcfg2(CFG, CONF)

        self.clientids: list[dict[str, Any]] = self.conf["Clients"]

        self.multidns: dict[int, dict[str, Any]] = {}
        """
        self.multidns = {
        ClientDI:
        Secret:
        multidns = [
            {Type="AAAA", RR="dns-01", Domain="example.com"},
            {Type="AAAA", RR="dns-02", Domain="example.com"},
            ]
        }
        """

        self.__server_cfg()

        self.__clientids_cfg()
    
        self.client_cache = {}

        for c in self.clientids:
            id_ = c["ClientID"]
            # timestamp, ip
            self.client_cache[id_] = [0, None]


    def __server_cfg(self):
        Ali = self.conf["Ali"]
        self.ali_keyid = Ali["AccessKeyId"]
        self.ali_keysecret = Ali["AccessKeySecret"]

        Server = self.conf["Server"]
        raw_address = Server.get("Address", "")
        raw_udp_address = Server.get("UDPAddress")
        legacy_address = "://" not in raw_address
        if raw_udp_address:
            udp_scheme, self.server_addr, self.server_port, _ = parse_address(raw_udp_address)
            if udp_scheme != "udp":
                raise ValueError("Server.UDPAddress 必须使用 udp:// scheme")
        else:
            udp_scheme, self.server_addr, self.server_port, _ = parse_address(
                raw_address, default_scheme="udp", default_port=Server.get("Port", 2022)
            )
        self.server_secret = Server["Secret"]

        # 新配置用 Server.Address 选择 API 协议；旧配置继续读取 [Http]/[Https]。
        http = self.conf.get("Http", {})
        https = self.conf.get("Https", {})
        def legacy_api_address(section, scheme, default_port):
            host = section.get("Address", "::")
            host = f"[{host}]" if ":" in host and not host.startswith("[") else host
            return parse_address(
                f"{scheme}://{host}:{section.get('Port', default_port)}",
                default_scheme=scheme,
                default_port=default_port,
            )

        if raw_address and "://" in raw_address:
            self.api_scheme, self.api_addr, self.api_port, _ = parse_address(raw_address)
            if self.api_scheme not in ("http", "https"):
                self.api_scheme = ""
                self.api_addr = ""
                self.api_port = 0
        elif not raw_udp_address and legacy_address and https.get("Enabled", False):
            self.api_scheme, self.api_addr, self.api_port, _ = legacy_api_address(https, "https", 8443)
        elif not raw_udp_address and legacy_address and http.get("Enabled", False):
            self.api_scheme, self.api_addr, self.api_port, _ = legacy_api_address(http, "http", 8080)
        else:
            self.api_scheme = ""
            self.api_addr = ""
            self.api_port = 0

        self.http_enabled = self.api_scheme == "http"
        self.https_enabled = self.api_scheme == "https"
        self.https_addr = self.api_addr
        self.https_port = self.api_port
        self.https_cert = Server.get("CertFile", https.get("CertFile", ""))
        self.https_key = Server.get("KeyFile", https.get("KeyFile", ""))

        # self.self_domain_name = self.conf["SelfDomainName"]
        # self.server_interval = self.self_domain_name["Interval"]
        
    
    def __clientids_cfg(self):

        for cid in self.clientids:
            c_id = cid["ClientID"]
            self.multidns[c_id] = cid


    def get_multidns_info(self, id: int) -> dict[str, Any]:
        return self.multidns[id]


    def cache_check(self, id_client, cur_ip):
        """
        放内存吧。
        return: 0: 更新， 1: 没有对应client, 2: 更新太频繁, 3：ip 没变化
        """
        result = self.client_cache.get(id_client)

        if result is None:
            return 1

        t, cache_ip = result

        cur = time.time()
        if t != 0 and (cur - t) <= 30:
            return 2

        if cur_ip != cache_ip:
            self.client_cache[id_client] = [cur, cur_ip]
            return 0
        else:
            return 3


# 封装成一个类试试
class IPv6UDPServer:

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_RECVPKTINFO, 1)  # 开启 IPV6_PKTINFO 选项
        self.sock.bind((self.host, self.port))
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_value, traceback):
        self.sock.close()

    def recv(self, buffer_size=8192) -> tuple[bytes, tuple]:
        data, ancdata, msg_flags, addr = self.sock.recvmsg(buffer_size, 1024)
        # 解析控制消息以获取目标地址信息
        for cmsg_level, cmsg_type, cmsg_data in ancdata:
            if cmsg_level == socket.IPPROTO_IPV6 and cmsg_type == socket.IPV6_PKTINFO:
                self.cmsg_level = cmsg_level
                self.cmsg_type = cmsg_type
                self.pktinfo = cmsg_data
                # self.server_recv_addr = socket.inet_ntop(socket.AF_INET6, cmsg_data[:16])
                # self.if_index = struct.unpack("=I", cmsg_data[16:20])[0]  # 如果需要接口索引
                return data, addr
        
        # 如果没有控制消息，直接返回数据和地址
        return data, addr
    
    def send(self, data: bytes, addr: tuple) -> int:
        return self.sock.sendmsg([data], [(self.cmsg_level, self.cmsg_type, self.pktinfo)], 0, addr)


def update_dns(alidns: AliDDNS, rr, typ, domain, ip):
    """
    return: False or dns_record_id
    """

    dns = f"{rr}.{domain}"

    result = alidns.describe_sub_domain(dns, typ)

    logger.debug(f"\nsub_domain_record: {pprint.pformat(result)}")

    # 这里可能会查询到0条或多条记录
    if len(result["DomainRecords"]["Record"]) == 0:
        logger.info(f"添加记录: {dns=} --> {ip=}")
        result = alidns.addDomainRecord(domain, rr, typ, ip)
        dns_record_id = result["RecordId"]
        
        # 更新缓存
        # ip_dnsid_cache.set(dns, dns_record_id, ip)
        ip_dnsid_cache.set(dns, ip)
        return dns_record_id

    elif len(result["DomainRecords"]["Record"]) == 1:
        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

        ip_value = result["DomainRecords"]["Record"][0]["Value"]

        logger.debug(f"{dns_record_id=}")

        if ip == ip_value:
            logger.debug(f"域名和ip相同，不用更新: {dns=} {ip=}")
        else:

            logger.info(f"更新记录: {dns=} --> {ip=}")

            try:
                result = alidns.updateDonameRecord(dns_record_id, rr, typ, ip)
            except Exception as e:
                logger.warning(f"异信息: {e}")
                logger.warning(f"updateDonameRecord() --> {result}")
                return False

        ip_dnsid_cache.set(dns, ip)

        return dns_record_id

    elif len(result["DomainRecords"]["Record"]) > 1:
        logger.warning(f"{typ=} {dns=} 有多个ip地址, 暂时还不支持一个域名对应多个地址")
        return False

    else:
        logger.warning("当前能查询到多条记录，可能需要更准确的查询，才能正常工作。")
        return False


def multi_update_dns(alidns: AliDDNS, multidns: list, ip: str):
    for info in multidns:
        update_dns(alidns, info["RR"], info["Type"], info["Domain"], ip)


def process_update(conf: Conf, alidns: AliDDNS, client_id: int, ip: str) -> int:
    """统一处理 UDP 和 HTTPS 请求，返回 0/1/2/3 状态码。"""
    result = conf.client_cache.get(client_id)
    if result is None:
        return 1

    c_check = conf.cache_check(client_id, ip)
    if c_check == 0:
        domains = conf.get_multidns_info(client_id)["multidns"]
        th = Thread(target=multi_update_dns, args=(alidns, domains, ip), daemon=True)
        th.start()
    return c_check


def server(conf: Conf):

    logger.debug(f"server listen: [{conf.server_addr}]:{conf.server_port}")

    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)

    with IPv6UDPServer(conf.server_addr, conf.server_port) as sock:

        while True:
            data, addr = sock.recv(8192)
            addr_ip = addr[0]
            logger.debug(f"收到来自 {addr_ip} 的数据: {data}")
            req = Request()
            try:
                req.frombuf(data)
            except DDNSPacketError as e:
                logger.warning(f"Error: {e}\n请求验证失败，可能有人在探测: {addr_ip=}")
                continue
            
            try:
                client_secret = conf.get_multidns_info(req.id_client)["Secret"]
            except KeyError:
                logger.warning(f"没有对应的 ClientID: {req.id_client} {addr_ip=}")
                continue

            # 查看有没有携带ip
            if req.ip:
                dns_ip = req.ip
            else:
                dns_ip = addr_ip


            if client_secret is not None and req.verify(client_secret):

                logger.debug(f"Cache={conf.client_cache}")
                c_check = process_update(conf, alidns, req.id_client, dns_ip)

                if c_check == 0:

                    # 回复client ACK
                    logger.debug("回复ACK")
                    sock.send(req.ack(conf.server_secret), addr)

                elif c_check == 1:
                    logger.warning(f"没有对应的: ClientID={req.id_client} {addr_ip=}")

                elif c_check == 2:
                    sock.send(req.ack(conf.server_secret), addr)
                    logger.debug(f"请求太频繁(间隔小小于30秒): ClientID={req.id_client} {addr_ip=}")

                elif c_check == 3:
                    sock.send(req.ack(conf.server_secret), addr)
                    logger.debug(f"当前ip没有改变: ClientID={req.id_client} {addr_ip=}")

            else:
                logger.warning(f"请求验证失败，可能有人在探测: {addr_ip=}")


def server_worker(conf: Conf):
    while True:
        try:
            server(conf)
        except Exception as e:  # noqa: BLE001
            logger.error(f"服务端异常(5秒后重启)：{pprint.pformat(e)}")
            time.sleep(5)


def api_worker(conf: Conf, alidns: AliDDNS, debug: bool):
    async def shutdown_trigger():
        await asyncio.Future()

    config = HypercornConfig()
    host = f"[{conf.api_addr}]" if ":" in conf.api_addr else conf.api_addr
    config.bind = [f"{host}:{conf.api_port}"]
    config.accesslog = "-" if debug else None
    config.loglevel = "debug" if debug else "warning"
    if conf.https_enabled:
        config.certfile = conf.https_cert
        config.keyfile = conf.https_key

    asyncio.run(serve(create_api_app(conf, alidns), config, shutdown_trigger=shutdown_trigger))


def create_api_app(conf: Conf, alidns: AliDDNS) -> Quart:
    app = Quart(__name__)

    @app.post("/api/v1/update")
    async def https_update():
        data = await request.get_json(silent=True)
        if not isinstance(data, dict):
            return jsonify(ok=False, message="invalid JSON"), 400

        try:
            client_id = data["client_id"]
            timestamp = data["timestamp"]
            ip = data.get("ip", "")
            signature = data["signature"]
            if not isinstance(client_id, int) or not isinstance(timestamp, int):
                raise ValueError
            if not isinstance(ip, str) or not isinstance(signature, str):
                raise ValueError
        except (KeyError, ValueError, TypeError):
            return jsonify(ok=False, message="invalid request"), 400

        try:
            client_cfg = conf.get_multidns_info(client_id)
        except KeyError:
            return jsonify(ok=False, message="unknown client"), 404

        if abs(int(time.time()) - timestamp) > 60:
            return jsonify(ok=False, message="timestamp expired"), 401

        if ip:
            try:
                ipaddress.ip_address(ip)
            except ValueError:
                return jsonify(ok=False, message="invalid IP"), 400
        else:
            ip = request.remote_addr or ""

        if not verify_https_signature(client_id, timestamp, data.get("ip", ""), signature, client_cfg["Secret"]):
            return jsonify(ok=False, message="invalid signature"), 401

        if not ip:
            return jsonify(ok=False, message="IP is required"), 400

        status = process_update(conf, alidns, client_id, ip)
        messages = {0: "accepted", 2: "too frequent", 3: "IP unchanged"}
        if status == 1:
            return jsonify(ok=False, message="unknown client"), 404
        if status == 2:
            return jsonify(ok=False, message=messages[status]), 429
        return jsonify(ok=True, message=messages.get(status, "accepted"))

    return app


def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="使用阿里 DNS 做 DDNS.",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)
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

    conf = Conf()
    alidns = AliDDNS(conf.ali_keyid, conf.ali_keysecret)

    if conf.https_enabled:
        if not conf.https_cert or not conf.https_key:
            parse.error("Address 使用 https 时必须配置 Server.CertFile 和 Server.KeyFile")

    udp_thread = Thread(target=server_worker, args=(conf,), daemon=True, name="UDPServer")
    udp_thread.start()

    api_thread = None
    if conf.http_enabled or conf.https_enabled:
        api_thread = Thread(target=api_worker, args=(conf, alidns, args.debug), daemon=True, name="HTTPServer")
        api_thread.start()

    udp_thread.join()
    if api_thread:
        api_thread.join()


if __name__ == '__main__':
    main()
