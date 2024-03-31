#!/bin/bash
# date 2023-01-31 22:33:18
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh


if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi

