#!/usr/bin/env python3
# coding=utf-8
# date 2019-11-22 09:11:49
# author calllivecn <c-all@qq.com>


import os
import sys
import time
import base64
import hashlib
import json
import hmac

import logging
import pprint

import urllib
from urllib import request
from urllib import parse

#import configparser

# Set the global configuration
#CONFIG_FILENAME = 'config.ini'
#configFilepath = os.path.split(os.path.realpath(__file__))[0] + os.path.sep + CONFIG_FILENAME
#config = configparser.ConfigParser()
#config.read(configFilepath)

logger = logging.getLogger("logger")
formatter = logging.Formatter("%(asctime)s %(filename)s:%(lineno)d %(levelname)s: %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")
consoleHandler = logging.StreamHandler(stream=sys.stdout)
logger.setLevel(logging.DEBUG)
consoleHandler.setLevel(logging.DEBUG)
consoleHandler.setFormatter(formatter)
logger.addHandler(consoleHandler)


class TencentDns:
    __endpoint = 'https://cns.api.qcloud.com'
    __endpoint = 'https://cns.api.qcloud.com/v2/index.php'
    __letsencryptSubDomain = '_acme-challenge'
    __appid = ''
    __appsecret = ''
    __logger = logging.getLogger("logger")

    def __init__(self, appid, appsecret):
        self.__appid = appid
        self.__appsecret = appsecret

        self.__urlparse = parse.urlparse(self.__endpoint)

    def __signature(self, params):
        sortedParams = []
        for k, v in sorted(params.items(), key=lambda params: params[0]):
            sortedParams.append(k.replace('_', '.') + '=' + v)

        self.__query_str = '&'.join(sortedParams)

        self.__logger.debug("参数编码串：{}".format(self.__query_str))

        stringToSign = 'POST' + self.__urlparse.netloc + self.__urlparse.path + '?' + self.__query_str
        self.__logger.debug("签名串：{}".format(stringToSign))

        try:
            h = hmac.new(self.__appsecret.encode("utf-8"), stringToSign.encode("utf-8"), hashlib.sha256)
        except Exception as e:
            self.__logger.error("HMAC签名出错...")
            self.__logger.error(e)

        signature = base64.encodebytes(h.digest()).strip()

        return signature

    def __request(self, params):
        commonParams = {
            'SecretId': self.__appid,
            'Timestamp':  str(time.time()),
            'Nonce': str(int(time.time() * 1000)),
            'SignatureMethod': 'HmacSHA256',
            }

        # merge all params
        finalParams = commonParams.copy()
        finalParams.update(params)

        # signature
        finalParams['Signature'] = self.__signature(finalParams)
        self.__logger.info('Signature: {}'.format(finalParams['Signature']))
        self.__logger.debug('finalParam: {}'.format(finalParams))

        # encode post data
        data = parse.urlencode(finalParams).encode("utf-8")
        self.__logger.debug("POST urlencode(): {}".format(data))

        req = request.Request(self.__endpoint, data=data, method="POST")
        self.__logger.debug(req.full_url)
        self.__logger.debug(req.get_method())

        try:
            f = request.urlopen(req)
            response = json.loads(f.read().decode('utf-8'))

            if response.get("code") != 0:
                self.__logger.error(response)
                sys.exit(1)
            else:
                self.__logger.info(response)

            self.__logger.info("Response: {}".format(response))

            return response

        except request.HTTPError as e:
            self.__logger.info(e.read().decode('utf-8'))
            raise SystemExit(e)

    def addDomainRecord(self, domain, value):
        params = {
            'Action': 'RecordCreate',
            'domain': domain,
            'subDomain': self.__letsencryptSubDomain,
            'recordType': 'TXT',
            'recordLine': '默认',
            'value': value
            }
        self.__request(params)

    def deleteSubDomainRecord(self, domain, recordid):
        params = {
            'Action': 'RecordDelete',
            'domain': domain,
            'recordId': str(recordid),
            }
        self.__request(params)

    def getRecordId(self, domain):
        params = {
                'Action': 'RecordList',
                'domain': domain,
                'subDomain': self.__letsencryptSubDomain,
                'recordType': 'TXT'
            }
        
        response = self.__request(params)

        if response.get("code") != 0:
            self.__logger.error(response)
            sys.exit(1)
        else:
            data = response["data"]
            records = data["records"]

            targets_id = []
            for record in records:
                if record["name"] == self.__letsencryptSubDomain:
                    targets_id.append(record["id"])

            return targets_id


    def addLetsencryptDomainRecord(self, domain, value):
        self.addDomainRecord(domain, value)

    def deleteLetsencryptDomainRecord(self, domain):

        recordsId = self.getRecordId(domain)

        for recordid in recordsId:
            self.__logger.info("Delete: subDomain:{} recordId:{}".format(domain, recordid))
            self.deleteSubDomainRecord(domain, recordid)

    def __str__(self):
        return 'SecretId=' + self.__appid + ', SecretKey=' + self.__appsecret


def auth(Dns):

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

        # add letsencrypt domain record
        Dns.addLetsencryptDomainRecord(domain, value)
        logger.debug("addDomainRecord()")

        # wait for completion
        logger.info('sleep 10 secs')
        time.sleep(10)

        logger.info('Success.')
        logger.info('DNS setting end!')

    except urllib.error.HTTPError as e:
        logger.error(e)
        sys.exit(1)


def cleanup(Dns):
    
    domain = os.environ.get('CERTBOT_DOMAIN')
    if domain is None:
        raise Exception('Environment variable CERTBOT_DOMAIN is empty.')

    try:
        logger.info('Start to clean up')
        logger.info('Domain:' + domain)

        # delete letsencrypt domain record
        Dns.deleteLetsencryptDomainRecord(domain)

        logger.info('Success.')
        logger.info('Clean up end!')

    except urllib.error.HTTPError as e:
        logger.error(e)
        sys.exit(1)



Usage="""\
Usage: {} <auth|cleanup> <secretid> <secretkey>
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
        auth(TencentDns(appid, secretkey))

    elif sys.argv[1] == "cleanup":
        cleanup(TencentDns(appid, secretkey))

    else:
        logger.error(Usage)
        

if __name__ == '__main__':
    main()
