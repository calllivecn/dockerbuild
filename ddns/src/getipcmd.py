"""
调用命令行脚本拿到对应ip
执行的脚本和参数可以在配置文件中指定。
脚本需要在标准输出，一个ipv4 或ipv6的字符串。
"""

import subprocess

from utils import PWD


def run(cmd: str) -> str:
    """
    执行命令获取 IPv4 地址
    :return: IP 地址
    """
    cmd_dir = PWD.absolute() / "getipcmd"
    if not cmd_dir.is_dir():
        raise FileNotFoundError(f"命令目录不存在: {cmd_dir}")

    t = cmd.split()
    cmd_ = [cmd_dir / t[0]] + t[1:]
    print(f"执行命令: {cmd_=}")
    result = subprocess.run(cmd_, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15.0)
    ip_address = result.stdout.decode('utf-8').strip()
    return ip_address

