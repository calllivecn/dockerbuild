#!/bin/bash
# date 2019-05-09 20:21:42
# author calllivecn <c-all@qq.com>

set -e

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

#echo "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list
echo "deb [arch=amd64] https://mirrors.aliyun.com/docker-ce/linux/ubuntu bionic stable" | sudo tee /etc/apt/sources.list.d/docker.list

sudo apt update

sudo apt install docker-ce
