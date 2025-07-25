"""
调用命令行脚本拿到对应ip
执行的脚本和参数可以在配置文件中指定。
脚本需要在标准输出，一个ipv4 或ipv6的字符串。
"""

import subprocess


def run(cmd: str) -> str:
    """
    执行命令获取 IPv4 地址
    :return: IP 地址
    """
    cmd_ = cmd.split()
    try:
        result = subprocess.run(cmd_, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        ip_address = result.stdout.decode('utf-8').strip()
        return ip_address
    except subprocess.CalledProcessError:
        # print(f"Error executing command: {e}", file=sys.stderr)
        return ""

