#!/bin/bash
# date 2018-09-13 20:48:25
# author calllivecn <c-all@qq.com>

set -x

if [ -z $1 ];then
	echo "需要给一个samba密码"
	exit 1
else
	sed -i "s#PW#${1}#" dockerfile
	docker build -t samba .
fi

