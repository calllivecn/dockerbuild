#!/bin/bash
# date 2018-08-29 15:49:50
# author calllivecn <c-all@qq.com>

set -e

CD_DIR=$(dirname $(pwd)/${0})

BASE_DIR=$(dirname ${CD_DIR})

IMAGE_NAME=${CD_DIR##*/}

no_cache(){

	local arg

	for arg in "$@"
	do

		if [ -z "$arg"x = "--no-cache"x ];then
			NO_CACHE=1
			shift
		fi
	done
}
no_cache

docker_build(){
	if [ -n $NO_CACHE ];then
		bash build.sh
	else
		bash build.sh --no-cache
	fi
}

already_exists_depends(){

	if docker images --format={{.Repository}} |grep -qE ^"$1"$;then
		return 0
	else
		return 1
	fi
}

build_depends(){

	if already_exists_depends "${DEPENDS}";then
		:
	else
		pushd ../${DEPENDS}
		docker_build
		popd
	fi

}


if [ -n "${DEPENDS}" ];then
	build_depends
else
	echo "current build not have depends."
fi
