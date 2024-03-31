#!/bin/bash
# date 2021-11-03 17:11:25
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi
