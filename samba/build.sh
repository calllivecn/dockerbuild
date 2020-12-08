#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

. ../libbuild-depends.sh


if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi

