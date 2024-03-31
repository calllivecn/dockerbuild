#!/bin/bash
# date 2020-11-26 23:19:30
# author calllivecn <calllivecn@outlook.com>


. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi
