#!/bin/bash
# date 2019-11-27 11:11:36
# author calllivecn <c-all@qq.com>

. ../libbuild-depends.sh


if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
