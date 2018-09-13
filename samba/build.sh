#!/bin/bash
# date 2018-09-13 20:48:25
# author calllivecn <c-all@qq.com>

if [ -z $1 ];then
	echo "需要给一个samba密码"
	exit 1
elif [ -n $1 ];then
	sed -i "s#PW#${1}" dockerfile
	docker build -t samba .

else
	echo "Usage: sh build.sh samba_password"
fi

