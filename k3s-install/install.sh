#!/bin/bash
# date 2020-09-01 09:37:55
# author calllivecn <c-all@qq.com>


export INSTALL_K3S_SKIP_DOWNLOAD=true


if [ -f /usr/local/bin/k3s ];then
	:
else
	echo "去手动下载吧: https://github.com/rancher/k3s/releases"
	#echo "wget https://github.com/rancher/k3s/releases/download/v1.0.0/k3s"
	exit 1
fi

set -e

TMP=$(mktemp -d)

clear_exit(){
	rm -rfv "$TMP"
}

trap clear_exit EXIT ERR SIGINT SIGTERM

curl -o "$TMP/k3s-install.sh" https://raw.githubusercontent.com/rancher/k3s/master/install.sh

sh "$TMP/k3s-install.sh"
