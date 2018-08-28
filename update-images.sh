#!/bin/bash
# date 2018-08-24 23:01:51
# author calllivecn <c-all@qq.com>

if [ -z "$1" ];then
	echo "需要你的docker registry前缀！！！(如：registry.cn-hangzhou.aliyuncs.com/calllivecn)"
	exit 1
fi
prefix_docker_registry="$1"

cd $(dirname ${0})

for files in *;
do
	if [ -d ${files} ] && [ -f ${files}/dockerfile ];then
		pushd ${files}
		docker build -t ${files} . \
		&& docker tag ${files} ${prefix_docker_registry}/${files} \
		&& docker push ${prefix_docker_registry}/${files} \
		&& docker rmi ${prefix_docker_registry}/${files}
		popd
	fi
done
