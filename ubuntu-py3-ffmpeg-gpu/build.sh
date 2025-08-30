#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <calllivecn@outlook.com>

. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	TMPDIR=$HOME/.local/share/containers/tmp/ podman build --no-cache -t ${IMAGE_NAME} .
else
	TMPDIR=$HOME/.local/share/containers/tmp/ podman build -t ${IMAGE_NAME} .
fi
