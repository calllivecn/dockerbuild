#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <c-all@qq.com>


import os
import sys
import time
import socket
import logging
import argparse
import traceback
import configparser
from pathlib import Path


# pip install alibabacloud_alidns20150109==2.0.2
from alibabacloud_alidns20150109.client import Client as Alidns20150109Client
from alibabacloud_tea_openapi import models as open_api_models
from alibabacloud_alidns20150109 import models as alidns_20150109_models


"""
文档地址：https://next.api.aliyun.com/api/Alidns/2015-01-09/AddDomainRecord
"""

def getlogger(level=logging.INFO):
    logger = logging.getLogger("logger")
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
    不过ipv6拿到的是临时动态地址。 直接做ddns, 会频繁更新。
    """
    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.connect(("2400:3200:baba::1", 2022))
    addr = sock.getsockname()[0]
    sock.close()
    logger.debug(addr)
    return addr

# print(get_self_ip())


ALI_DDNS_URL = 'alidns.cn-zhangjiakou.aliyuncs.com'


class AliDDNS:
    def __init__(self, access_key_id, access_key_secret):
        self.access_key_id = access_key_id
        self.access_key_secret = access_key_secret

        """
        使用AK&SK初始化账号Client
        @param access_key_id:
        @param access_key_secret:
        @return: Client
        @throws Exception
        """
        config = open_api_models.Config(
            # 您的AccessKey ID,
            access_key_id=self.access_key_id,
            # 您的AccessKey Secret,
            access_key_secret=self.access_key_secret
        )

        # 访问的域名
        config.endpoint = ALI_DDNS_URL
        self.client = Alidns20150109Client(config)

    def addDomainRecord(self, domain_name, rr, typ, value):
        """
        参数：
        domain_name='calllive.cc',
        type='AAAA',
        rr='route'
        value='240e:3b5:3013:f760:6edd:c591:41db:7a5d',

        return:
        {
            "RequestId": "69698E87-A897-5FFA-B578-1001D5052D75",
            "RecordId": "751818936343988224"
        }
        """
        add_domain_record_request = alidns_20150109_models.AddDomainRecordRequest(
            domain_name=domain_name,
            type=typ,
            value=value,
            rr=rr
        )

        # 复制代码运行请自行打印 API 的返回值
        response = self.client.add_domain_record(add_domain_record_request)

        return response.body.to_map()

    def updateDonameRecord(self, record_id, rr, typ, value):
        """
        参数：
        record_id='751812982741233664',
        rr='route',
        type='AAAA',
        value='240e:3b5:3013:f760:2292:83ab:872:2'

        return:
        {
            "RequestId": "A997E4E6-C6BF-5A2B-85AE-01BE6E3AC1BE",
            "RecordId": "751812982741233664"
        }
        """

        update_domain_record_request = alidns_20150109_models.UpdateDomainRecordRequest(
            record_id=record_id,
            rr=rr,
            type=typ,
            value=value
        )
        # 复制代码运行请自行打印 API 的返回值
        response = self.client.update_domain_record(update_domain_record_request)

        return response.body.to_map()
    
    def describe_sub_domain(self, sub_domain, typ):
        """
        return:
        {
            "TotalCount": 1,
            "RequestId": "5AA5CC8A-4675-5B92-898A-5FBCC742E975",
            "PageSize": 20,
            "DomainRecords": {
                "Record": [
                    {
                        "RR": "route",
                        "Line": "default",
                        "Status": "ENABLE",
                        "Locked": false,
                        "Type": "AAAA",
                        "DomainName": "calllive.cc",
                        "Value": "240e:3b5:3013:f760:7942:d2cd:5cc4:2aa1",
                        "RecordId": "751945591127363584",
                        "TTL": 600,
                        "Weight": 1
                    }
                ]
            },
            "PageNumber": 1
        }
        """
        describe_sub_domain_records_request = alidns_20150109_models.DescribeSubDomainRecordsRequest(
            sub_domain=sub_domain,
            type=typ
        )
        # 复制代码运行请自行打印 API 的返回值
        response = self.client.describe_sub_domain_records(describe_sub_domain_records_request)
        # logger.debug(f"response type: {type(response)}")
        # logger.debug(f"response dir(): {dir(response)}")
        # logger.debug(f"response to_map(): {response.to_map()}")
        # logger.debug(f"response body: {response.body.to_map()}")
        # logger.debug(f"response.body type: {type(response.body)}")
        # jsondata = UtilClient.to_jsonstring(TeaCore.to_map(response))
        return response.body.to_map()

    def descrbieDomainRecord(self, domain_name, rrkey_word, typ):
        """
        domain_name='baidu.com',
        rrkey_word='ditu',
        typ='AAAA'

        return:
        {
            "TotalCount": 1,
            "RequestId": "06A55865-42D5-5453-B7D3-ECA434200584",
            "PageSize": 20,
            "DomainRecords": {
                "Record": [
                    {
                        "RR": "route",
                        "Line": "default",
                        "Status": "ENABLE",
                        "Locked": false,
                        "Type": "AAAA",
                        "DomainName": "calllive.cc",
                        "Value": "240e:3b5:3013:f760:6edd:c591:41db:7a5d",
                        "RecordId": "751812982741233664",
                        "TTL": 600,
                        "Weight": 1
                    }
                ]
            },
            "PageNumber": 1
        }

        """
        describe_domain_records_request = alidns_20150109_models.DescribeDomainRecordsRequest(
            domain_name=domain_name,
            rrkey_word=rrkey_word,
            type=typ
        )
        # 复制代码运行请自行打印 API 的返回值
        response = self.client.describe_domain_records(describe_domain_records_request)

        return response.body.to_map()


CONF="""\
[DDNS]
# 检查间隔时间单位秒
Interval=180

