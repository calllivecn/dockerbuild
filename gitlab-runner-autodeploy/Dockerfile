FROM gitlab/gitlab-runner:latest

LABEL author=zhangxu
COPY README.md /

RUN apt update \
&& apt -y install python3 python3-pip \
&& pip3 install -i https://mirrors.aliyun.com/pypi/simple/ ansible \
&& rm -rf /var/lib/apt/lists \
&& rm -rf $HOME/.cache

