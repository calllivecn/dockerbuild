#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	podman build --build-arg USER=$USER --build-arg UID=$UID --no-cache -t ${IMAGE_NAME} .
else
	podman build --build-arg USER=$USER --build-arg UID=$UID -t ${IMAGE_NAME} .
fi
