#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

DEPENDS=ubuntu
. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
