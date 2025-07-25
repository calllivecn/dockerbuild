# DDNS

## 目前只支持aliyun dns + ipv6 记录

- 默认使用 pyinstaller 打包。 ddnsclient.py 可以不打包(看看那种方便)
- 可以支持一个 ip 更新时，更新多个域名(多个域名指定同一ip)。

## ddns 服务器，只支持 linux。

- 默认只支持linux 和 安卓 termux 环境下+只支取ipv6.

- 想支持其他平台，如windwos。需要编写脚本(添加上执行权限)放到getipcmd/目录下。


## pyinstaller 打包

```shell
python -m venv /tmp/ddns

. /tmp/ddns/bin/activate

pip install -r requirements.txt
pip install -r requirements-build.txt

pyinstaller build.spec

rm -rf /tmp/ddns/

# 产物 dist/ddns/

```

## 使用容器

- docker/podman built -t ddns .
