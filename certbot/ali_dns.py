#!/usr/bin/env python3
# coding=utf-8
# date 2019-11-20 16:34:16
# author calllivecn <c-all@qq.com>


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


#import aliyun

# Set the global configuration
#CONFIG_FILENAME = 'config.ini'
#configFilepath = os.path.split(os.path.realpath(__file__))[0] + os.path.sep + CONFIG_FILENAME
#config = configparser.ConfigParser()
#config.read(configFilepath)

logger = logging.getLogger("logger")
formatter = logging.Formatter("%(asctime)s %(filename)s:%(lineno)d %(levelname)s: %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")
consoleHandler = logging.StreamHandler(stream=sys.stdout)

logger.setLevel(logging.INFO)

consoleHandler.setLevel(logging.DEBUG)
consoleHandler.setFormatter(formatter)
logger.addHandler(consoleHandler)

#fileLogFlag = True if config.get('log','enable').lower() == 'true' else False
#if fileLogFlag:
#    logfile = config.get('log','logfile')
#    fileHandler = logging.FileHandler(filename=logfile)
#    fileHandler.setLevel(logging.DEBUG)
#    fileHandler.setFormatter(formatter)
#    logger.addHandler(fileHandler)


class AliyunDns:
    __endpoint = 'https://alidns.aliyuncs.com'
    __letsencryptSubDomain = '_acme-challenge'
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
