#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>


DEPENDS=ubuntu
. ../libbuild-depends.sh

set +e

cat >dockerfile<<EOF
FROM ${DEPENDS}:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN sed -i -e "s#archive.ubuntu.com#mirrors.aliyun.com#g" -e "s#security.ubuntu.com#mirrors.aliyun.com#g" /etc/apt/sources.list \
&& apt -y update \
&& dpkg --add-architecture i386 \
&& apt -y install ca-certificates gnupg2 iproute2 vim bash-completion less iputils-ping telnet wget curl git \
&& wget -O- https://dl.winehq.org/wine-builds/winehq.key| apt-key add - \
&& echo "deb https://dl.winehq.org/wine-builds/ubuntu/ $(grep CODENAME /etc/lsb-release |cut -d'=' -f2) main" >> /etc/apt/sources.list.d/wine.list \
&& apt -y update \
&& DEBIAN_FRONTEND=noninteractive apt -y install --install-recommends winehq-stable \
&& ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& dpkg-reconfigure -f noninteractive tzdata  \
&& apt clean \
&& rm -rf /var/lib/apt/lists

EOF

# 设置容器里的时区

## && DEBIAN_FRONTEND=noninteractive apt install -y tzdata \
## && ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
## && dpkg-reconfigure -f noninteractive tzdata 

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi

[ -f dockerfile ] && rm dockerfile
