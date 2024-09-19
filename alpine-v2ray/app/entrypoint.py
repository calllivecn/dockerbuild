#!/usr/bin/python3
# coding=utf-8
# date 2022-02-15 13:17:44
# author calllivecn <calllivecn@outlook.com>

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
    request,
    error,
)
from threading import (
    Thread,
)
from concurrent.futures import ThreadPoolExecutor
from logging.handlers import TimedRotatingFileHandler


V2RAY_CONFIG_JSON = None

def readcfg():
    global V2RAY_CONFIG_JSON
    config = Path(sys.argv[0]).parent / "config.json"
    with open(config) as f:
        V2RAY_CONFIG_JSON = json.load(f)


class Loger:

    def __init__(self, level=logging.INFO):

        self.fmt = logging.Formatter("%(asctime)s %(filename)s:%(lineno)d %(message)s", datefmt="%Y-%m-%d-%H:%M:%S")

        self.stream = logging.StreamHandler(sys.stdout)
        self.stream.setFormatter(self.fmt)
    
        self.logger = logging.getLogger("v2ray")
        self.logger.setLevel(level)
        self.logger.addHandler(self.stream)


    def set_logfile(self, log_dir: Path):
        # self.fp = logging.FileHandler("manager.logs")
        self.fp = TimedRotatingFileHandler(log_dir / "manager.log", when="D", interval=1, backupCount=7)
        self.fp.setFormatter(self.fmt)
        self.logger.addHandler(self.fp)

    def set_level(self, level):
        self.logger.set_level(level)


    def get_logger(self):
        return self.logger


# 2024-09-15 改为使用标准输出，查看时使用pdman logs --tail 1000 <container_name>
log = Loger()
logger = log.get_logger()


HTTPX=True
try:
    import httpx
except ModuleNotFoundError:
    HTTPX=False
    logger.warning(f"没有httpx[http2]... 使用标准库 urllib")


HEADERS = {"User-Agent": "curl/8.5.0"}

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


# 这是标准库的版本保留下。
"""
def get(url):
    req = request.Request(url, headers=HEADERS, method="GET")
    data = request.urlopen(req, timeout=30)
    context = data.read()
    return context
"""


def get2(url):
    # r = httpx.get(url, headers=HEADERS, modeht)

    with httpx.Client(http2=True, timeout=30) as client:
        r = client.get(url, headers=HEADERS)

    return r.text


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

readcfg()

SERVER_URL = getenv("SERVER_URL")
logger.debug(f"SERVER_URL: {SERVER_URL}")

# v2ray path
V2RAY_PATH = Path(os.environ.get("V2RAY_PATH", "/v2ray"))

# 多久更新一次 unit hour
UPDATE_INTERVAL = int(os.environ.get("UPDATE_INTERVAL", "3"))

# 是否也输出到文件里
# log.set_logfile(V2RAY_PATH)

# 查看当前流量使用情况，和到期时间。
def check_subscription():
    API = os.environ.get("API_COUNTER")
    if API is not None:

        try:
            # result = get(API)
            result = get2(API)
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

    # 这是使用标准库的方式
    req = request.Request(url, headers=HEADERS)

    proxy_handler = request.ProxyHandler({"http": "[::1]:9999", "https": "[::1]:9999"})

    logger.debug(f"proxy req.headers -->: {req.headers}")
    opener = request.build_opener(proxy_handler)

    result = False
    wait_sleep = 3
    for i in range(5):
        try:
            html_bytes = opener.open(req, timeout=7).read()
            result = True
            break
        except socket.timeout:
            logger.warning(f"联通性测试超时。sleep({wait_sleep}) retry {i}/5。")
        except ConnectionRefusedError as e:
            logger.warning(f"可能才刚启动，代理还没准备好。sleep({wait_sleep}) retry {i}/5")
        except error.URLError as e:
            logger.warning(f"联通性测试失败。sleep({wait_sleep}) retry {i}/5。")
        except Exception as e:
            logger.warning("".join(traceback.format_exception(e)))

        if not result:
            time.sleep(wait_sleep)
            continue

    return result


# 使用httpx + http2
def testproxy2(url="https://www.google.com/", proxy="http://[::1]:9999") -> bool:

    result = False

    with httpx.Client(http2=True, proxy=proxy, timeout=15) as client:

        wait_sleep = 3
        for i in range(5):
            try:
                html_text = client.get(url, headers=HEADERS).text
                result = True
                break
            except (socket.timeout, httpx.ConnectTimeout):
                logger.warning(f"联通性测试超时。sleep({wait_sleep}) retry {i}/5。")
            except ConnectionRefusedError as e:
                logger.warning(f"可能才刚启动，代理还没准备好。sleep({wait_sleep}) retry {i}/5")
            except error.URLError as e:
                logger.warning(f"联通性测试失败。sleep({wait_sleep}) retry {i}/5。")
            except Exception as e:
                logger.warning("".join(traceback.format_exception(e)))

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


    def v2ray(self, config: Path = None):

        # v4.xx.x
        #subproc = subprocess.run(f"/v2ray/v2ray -config {config}".split())

        # v5.0.x
        p = V2RAY_PATH / "v2ray"
        if p.exists():
            subproc = subprocess.run([str(p), "run"], cwd=V2RAY_PATH)
        else:
            subproc = subprocess.run(["/v2ray/v2ray", "run"], cwd=V2RAY_PATH)

        return subproc


    def reboot(self):
        if self._running and hasattr(self, "p"):
            self.p.terminate()
        else:
            self.__start()

    
    def kill(self):
        self._exit = True
        self.p.terminate()


    def __start(self):
        self.th = Thread(target=self.__v2ray, daemon=True)
        self.th.start()
        self._running = True


    def __v2ray(self):

        while True:
            self.p = self.v2ray(self.config)
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
            # self.server_info = get(SERVER_URL)
            self.server_info = get2(SERVER_URL)
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
            # tf = testproxy()
            tf = testproxy2()
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
