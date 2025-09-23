#!/bin/bash
# date 2018-08-29 15:49:50
# author calllivecn <calllivecn@outlook.com>

set -e

BASE_IMAGES="alpine ubuntu centos"

CD_DIR=$(dirname $(pwd)/${0})

BASE_DIR=$(dirname ${CD_DIR})

IMAGE_NAME=${CD_DIR##*/}

# podman build 时使用的目录：
export TMPDIR=$HOME/.local/share/containers/tmp/


get_depend(){
	local DF="$CD_DIR/Dockerfile"
	if [ ! -f "$DF" ];then
		echo "当前目录没有 Dockerfile 请检查"
		exit 1
	fi

	grep -m 1 -oE '^FROM (.*):' "$DF" |cut -d' ' -f2 |cut -d':' -f1
}

DEPENDS=$(get_depend)

no_cache(){

	local arg

	for arg in "$@"
	do

		if [ "$arg"x = "--no-cache"x ];then
			NO_CACHE=1
			shift
		fi
	done
}
no_cache

docker_build(){
	if [ -n $NO_CACHE ];then
		bash build.sh --no-cache
	else
		bash build.sh
	fi
}

already_exists_depends(){

	if podman images --format={{.Repository}} |grep -qE "/?$1"$;then
		return 0
	else
		return 1
	fi
}

check_base_images(){
	# T is true, F is false
	local img flag=F

	for img in ${BASE_IMAGES}
	do
		if [ "${DEPENDS}"x = "$img"x ];then
			flag=T
		fi
	done


	if [ $flag = "T" ];then
		return 0
	else
		if [ -z $DEPENDS ];then
			echo "need \$DEPENDS"
			exit 1
		fi
		return 1
	fi
}

build_depends(){

	if already_exists_depends "${DEPENDS}";then
		:
	else
		if check_base_images;then
			podman pull "${DEPENDS}"
		else
			pushd ../${DEPENDS}
			docker_build
			popd
		fi
	fi

}


if [ -n "${DEPENDS}" ];then
	build_depends
else
	echo "current build not have depends."
fi
