#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

DEPENDS=alpine-py3
. ../libbuild-depends.sh

if [ -z "$1" ];then
	echo "请给出你的samba密码。"
	exit 1
else
	PW="$1"
fi

if [ -n $NO_CACHE ];then
	docker build --no-cache --build-arg "$PW" -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi


echo "samba username: root"
echo "samba password: ${PW}"
