#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

export TMPDIR=$HOME/.local/share/containers/tmp/ 

if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi
