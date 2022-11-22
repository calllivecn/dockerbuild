#!/bin/bash
# date 2022-11-22 08:19:08
# author calllivecn <c-all@qq.com>

URL="https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_$(lsb_release -rs)"


sudo mkdir -p /etc/apt/keyrings
curl -fsSL $URL/Release.key | gpg --dearmor | sudo apt-key add -

echo"deb [arch=$(dpkg --print-architecture)] $URL/" | sudo tee /etc/apt/sources.list.d/devel:kubic:libcontainers:stable.list
sudo apt -y update
sudo apt -y install podman
