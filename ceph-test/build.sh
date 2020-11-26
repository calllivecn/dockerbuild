#!/bin/bash
# date 2020-11-26 23:19:30
# author calllivecn <c-all@qq.com>


. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
