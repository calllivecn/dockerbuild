#!/bin/bash
# date 22020-07-03-00:00
# author calllivecn <c-all@qq.com>



# step1

set -e

sed -i -e "s#archive.ubuntu.com#mirrors.aliyun.com#g" \
	-e "s#security.ubuntu.com#mirrors.aliyun.com#g" /etc/apt/sources.list

apt -y update

dpkg --add-architecture i386

apt -y install ca-certificates gnupg2 iproute2 vim bash-completion less iputils-ping telnet wget curl git zip unzip gosu

