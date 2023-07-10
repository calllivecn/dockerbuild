FROM ubuntu:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

#RUN sed -i -e "s#archive.ubuntu.com#mirrors.aliyun.com#g" -e "s#security.ubuntu.com#mirrors.aliyun.com#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list

# 使用清华源
RUN sed -i -e "s#archive.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#security.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list

#RUN sed -i -e "s#^deb#deb [arch=amd64]#g" -e "s#http://archive.ubuntu.com#https://mirrors.aliyun.com#g" -e "s#http://security.ubuntu.com#https://mirrors.aliyun.com#g" /etc/apt/sources.list

RUN apt -y update \
&& apt -y upgrade \
&& apt clean \
&& rm -rf /var/lib/apt/lists

