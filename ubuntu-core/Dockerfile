FROM ubuntu:latest

# 1. 换源
# 2. 配置默认时区
# 3. 安装CJK字体和 locales


# 使用清华源
#RUN sed -i -e "s#archive.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#security.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list

# 24.04 源有新位置
RUN sed -i -e "s#archive.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#security.ubuntu.com#mirrors.tuna.tsinghua.edu.cn#g" -e "s#^deb#deb [arch=amd64]#g" /etc/apt/sources.list.d/ubuntu.sources \
&& apt -y update && apt -y upgrade \
&& apt -y install ca-certificates tzdata \
&& sed -i -e "s#http://#https://#g" /etc/apt/sources.list.d/ubuntu.sources \
&& apt -y install tzdata locales \
&& DEBIAN_FRONTEND=noninteractive apt install -y tzdata \
&& dpkg-reconfigure -f noninteractive tzdata \
&& ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& locale-gen en_US.UTF-8 \
&& locale-gen zh_CN.UTF-8 \
&& apt clean \
&& rm -rf /var/lib/apt/lists

ENV LANG zh_CN.UTF-8

