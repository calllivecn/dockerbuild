#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

DEPENDS=ubuntu
. ../libbuild-depends.sh

if [ -z "$1" ];then
	echo "请给出你的samba密码。"
	exit 1
fi

SED=dockerfile-sed

sed "s#PW#${1}#" dockerfile > ${SED}

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} -f ${SED} .
else
	docker build -t ${IMAGE_NAME} -f ${SED} .
fi

rm -rf ${SED}

echo "samba username: root"
echo "samba password: ${1}"
