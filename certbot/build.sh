#!/bin/bash
# date 2019-11-20 09:25:03
# author calllivecn <c-all@qq.com>

DEPENDS=alpine-py3
. ../libbuild-depends.sh


if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
