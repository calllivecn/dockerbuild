#!/bin/bash
# date 2019-05-09 20:21:42
# author calllivecn <c-all@qq.com>

set -e

CODENAME="/etc/lsb-release"

#curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo apt-key add -

if type -p lsb_release;then
	codename="$(lsb_release -cs)"
else
	codename="$(grep $CODENAME |cut -d'=' -f2)"
fi

#echo "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list

# 19.04 19.08 直接用18.04 的codename。
#echo "deb [arch=amd64] https://mirrors.aliyun.com/docker-ce/linux/ubuntu bionic stable" | sudo tee /etc/apt/sources.list.d/docker.list

echo "deb [arch=amd64] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $codename stable" | sudo tee /etc/apt/sources.list.d/docker.list

sudo apt update

sudo apt install docker-ce

sudo gpasswd -a "$USER" docker
