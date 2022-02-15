#!/usr/bin/env python3
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
import subprocess
from urllib import request

V2RAY_CONFIG_JSON = {
    "log": {
        "loglevel": "info"
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
        }
    ]
}


def runtime(prompt):
    def decorator(func):
        def warp(*args, **kwarg):
            t1 = time.time()
            result = func(*args, **kwarg)
            t2 = time.time()
            print(f"{prompt}  运行时间: {t2-t1}/s")
            return result
        return warp
    return decorator


@runtime("get subscription")
def get(url):
    req = request.Request(url, headers={"User-Agent": "curl/7.68.0"}, method="GET")
    data = request.urlopen(req)
    context = data.read()
    print("server url result:", context)
    return context


def getenv(key):
    value = os.environ.get(key)
    if value is None:
        print(f"需要 {key} 环境变量")
        sys.exit(1)
    else:
        return value


#
SERVER_URL = getenv("SERVER_URL")
print("SERVER_URL:", SERVER_URL)

# v2ray path
V2RAY_PATH = os.environ.get("V2RAY_PATH", "/v2ray")

# 多久更新一次 unit hour
UPDATE_INTERVAL = int(os.environ.get("UPDATE_INTERVAL", "8"))


def getsubscription(context):
    proxy_urls = base64.b64decode(context).decode("utf-8")
    proxys = {}

    for url in proxy_urls.split("\n"):

        if url.startswith("ss://"):
            continue
            # ss = base64.b64decode(url[5:].encode("ascii")).decode("ascii")
            # if proxys.get("ss"):
            #     proxys["ss"].append(ss)
            # else:
            #     proxys["ss"] = [ss]

        if url.startswith("vmess://"):
            try:
                vmess = base64.b64decode(url[8:]).decode("utf-8")
            except Exception:
                print(f"Error: base64.b64decode() --> {url}")
                continue

            if proxys.get("vmess"):
                proxys["vmess"].append(json.loads(vmess))
            else:
                proxys["vmess"] = [json.loads(vmess)]

        else:
            print(f"未知协议: {url}", file=sys.stderr)

    return proxys


# 目前先只支持vmess
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

    with open(os.path.join(V2RAY_PATH, "config.json"), "w") as f:
        f.write(json.dumps(V2RAY_CONFIG_JSON, ensure_ascii=False, indent=4))


def v2ray(config):
    subproc = subprocess.Popen(f"/v2ray/v2ray -config {config}".split())
    return subproc


def reboot(subproc, config):
    subproc.terminate()
    new_subproc = v2ray(config)
    return new_subproc


def main():

    print(f"第 {UPDATE_INTERVAL} 小时更新节点信息")

    v2ray_config = os.path.join(V2RAY_PATH, "config.json")

    v2ray_process = ""
    last_server_info = ""

    while True:
        server_info = get(SERVER_URL)

        if last_server_info == server_info:
            print("server 信息没更新，不重启")

        else:
            proxys = getsubscription(server_info)

            speed_sorted = test_connect_speed(proxys["vmess"])
            print("测试连接延时:")
            pprint.pprint(speed_sorted)

            updatecfg(speed_sorted[0][1])

            if subprocess.Popen == type(v2ray_process):
                reboot(v2ray_process, v2ray_config)
            else:
                v2ray_process = v2ray(v2ray_config)

            last_server_info = server_info

        days = 60*60 * UPDATE_INTERVAL
        time.sleep(days)
        T = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
        print(f"{T}: 更新节点信息")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(".....")
