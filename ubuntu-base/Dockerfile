FROM ubuntu-core:latest


RUN apt -y update \
&& apt -y --no-install-recommends install iproute2 vim bash-completion less iputils-ping telnet wget curl git \
&& DEBIAN_FRONTEND=noninteractive apt install -y tzdata \
&& dpkg-reconfigure -f noninteractive tzdata \
&& ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& apt clean \
&& rm -rf /var/lib/apt/lists
# 设置容器里的时区