[Ali]
# 阿里云
AccessKeyId=
AccessKeySecret=

[DomainName]
# 例如域名是：dns.example.com
# RR: dns
RR=
# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
Type=
# Domain: example.com
Domain=

[Server]
# 其他轻客户端的key (预计使用很少的 bash 就可以实现; 目前还没实现。)
clientkey1=
clientkey2=
"""

PYZ_PATH = Path(sys.argv[0])
PWD = PYZ_PATH.parent

name, ext = os.path.splitext(PYZ_PATH.name)

CFG = PWD / (name + ".conf")
CACHE = PWD / (name + ".cache")

def readcfg():
    if CFG.exists() and CFG.is_file():
        conf = configparser.ConfigParser()
        conf.read(str(CFG))
    else:
        with open(CFG, "w") as f:
            f.write(CONF)
        
        logger.warning(f"需要配置{CFG}文件")
        sys.exit(1)
    
    return conf


def callddns(conf):

    ali = conf["Ali"]

    domain = conf["DomainName"]
    dns = ".".join([domain["RR"], domain["Domain"]])
    
    ipv6 = get_self_ipv6()
    logger.info(f"获取本机ipv6地址：{ipv6}")

    if not CACHE.is_file():
        logger.warning(f"{CACHE} 不存在, 需要第一次更新。")

        alidns = AliDDNS(ali["AccessKeyId"], ali["AccessKeySecret"])

        result = alidns.describe_sub_domain(".".join([domain["rr"], domain["Domain"]]), domain["Type"])

        logger.debug(f"sub_domain_record: {result}, type: {type(result)}")

        dns_record_id = result["DomainRecords"]["Record"][0]["RecordId"]

        logger.debug(f"dns_record_id: {dns_record_id}")
        
        logger.info(f"更新ipv6: {dns} --> {ipv6}")
        alidns.updateDonameRecord(dns_record_id, domain["RR"], domain["Type"], ipv6)

        logger.debug(f"写入缓存: {dns_record_id} ")
        # write cache
        with open(CACHE, "w") as f:
            f.write(" ".join([ipv6, dns_record_id]))
        
        return None


    if CACHE.lstat().st_size <= 4096:
        with open(CACHE, "r") as f:
            cache = f.read()

        logger.debug(f"cache值：{cache}")
        ip_cache , dns_record_id = cache.split(" ")

    else:
        logger.warning(f"{CACHE} 不存在，或者文件大小不正常。")

        raise ValueError(f"{CACHE} 不存在，或者文件大小不正常。")
    
    
    if ipv6 == ip_cache:
        logger.info("与缓存相同，不用更新.")
    else:
        alidns = AliDDNS(ali["AccessKeyId"], ali["AccessKeySecret"])

        logger.info(f"更新ipv6: {dns} --> {ipv6}")
        alidns.updateDonameRecord(dns_record_id, domain["RR"], domain["Type"], ipv6)

        logger.debug(f"更新cache: {ipv6} {dns_record_id}")
        with open(CACHE, "w") as f:
            f.write(" ".join([ipv6, dns_record_id]))


def main():
    parse = argparse.ArgumentParser(
        usage="%(prog)s",
        description="使用阿里 DNS 做 DDNS.",
    )

    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.debug:
        logger.setLevel(logging.DEBUG)

    conf = readcfg()

    INTERVAL = conf.getint("DDNS", "Interval")

    while True:
        logger.info(f"检查和更新...")

        try:
            callddns(conf)
        except Exception:
            logger.warning(f"异常:")
            traceback.print_exc()

        logger.info(f"sleep({INTERVAL}) ...")
        time.sleep(90)


if __name__ == '__main__':
    main()
