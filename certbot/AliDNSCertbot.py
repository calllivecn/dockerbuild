#!/usr/bin/env python3
# coding=utf-8
# date 2019-11-20 16:34:16
# author calllivecn <calllivecn@outlook.com>


import os
import sys
import time
import base64
import hashlib
import hmac

import logging
#import configparser

import urllib
from urllib import request
from urllib import parse


# pip install alibabacloud_alidns20150109==2.0.2
from alibabacloud_alidns20150109.client import Client as Alidns20150109Client
from alibabacloud_tea_openapi import models as open_api_models
from alibabacloud_alidns20150109 import models as alidns_20150109_models



def getlogger(level=logging.INFO):
    logger = logging.getLogger("logger")
    formatter = logging.Formatter("%(asctime)s %(filename)s:%(funcName)s:%(lineno)d %(levelname)s: %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")
    consoleHandler = logging.StreamHandler(stream=sys.stdout)
    #logger.setLevel(logging.DEBUG)

    consoleHandler.setFormatter(formatter)

    # consoleHandler.setLevel(logging.DEBUG)
    logger.addHandler(consoleHandler)
    logger.setLevel(level)
    return logger

logger = getlogger()


ALI_DDNS_URL = 'alidns.cn-zhangjiakou.aliyuncs.com'
LetsEncryptSubDomain = '_acme-challenge'


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

class AliyunDns:
    __endpoint = 'https://alidns.aliyuncs.com'
    __appid = ''
    __appsecret = ''
    __logger = logging.getLogger("logger")

    def __init__(self, appid, appsecret):
        self.__appid = appid
        self.__appsecret = appsecret

    def __getSignatureNonce(self):
        return str(int(round(time.time() * 1000)))

    def __percentEncode(self, s):

        res = parse.quote_plus(s.encode('utf8'), '')

        res = res.replace('+', '%20')
        res = res.replace('*', '%2A')
        res = res.replace('%7E', '~')

        #res = res.replace('+', '%20')
        #res = res.replace('\'', '%27')
        #res = res.replace('\"', '%22')
        #res = res.replace('*', '%2A')
        #res = res.replace('%7E', '~')

        return res

    def __signature(self, params):
        sortedParams = sorted(params.items(), key=lambda params: params[0])

        query = ''
        for k, v in sortedParams:
            query += '&' + self.__percentEncode(k) + '=' + self.__percentEncode(v)

        self.__logger.debug("参数编码串：{}".format(query))

        stringToSign = 'GET&%2F&' + self.__percentEncode(query[1:])
        self.__logger.debug("签名串：{}".format(stringToSign))

        try:
            h = hmac.new((self.__appsecret + "&").encode("utf-8"), stringToSign.encode("utf-8"), hashlib.sha1)
        except Exception as e:
            self.__logger.error("签名出错...")
            self.__logger.error(e)

        signature = base64.encodebytes(h.digest()).strip()

        return signature

    def __request(self, params):
        commonParams = {
            'Format': 'JSON',
            'Version': '2015-01-09',
            'SignatureMethod': 'HMAC-SHA1',
            'SignatureNonce': self.__getSignatureNonce(),
            'SignatureVersion': '1.0',
            'AccessKeyId': self.__appid,
            'Timestamp':  time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        }

        # merge all params
        finalParams = commonParams.copy()
        finalParams.update(params)

        self.__logger.debug("finalParams: {}".format(finalParams))

        # signature
        finalParams['Signature'] = self.__signature(finalParams)
        self.__logger.info('Signature: {}'.format(finalParams['Signature']))

        # get final url
        url = "{}/?{}".format(self.__endpoint, parse.urlencode(finalParams))
        # print(url)

        req = request.Request(url)
        self.__logger.debug(req.full_url)
        self.__logger.debug(req.get_method())

        try:
            f = request.urlopen(req)
            response = f.read()
            self.__logger.info(response.decode('utf-8'))
        except request.HTTPError as e:
            self.__logger.info(e.read().strip().decode('utf-8'))
            raise SystemExit(e)

    def addDomainRecord(self, domain, rr, value):
        params = {
            'Action': 'AddDomainRecord',
            'DomainName': domain,
            'RR': rr,
            'Type': 'TXT',
            'Value': value
        }
        self.__request(params)

    def deleteSubDomainRecord(self, domain, rr):
        params = {
            'Action': 'DeleteSubDomainRecords',
            'DomainName': domain,
            'RR': rr,
            'Type': 'TXT'
        }
        self.__request(params)

    def addLetsencryptDomainRecord(self, domain, value):
        self.addDomainRecord(domain, self.__letsencryptSubDomain, value)

    def deleteLetsencryptDomainRecord(self, domain):
        self.deleteSubDomainRecord(domain, self.__letsencryptSubDomain)

    def toString(self):
        print('AliyunDns[appid=' + self.__appid + ', appsecret=' + self.__appsecret+']')



def auth(aliyunDns):

    domain = os.environ.get('CERTBOT_DOMAIN')
    value = os.environ.get('CERTBOT_VALIDATION')

    if domain is None:
        raise Exception('Environment variable CERTBOT_DOMAIN is empty.')

    if value is None:
        raise Exception('Environment variable CERTBOT_VALIDATION is empty.')

    try:
        logger.info('Start setting DNS')
        logger.info('Domain:' + domain)
        logger.info('Value:' + value)

        # aliyunDns.toString()

        # add letsencrypt domain record
        aliyunDns.addLetsencryptDomainRecord(domain, value)
        logger.debug("addDomainRecord()")

        # wait for completion
        logger.info('sleep 10 secs')
        time.sleep(10)

        logger.info('Success.')
        logger.info('DNS setting end!')

    except urllib.error.HTTPError as e:
        logger.error(e)
        sys.exit(1)
    except Exception as e:
        logger.error(e)
        sys.exit(1)


def cleanup(aliyunDns):
    
    domain = os.environ.get('CERTBOT_DOMAIN')
    if domain is None:
        raise Exception('Environment variable CERTBOT_DOMAIN is empty.')

    try:
        logger.info('Start to clean up')
        logger.info('Domain:' + domain)

        # aliyunDns.toString()

        # delete letsencrypt domain record
        aliyunDns.deleteLetsencryptDomainRecord(domain)

        logger.info('Success.')
        logger.info('Clean up end!')

    except Exception as e:
        logger.error(e)
        sys.exit(1)



Usage="""\
Usage: {} <auth|cleanup> <appid> <secretkey>
And: set environment CERTBOT_DOMAIN CERTBOT_VALIDATION
""".format(sys.argv[0])

def main():

    if len(sys.argv) == 1:
        print(Usage)
        sys.exit(1)

    if len(sys.argv) == 4:
        if "auth" == sys.argv[1] or "cleanup" == sys.argv[1]:
            appid = sys.argv[2]
            secretkey = sys.argv[3]
        else:
            logger.error(Usage)
            sys.exit(1)
    else:
        logger.error("Usage: {} <auth|cleanup> <appid> <secretkey>".format(sys.argv[0]))
        sys.exit(1)

    
    if sys.argv[1] == "auth":
        auth(AliyunDns(appid, secretkey))

    elif sys.argv[1] == "cleanup":
        cleanup(AliyunDns(appid, secretkey))

    else:
        logger.error(Usage)
        

if __name__ == '__main__':
    main()
