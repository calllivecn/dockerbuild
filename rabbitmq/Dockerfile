FROM ubuntu-devel:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

#&& apt -y install gcc make zlib1g-dev libssl-dev \

RUN apt -y update && apt -y upgrade \
&& apt -y install rabbitmq-server \
&& apt clean \
&& rm -rf /var/lib/apt/lists
