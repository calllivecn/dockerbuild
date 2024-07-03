#!/usr/bin/env python3
# coding=utf-8
# date 2019-11-20 16:34:16
# update 2024-07-03 22:06:27
# author calllivecn <calllivecn@outlook.com>


import os
import sys
import time
import logging
import traceback
import configparser

from pathlib import Path


from aliyunlib import AliDDNS


CFG = Path('ali_dns.conf')
config = configparser.ConfigParser()
config.read(CFG)

ACCESS_KEY_ID = config.get("AliDNSKey", "ACCESS_KEY_ID")
ACCESS_KEY_SECRET = config.get("AliDNSKey", "ACCESS_KEY_SECRET")

logger = logging.getLogger("logger")
formatter = logging.Formatter("%(asctime)s %(filename)s:%(lineno)d %(levelname)s: %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")
consoleHandler = logging.StreamHandler(stream=sys.stdout)
#logger.setLevel(logging.DEBUG)
logger.setLevel(logging.INFO)
consoleHandler.setLevel(logging.DEBUG)
consoleHandler.setFormatter(formatter)
logger.addHandler(consoleHandler)


__letsencryptSubDomain = '_acme-challenge'


def auth():

    alidns = AliDDNS(ACCESS_KEY_ID, ACCESS_KEY_SECRET)

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
        result = alidns.addDomainRecord(domain, __letsencryptSubDomain, "TXT", value)
        logger.debug(f"addDomainRecord() --> {result}")

        # wait for completion
        logger.info('sleep 10 secs')
        time.sleep(10)

        logger.info('Success.')
        logger.info('DNS setting end!')

    except Exception as e:
        logger.error(e)
        sys.exit(1)


def cleanup():

    alidns = AliDDNS(ACCESS_KEY_ID, ACCESS_KEY_SECRET)

    domain = os.environ.get('CERTBOT_DOMAIN')
    
    if domain is None:
        raise Exception('Environment variable CERTBOT_DOMAIN is empty.')

    try:
        logger.info('Start to clean up')
        logger.info('Domain:' + domain)

        # delete letsencrypt domain record
        alidns.deleteDomainRecord(domain, __letsencryptSubDomain, "TXT")

        logger.info('Success.')
        logger.info('Clean up end!')

    except Exception as e:
        traceback.print_exception(e)
        logger.error(e)
        sys.exit(1)



Usage="""\
Usage: {} <auth|cleanup>
And: set environment CERTBOT_DOMAIN CERTBOT_VALIDATION
""".format(sys.argv[0])


def main():

    if len(sys.argv) != 2:
        logger.error(f"Usage: {sys.argv[0]} <auth|cleanup>") 
        sys.exit(1)
    
    if sys.argv[1] == "auth":
        auth()

    elif sys.argv[1] == "cleanup":
        cleanup()

    else:
        logger.error(Usage)
        

if __name__ == '__main__':
    main()
