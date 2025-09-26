#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	podman build --no-cache --build-arg https_proxy="http://10.1.3.1:9999" -t ${IMAGE_NAME} .
else
	podman build --build-arg https_proxy="http://10.1.3.1:9999" -t ${IMAGE_NAME} .
fi
