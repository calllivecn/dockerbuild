#!/bin/bash
# date 2018-09-13 20:48:25
# author calllivecn <c-all@qq.com>

set -x

DOCKERFILE=dockerfile-sed

if [ -z $1 ];then
	echo "需要给一个samba密码"
	exit 1
else
	sed "s#PW#${1}#" dockerfile > ${DOCKERFILE}

	#DEPENDS=ubuntu
	. ../libbuild-depends.sh

	docker build -t ${IMAGE_NAME} -f ${DOCKERFILE} .

	echo "username: root"
	echo "passwrod: $1"

	rm ${DOCKERFILE}
fi

