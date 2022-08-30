#!/usr/bin/python3
# coding=utf-8
# date 2022-02-15 13:17:44
# author calllivecn <c-all@qq.com>

import os
import sys
import time
import json
import base64
import pprint
import socket
import signal
import logging
import argparse
import subprocess
from pathlib import Path
from urllib import parse, request
from threading import (
    Lock,
    Thread,
)

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
    req = request.Request(url, headers={"User-Agent": "curl/7.81.0"}, method="GET")
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


def signal_handle(subproc):
    subproc.terminate()
    logger.info(f"subproc terminate()")
    sys.exit(0)


# 访问 google 测试连通性
def testproxy(url="https://www.google.com/"):

    headers = {"User-Agent": "curl/7.68.0"}

    req = request.Request(url, headers=headers)

    proxy_handler = request.ProxyHandler({"http": "[::1]:9999", "https": "[::1]:9999"})

    logger.debug("proxy req.headers -->:\n", req.headers)
    opener = request.build_opener(proxy_handler)
    try:
        html_bytes = opener.open(req, timeout=7).read()
    except socket.timeout:
        return False

    return True



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
        try:
            sock = socket.create_connection((vmess["add"], vmess["port"]), timeout=7)
        except socket.timeout:
            logger.warning(f"测试连接速度超时: {vmess['add']} {vmess['port']}")
            continue

        t2 = time.time()
        sock.close()

        score.append((round((t2 - t1)*1000), vmess))

    return sorted(score, key=lambda x: x[0])


def updatecfg(vmess_json):

    outbounds = []
    for _delay, vmess in vmess_json:
        v = {
            "protocol": "vmess",
            "settings": {
                "vnext": [
                    {
                        "address": vmess["add"],
                        "port": int(vmess["port"]),
                        "users": [
                            {
                                "id": vmess["id"],
                                "alterId": vmess["aid"]
                            }
                        ]
                    }
                ]
            }
        }
        outbounds.append(v)

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

    logger.info(f"v2ray pid: {subproc.pid}")
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

    # 如果 subprc 是结束，send_signal() 什么也不做。
    subproc.send_signal(9)

    new_subproc = v2ray(config)
    logger.info(f"v2ray pid: {new_subproc.pid}")
    return new_subproc


def v2ray_manager(reboot_lock: Lock, kill_lock):
    """
    需要重启的情况：
    1. 进程退出？
    2. testproxy() --> False
    3. 配置更新？
    """
    while True:
        if reboot_lock.acquire(timeout=7):
            reboot


def main():

    parse = argparse.ArgumentParser()
    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.debug:
        logger.setLevel(logging.DEBUG)

    logger.info(f"每 {UPDATE_INTERVAL} 小时更新节点信息")

    v2ray_config = os.path.join(V2RAY_PATH, "config.json")

    v2ray_process = ""
    last_server_info = ""
    # 连续请求最小间隔
    MIN_INTERVAL = 30
    min_interval = 0

    signal.signal(signal.SIGTERM, lambda sig, frame: signal_handle(v2ray_process))

    while True:
        # 防止短时间内请求订阅地址过多被ban。
        t = time.time()
        if (t - min_interval) > MIN_INTERVAL:
            server_info = get(SERVER_URL)
            check_subscription()
            min_interval = time.time()
        else:
            logger.warning(f"短时间内请求订阅地址过多, 本次暂不请求。")


        if last_server_info == server_info:
            logger.info("server 信息没更新，不重启")
        else:
            logger.info(f"server url result: {server_info}")

            proxys = getsubscription(server_info)

            speed_sorted = test_connect_speed(proxys["vmess"])
            logger.info("测试连接延时:\n" + pprint.pformat(speed_sorted))
            updatecfg(speed_sorted)

            if subprocess.Popen == type(v2ray_process):
                v2ray_process = reboot(v2ray_process, v2ray_config)
            else:
                v2ray_process = v2ray(v2ray_config)

            last_server_info = server_info
        

        # 如果 v2ray 已退出，起个新的
        if v2ray_process.poll() is not None:
            v2ray_process = reboot(v2ray_process, v2ray_config)

        days = 60*60 * UPDATE_INTERVAL
        # sleep_interval = 60*5
        # for i in range(0, days, sleep_interval):
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
