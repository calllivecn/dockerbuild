#!/bin/bash
# date 2018-08-24 23:01:51
# author calllivecn <calllivecn@outlook.com>


prefix_docker_registry="$1"

cd $(dirname ${0})

build_push(){
	for files in *;
	do
		if [ -d ${files} ] && [ -f ${files}/dockerfile ];then
			pushd ${files}
			podman build -t ${files} . \
			&& podman tag ${files} ${prefix_docker_registry}/${files} \
			&& podman push ${prefix_docker_registry}/${files} \
			&& podman rmi ${prefix_docker_registry}/${files}
			popd
		fi
	done
}

build(){

	for files in *;
	do
		if [ -d ${files} ] && [ -f ${files}/dockerfile ];then
			pushd ${files}
			podman build -t ${files} . 
			popd
		fi
	done

}
	#echo "需要你的docker registry前缀！！！(如：registry.cn-hangzhou.aliyuncs.com/calllivecn)"
	#exit 1

if [ -n "$1" ];then
	build_push
else
	build
fi
