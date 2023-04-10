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
import traceback
import subprocess
from queue import Queue
from pathlib import Path
from urllib import (
    parse,
    request,
    error,
)
from threading import (
    Lock,
    Thread,
)
from concurrent.futures import ThreadPoolExecutor
from logging.handlers import TimedRotatingFileHandler


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

    # fp = logging.FileHandler("manager.logs")
    fp = TimedRotatingFileHandler("manager.logs", when="D", interval=1, backupCount=7)
    fp.setFormatter(fmt)

    logger = logging.getLogger("AES")
    logger.setLevel(level)
    logger.addHandler(stream)
    logger.addHandler(fp)
    return logger


logger = getlogger()


HEADERS={"User-Agent": "curl/7.81.0"}

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
    req = request.Request(url, headers=HEADERS, method="GET")
    data = request.urlopen(req, timeout=30)
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


##################
#
# begin
#
##################



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
        except Exception as e:
            logger.warning("".join(traceback.format_exception(e)))
            logger.warning(f"请求流量使用信息出错")
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
    # subproc.terminate()
    subproc.kill()
    sys.exit(0)


# 访问 google 测试连通性
def testproxy(url="https://www.google.com/"):

    req = request.Request(url, headers=HEADERS)

    proxy_handler = request.ProxyHandler({"http": "[::1]:9999", "https": "[::1]:9999"})

    logger.debug(f"proxy req.headers -->: {req.headers}")
    opener = request.build_opener(proxy_handler)

    result = True
    wait_sleep = 3
    for i in range(5):
        try:
            html_bytes = opener.open(req, timeout=7).read()
            result = True
            break
        except socket.timeout:
            logger.warning(f"联通性测试超时。sleep({wait_sleep}) retry {i}/5。")
            result = False
        except ConnectionRefusedError as e:
            logger.warning(f"可能才刚启动，代理还没准备好。sleep({wait_sleep}) retry {i}/5")
            result = False
        except error.URLError as e:
            logger.warning(f"联通性测试失败。sleep({wait_sleep}) retry {i}/5。")
            result = False
        except Exception as e:
            logger.warning("".join(traceback.format_exception(e)))
            result = False

        if not result:
            time.sleep(wait_sleep)
            continue

    return result



def decode_subscription(context):
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


def test_connect(addr: tuple[str, int]) -> int:

    t1 = time.time()
    try:
        sock = socket.create_connection(addr, timeout=7)
    except socket.timeout:
        logger.warning(f"测试连接速度超时: {addr}")
        return None

    sock.close()
    t2 = time.time()
    return round((t2 - t1)*1000)


# 目前先只支持TCP
@runtime("测试链接速度")
def test_connect_speed(vmess_list):
    score = []
    for vmess in vmess_list:
        t = test_connect((vmess["add"], vmess["port"]))
        if t is not None:
            score.append((t, vmess))

    return sorted(score, key=lambda x: x[0])


@runtime("测试链接速度")
def test_connect_speed_thread(vmess_list):
    score = []

    pool = ThreadPoolExecutor(max_workers=50)
    map_result = pool.map(test_connect, ((vmess["add"], vmess["port"]) for vmess in vmess_list))

    for t, vmess in zip(map_result, vmess_list):
        if t is not None:
            score.append((t, vmess))

    pool.shutdown()

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
        subproc = subprocess.Popen(f"{p} run".split(), cwd=V2RAY_PATH)
    else:
        subproc = subprocess.Popen(f"/v2ray/v2ray run".split(), cwd=V2RAY_PATH)

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
    # subproc.send_signal(9)

    new_subproc = v2ray(config)
    logger.info(f"v2ray pid: {new_subproc.pid}")
    return new_subproc


class v2ray_manager:
    """
    需要重启的情况：
    1. 进程退出？
    2. testproxy() --> False
    3. 配置更新？
    """

    SIG_REBOOT = 1

    def __init__(self, config: Path):
        self.config = config
        self.q = Queue(4)
        self._running = False
        self._exit = False

    def __start(self):
        self.th = Thread(target=self.__v2ray, daemon=True)
        self.th.start()
        self._running = True

    def reboot(self):
        if self._running:
            self.p.terminate()
        else:
            self.__start()
    
    def kill(self):
        self._exit = True
        self.p.terminate()

    def __v2ray(self):

        while True:
            self.p = v2ray(self.config)
            logger.info(f"启动 v2ray pid: {self.p.pid}")
            recode = self.p.wait()

            if self._exit:
                logger.info(f"terminal() pid:{self.p.pid}")
                break

            logger.info(f"v2ray 退出 recode: {recode}, sleep 1 重启")
            time.sleep(1)


class JustMySock:

    def __init__(self):

        # 连续请求最小间隔
        self.MIN_INTERVAL = 30
        self._t_init = 0

        self.last_server_info = ""

        self.conn_fail = False

    def get_subscription(self):
        t = time.time()
        if (t - self._t_init) > self.MIN_INTERVAL:
            logger.info(f"更新节点信息...")
            self.server_info = get(SERVER_URL)
            check_subscription()
            self._t_init = t
        else:
            logger.warning(f"短时间内请求订阅地址过多, 本次暂不请求。")

        if self.last_server_info == self.server_info:
            logger.info(f"server 信息没更新。")
            self.test_speed()
            self.updated = True
        else:
            logger.info(f"server url result: {self.server_info}")
            self.test_speed()
            self.last_server_info = self.server_info
            self.updated = True

    def test_speed(self):
        self.proxys = decode_subscription(self.server_info)
        # speed_sorted = test_connect_speed(self.proxys["vmess"])
        speed_sorted = test_connect_speed_thread(self.proxys["vmess"])
        logger.info("测试连接延时:\n" + pprint.pformat(speed_sorted))
        if len(speed_sorted) == 0:
            logger.info(f"没有连接...")
            return
        else:
            updatecfg(speed_sorted)


def main():

    parse = argparse.ArgumentParser()
    parse.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

    args = parse.parse_args()

    if args.debug:
        logger.setLevel(logging.DEBUG)

    logger.info(f"每 {UPDATE_INTERVAL} 小时更新节点信息")

    v2ray_config = V2RAY_PATH / "config.json"

    v2ray_process = v2ray_manager(v2ray_config)

    jms = JustMySock()

    signal.signal(signal.SIGTERM, lambda sig, frame: signal_handle(v2ray_process))

    while True:

        try:
           jms.get_subscription()
        except Exception as e:
            logger.warning("".join(traceback.format_exception(e)))
            logger.warning(f"请求订阅出错")
            time.sleep(30)
            continue


        if jms.conn_fail or jms.updated:
            v2ray_process.reboot()

        days = 60*60 * UPDATE_INTERVAL
        sleep_interval = 60*5
        for i in range(0, days, sleep_interval):
            pt1 = time.time()
            tf = testproxy()
            pt2 = time.time()
            if tf:
                logger.info(f"联通性测试ok")
            else:
                logger.warning(f"联通性测试失败, 更新配置或重启。")
                jms.conne_fail = True
                break
            time.sleep(sleep_interval - (pt2-pt1))



if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        os.kill(os.getpid(), 15)
        logger.info("Ctrl+C exit...")
