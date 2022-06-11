#!/usr/bin/python3
# coding=utf-8
# date 2022-02-15 13:17:44
# author calllivecn <c-all@qq.com>

import os
import sys
import ssl
import time
import json
import base64
import pprint
import socket
import logging
import argparse
import subprocess
from pathlib import Path
from urllib import request

V2RAY_CONFIG_JSON = {
    "log": {
        "loglevel": "info",
        "access": "access.logs",
        "error": "error.logs"
    },
    "inbounds": [
        {
            "port": 9999,
            "protocol": "http",
            "sniffing": {
                "enabled": True,
                "destOverride": ["http", "tls"]
            },
            "settings": {
                "auth": "noauth"
            }
        },
        {
            "port": 10000,
            "protocol": "socks",
            "sniffing": {
                "enabled": True,
                "destOverride": ["http", "tls"]
            },
            "settings": {
                "auth": "noauth"
            }
        }
    ],
    "outbounds": [
        {
            "protocol": "vmess",
            "settings": {
                "vnext": [
                    {
                        "address": "{VMESS_ADDR}",
                        "port": "{VMESS_PORT}",
                        "users": [
                            {
                                "id": "{VMESS_UUID}",
                                "alterId": "{VMESS_AID}"
                            }
                        ]
                    }
                ]
            }
        },
        {
            "protocol": "shadowsocks",
            "settings":{
                "servers": [
                    {
                        "address": "{SS_ADDR}",
                        "port": "{SS_PORT}",
                        "method": "aes-256-gcm",
                        "password": "{PW}",
                        "ota": False,
                        "level": 0
                    }
                ]
            }
        }
    ]
}

def getlogger(level=logging.INFO):
    fmt = logging.Formatter("%(asctime)s %(filename)s:%(lineno)d %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")

    stream = logging.StreamHandler(sys.stdout)
    stream.setFormatter(fmt)

    fp = logging.FileHandler("manager.logs")
    fp.setFormatter(fmt)

    logger = logging.getLogger("AES")
    logger.setLevel(level)
    logger.addHandler(stream)
    logger.addHandler(fp)
    return logger


logger = getlogger()


def runtime(prompt):
    def decorator(func):
        def warp(*args, **kwarg):
            t1 = time.time()
            result = func(*args, **kwarg)
            t2 = time.time()
            logger.info(f"{prompt} 运行时间: {t2-t1}/s")
            return result
        return warp
    return decorator


def get(url):
    req = request.Request(url, headers={"User-Agent": "curl/7.68.0"}, method="GET")

    if os.environ.get("SKIP_CA"):
        ctx = ssl.SSLContext()
        data = request.urlopen(req, context=ctx)
    else:
        data = request.urlopen(req)

    context = data.read()
    return context


def getenv(key):
    value = os.environ.get(key)
    if value is None:
        logger.info(f"需要 {key} 环境变量")
        sys.exit(1)
    else:
        return value

# base64 补上=号
def check_b64(data):
    return data + b"=" * (4 - len(data) % 4)

#
SERVER_URL = getenv("SERVER_URL")
logger.debug(f"SERVER_URL: {SERVER_URL}")

# v2ray path
V2RAY_PATH = Path(os.environ.get("V2RAY_PATH", "/v2ray"))

# 多久更新一次 unit hour
UPDATE_INTERVAL = int(os.environ.get("UPDATE_INTERVAL", "8"))

# 查看当前流量使用情况，和到期时间。
def check_subscription():
    API = os.environ.get("API_COUNTER")
    if API is not None:

        try:
            result = get(API)
        except Exception:
            return

        j = json.loads(result) 

        total = j["monthly_bw_limit_b"]
        use = j["bw_counter_b"]
        reset_day = j["bw_reset_day_of_month"]
        
        total_GB = round(total/(1<<30), 2)
        use_GB = round(use/(1<<30), 2)
        percentage = round(use/total*100, 2)

        logger.info(f"当前总共流量 {total_GB}G，当前流量使用: {use_GB}GB 当前已使用百分比： {percentage}% 流量重置日期：{reset_day}")


