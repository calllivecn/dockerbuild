#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <calllivecn@outlook.com>


set +e

IMAGE_NAME=wine

cat >Dockerfile<<EOF
FROM ubuntu-devel:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apt update \
&& dpkg --add-architecture i386 \
&& apt -y install ca-certificates gnupg2 iproute2 vim bash-completion less iputils-ping telnet wget curl git zip unzip gosu locales fonts-wqy-microhei \
&& locale-gen en_US.UTF-8 zh_CN.UTF-8 \
&& apt -y install wine \
&& useradd -m -s /bin/bash wine \
&& echo "export LANG=zh_CN.UTF-8" >> /home/wine/.profile \
&& apt clean

WORKDIR /home/wine

COPY entrypoint.sh /

CMD ["/entrypoint.sh"]
EOF


podman build -t ${IMAGE_NAME} .

[ -f Dockerfile ] && rm Dockerfile
