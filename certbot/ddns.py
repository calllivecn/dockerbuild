#!/usr/bin/env python3
# coding=utf-8
# date 2022-03-15 12:40:54
# author calllivecn <c-all@qq.com>


import sys
import socket
import argparse
import configparser


# pip install alibabacloud_alidns20150109==2.0.2
from alibabacloud_alidns20150109.client import Client as Alidns20150109Client
from alibabacloud_tea_openapi import models as open_api_models
from alibabacloud_alidns20150109 import models as alidns_20150109_models

"""
文档地址：https://next.api.aliyun.com/api/Alidns/2015-01-09/AddDomainRecord
"""


def get_self_ip():
    """
    这样可以拿到， 默认出口ip。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.connect(("223.5.5.5", 2022))
    addr = sock.getsockname()
    print(addr)
    return addr


def get_self_ipv6():
    """
    这样可以拿到， 默认出口ip。
    不过ipv6拿到的是临时动态地址。 不好直接做ddns。
    """
    sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
    sock.connect(("2400:3200:baba::1", 2022))
    addr = sock.getsockname()
    print(addr)
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
        jsondata = self.client.add_domain_record(add_domain_record_request)

        return jsondata

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
        jsondata = self.client.update_domain_record(
            update_domain_record_request)

        return jsondata

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
        jsondata = self.client.describe_domain_records(
            describe_domain_records_request)

        return jsondata


def main():
    pass


if __name__ == '__main__':
    main()