@runtime("get subscription")
def getsubscription(context):
    proxy_urls = base64.b64decode(context)
    proxys = {}

    for url in proxy_urls.split(b"\n"):

        if url.startswith(b"ss://"):
            logger.warning(f"目前先不支持ss: {url}")

            # ss = base64.b64decode(check_b64(url[5:])).decode("utf8")
            # if proxys.get("ss"):
                # proxys["ss"].append(ss)
            # else:
                # proxys["ss"] = [ss]


        # elif url.startswith(b"ssr://"):
        #     logger.debug(f"ssr 协议")

        #     try:
        #         vmess = base64.b64decode(check_b64(url[6:]))
        #     except Exception:
        #         logger.error(f"Error: base64.b64decode() --> {url}")
        #         continue

        #     # 加入到proxy 
        #     if proxys.get("ssr"):
        #         proxys["ssr"].append(json.loads(vmess))
        #     else:
        #         proxys["ssr"] = [json.loads(vmess)]


        elif url.startswith(b"vmess://"):
            try:
                vmess = base64.b64decode(check_b64(url[8:])).decode("utf8")
            except Exception:
                logger.error(f"Error: base64.b64decode() --> {url}")
                continue

            # 加入到proxy 
            if proxys.get("vmess"):
                proxys["vmess"].append(json.loads(vmess))
            else:
                proxys["vmess"] = [json.loads(vmess)]

        else:
            logger.warning(f"未知协议: {url}")

    return proxys


# 目前先只支持vmess
@runtime("测试链接速度")
def test_connect_speed(vmess_list):
    score = []
    for vmess in vmess_list:

        t1 = time.time()
        sock = socket.create_connection((vmess["add"], vmess["port"]))
        t2 = time.time()
        sock.close()

        score.append((round((t2 - t1)*1000), vmess))

    return sorted(score, key=lambda x: x[0])


def updatecfg(vmess_json):

    outbounds = [
        {
            "protocol": "vmess",
            "settings": {
                "vnext": [
                    {
                        "address": vmess_json["add"],
                        "port": int(vmess_json["port"]),
                        "users": [
                            {
                                "id": vmess_json["id"],
                                "alterId": vmess_json["aid"]
                            }
                        ]
                    }
                ]
            }
        }
    ]

    V2RAY_CONFIG_JSON["outbounds"] = outbounds

    with open(V2RAY_PATH / "config.json", "w") as f:
        f.write(json.dumps(V2RAY_CONFIG_JSON, ensure_ascii=False, indent=4))


def v2ray(config):

    # v4.xx.x
    #subproc = subprocess.Popen(f"/v2ray/v2ray -config {config}".split())

    # v5.0.x
    p = V2RAY_PATH / "v2ray"
    if p.exists():
        subproc = subprocess.Popen(f"{p} run".split())
    else:
        subproc = subprocess.Popen(f"/v2ray/v2ray run".split())

    return subproc


def reboot(subproc, config):

    for i in range(5):
        recode = subproc.terminate()

        if subproc.poll() is not None:
            break

        logger.info(f"terminate() retry {i}/5")
        if recode is None:
            time.sleep(1)
        else:
            logger.info(f"terminate() ok")
            break

    # 如果subprc 是结束，send_signal() 什么也不做。
    subproc.send_signal(9)

    new_subproc = v2ray(config)
    return new_subproc


def main():

    parse = argparse.ArgumentParser()
    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.debug:
        logger.setLevel(logging.DEBUG)

    logger.info(f"第 {UPDATE_INTERVAL} 小时更新节点信息")

    v2ray_config = os.path.join(V2RAY_PATH, "config.json")

    v2ray_process = ""
    last_server_info = ""

    while True:
        server_info = get(SERVER_URL)

        if last_server_info == server_info:
            logger.info("server 信息没更新，不重启")

        else:
            logger.info(f"server url result: {server_info}")

            proxys = getsubscription(server_info)

            try:
                speed_sorted = test_connect_speed(proxys["vmess"])
            except Exception as e:
                logger.info(f"测试连异常: {e}")
                logger.info("使用第一个vmess地址")
                updatecfg(proxys["vmess"][0][1])
            else:
                logger.info("测试连接延时:\n" + pprint.pformat(speed_sorted))
                updatecfg(speed_sorted[0][1])

            if subprocess.Popen == type(v2ray_process):
                v2ray_process = reboot(v2ray_process, v2ray_config)
            else:
                v2ray_process = v2ray(v2ray_config)

            last_server_info = server_info
        
        check_subscription()

        days = 60*60 * UPDATE_INTERVAL
        for i in range(days):
            if v2ray_process.poll() is not None:
                break
            time.sleep(1)

        logger.info(f"更新节点信息")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        logger.info("Ctrl+C exit...")
