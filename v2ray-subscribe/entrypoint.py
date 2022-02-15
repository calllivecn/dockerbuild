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
from threading import Thread
from urllib import request

V2RAY_CONFIG_JSON="""\
{
  "log": {
    "loglevel": "info"
  },
  "inbounds": [
    {
      "port": 9999,
      "protocol": "http",
      "sniffing": {
        "enabled": true,
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
        "enabled": true,
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
            "port": {VMESS_PORT},
            "users": [
              {
                "id": "{VMESS_UUID}",
                "alterId": {VMESS_AID}
              }
            ]
          }
        ]
      }
    }
  ]
}
"""

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


@runtime("get subscription")
def getsubscription():
    proxy_urls = base64.b64decode(get(SERVER_URL)).decode("utf-8")
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
                print("Error: base64.b64decode() --> {url} ")
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
    print("vmess_json:\n", vmess_json)

    v2ray_config_json = V2RAY_CONFIG_JSON.format(vmess_json["add"], vmess_json["port"], vmess_json["id"], vmess_json["aid"])

    with open(os.path.join(V2RAY_PATH, "config.json"), "w") as f:
        f.write(v2ray_config_json)


# run v2ray in thread
def v2ray(config):
    subproc = subprocess.Popen(f"/v2ray/v2ray -config {config}")
    return subproc

def reboot(subproc, config):
    subproc.terminate()
    new_subproc = v2ray(config)
    return new_subproc



def main():

    proxys = getsubscription()

    speed_sorted = test_connect_speed(proxys["vmess"])
    print("测试连接延时:")
    pprint.pprint(speed_sorted)

    updatecfg(speed_sorted[0][1])

    v2ray(os.path.join(V2RAY_PATH, "config.json"))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(".....")


