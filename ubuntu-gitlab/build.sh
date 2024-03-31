#!/bin/bash
# date 2019-11-13 17:44:45
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi
