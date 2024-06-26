#!/bin/bash
# date 2020-12-08 21:15:56
# author calllivecn <calllivecn@outlook.com>

VERSION_ID=$(grep VERSION_ID /etc/os-release |cut -d'"' -f2)

echo "deb https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_${VERSION_ID}/ /" | tee /etc/apt/sources.list.d/podman-stable.list

curl -L https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_${VERSION_ID}/Release.key | apt-key add -

apt update
apt install podman
