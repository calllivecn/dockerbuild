#!/bin/bash
# date 2019-11-20 09:25:03
# author calllivecn <c-all@qq.com>

DEPENDS=py3
. ../libbuild-depends.sh

# aliyun DNS API 操作

if [ -z "$APPID" ] || [ -z "$SECRETKEY" ];then
	echo "需要Aliyun 的AppId 和 SecretKey"
	echo 'export APPID=${APPID} SECRETKEY=${SECRETKEY}'
	echo 'Or: APPID=${APPID} SECRETKEY=${SECRETKEY} sh build.sh'
	exit 1
fi


echo "[aliyun]" > config.ini
echo "appid=$APPID" >> config.ini
echo "secretkey=${SECRETKEY}" >> config.ini

echo "[log]" >> config.ini
echo "enable=false" >> config.ini
echo "logfile=/var/log/manual-hook.log" >> config.ini


if [ -f aliyun.py ];then
	:
else
	wget https://github.com/broly8/letsencrypt-aliyun-dns-manual-hook/raw/master/aliyun.py
fi

if [ -f manual-hook.py ];then
	:
else
	wget https://github.com/broly8/letsencrypt-aliyun-dns-manual-hook/raw/master/manual-hook.py
fi


if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
