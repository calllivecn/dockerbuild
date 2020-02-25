FROM ubuntu-base:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

#ARG JDK=JDK8

COPY jdk8/ /usr/local

#RUN tar -vxf /tmp/$JDK -C /usr/local/

ENV JAVA_HOME=/usr/local JRE_HOME=/usr/local


# 设置容器里的时区
RUN apt update \
&& DEBIAN_FRONTEND=noninteractive apt install -y tzdata \
&& ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& dpkg-reconfigure -f noninteractive tzdata \
&& rm -rfv /lib/apt/lists
