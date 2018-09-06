#!/bin/bash
# date 2018-08-29 15:49:50
# author calllivecn <c-all@qq.com>

set -e

CD_DIR=$(dirname $(pwd)/${0})

BASE_DIR=$(dirname ${CD_DIR})

IMAGE_NAME=${CD_DIR##*/}

already_exists_depends(){

	if docker images --format={{.Repository}} |grep -qE ^"$1"$;then
		return 0
	else
		return 1
	fi
}

build_depends(){

	local depends_len=${#DEPENDS[@]} image

	for i in $(seq 0 $[depends_len -1])
	do
		image=${DEPENDS[${i}]}
		if already_exists_depends "${image}";then
			:
		else
			pushd ../${image}
			bash build.sh
			popd
		fi
	done
}


if [ -n $DEPAENDS ];then
	build_depends
else
	echo "current build not have depends."
fi
