FROM ubuntu-base:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apt -y update \
&& apt -y install systemd \
&& apt clean \
&& rm -rf /var/lib/apt/lists
# 设置容器里的时区

CMD ["/usr/bin/systemd"]
