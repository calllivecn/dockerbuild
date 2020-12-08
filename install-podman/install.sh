#!/bin/bash
# date 2020-12-08 21:15:56
# author calllivecn <c-all@qq.com>

VERSION_ID=$(grep VERSION_ID /etc/os-release |cut -d'"' -f2)

echo "deb https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_${VERSION_ID}/ /" | sudo tee /etc/apt/sources.list.d/podman-stable.list

curl -L https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_${VERSION_ID}/Release.key | sudo apt-key add -

sudo apt update
sudo apt -y upgrade
sudo apt install podman
