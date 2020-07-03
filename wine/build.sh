#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>


DEPENDS=ubuntu

IMAGE_NAME="wine"

func1(){
cat >dockerfile<<EOF
FROM ${IMAGES_NAME}:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

COPY containers/ /tmp/

RUN bash /tmp/run-step1.sh \
&& rm -rf /tmp/*

EOF

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi

[ -f dockerfile ] && rm dockerfile

}

func2(){
cat >dockerfile<<EOF
FROM ${IMAGES_NAME}-step1:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

ARG APT_PROXY=$APT_PROXY

COPY containers/ /tmp/

RUN bash /tmp/run-step2.sh \
&& mv /tmp/entrypoint.sh / \
&& rm -rf /tmp/*

WORKDIR /home/wine

CMD ["/entrypoint.sh"]

EOF

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi

[ -f dockerfile ] && rm dockerfile

}


build_step(){
	local func="$1" step exec_step="$2" step_f="build.step"

	if [ -z "$func" ] || [ -z "$exec_step" ];then
		echo "Usage: build_step <func4> <4> 前执行第4步 func4函数"
		exit 1
	fi

	if [ -f "$step_f" ];then
		step=$(cat "$step_f")
	else
		step=1
		echo "$step" > "$step_f"
	fi

	# 如果当前步骤小于函数的step跳过此函数
	if [ $exec_step -lt $step ];then

		echo "$func 已执行成功， 跳过。"

	elif [ $exec_step -eq $step ];then

		echo "$func 执行。。。"
		$func

	elif [ $exec_step -gt $step ];then

		echo "前面有步骤没有执行，成功。请debug"
		exit 1

	fi

}


build_step func1 1

build_step func2 2
