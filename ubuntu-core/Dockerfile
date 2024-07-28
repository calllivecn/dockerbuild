FROM ubuntu:latest



# 使用清华源
#RUN sed -i -e "s#archive.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#security.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list

# 24.04 源有新位置
RUN sed -i -e "s#archive.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#security.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list.d/ubuntu.sources


RUN apt -y update \
&& apt -y upgrade \
&& apt clean \
&& rm -rf /var/lib/apt/lists

