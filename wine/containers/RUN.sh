#!/bin/bash
# date 22020-07-03-00:00
# author calllivecn <c-all@qq.com>

set -e

if [ -n "$APT_PROXY" ];then
	echo "走了代理: $APT_PROXY" >&2
else
	echo "没走代理: $APT_PROXY" >&2
fi

sed -i -e "s#archive.ubuntu.com#mirrors.aliyun.com#g" -e "s#security.ubuntu.com#mirrors.aliyun.com#g" /etc/apt/sources.list

apt -y update

dpkg --add-architecture i386

apt -y install ca-certificates gnupg2 iproute2 vim bash-completion less iputils-ping telnet wget curl git zip unzip gosu locales fonts-wqy-microhei

# 配置中文
locale-gen en_US.UTF-8 zh_CN.UTF-8

wget -O- https://dl.winehq.org/wine-builds/winehq.key| apt-key add -

echo "deb https://dl.winehq.org/wine-builds/ubuntu/ $(grep CODENAME /etc/lsb-release |cut -d'=' -f2) main" >> /etc/apt/sources.list.d/wine.list

apt -y update 

if [ -n "$APT_PROXY" ];then
	echo "走了代理了: $APT_PROXY" >&2
	DEBIAN_FRONTEND=noninteractive apt -o Acquire::https::Proxy::dl.winehq.org=$APT_PROXY -y install --install-recommends winehq-stable
else
	echo "没走代理: $APT_PROXY" >&2
	DEBIAN_FRONTEND=noninteractive apt -y install --install-recommends winehq-stable
fi

# 创建普通用户用来启动wine

useradd -m -s /bin/bash wine

echo "export LANG=zh_CN.UTF-8" >> /home/wine/.profile

# 设置容器里的时区
ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime 

dpkg-reconfigure -f noninteractive tzdata

apt clean 

rm -rf /var/lib/apt/lists

mv -v /tmp/entrypoint.sh /


