FROM ubuntu-base:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apt -y update \
&& apt -y install keepalived \
&& apt clean \
&& rm -rf /var/lib/apt/lists


CMD ["/bin/bash"]
