FROM alpine-core:latest


RUN apk --no-cache add python3 py3-pip \
&& pip config --global set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple \
&& rm -fr $HOME/.cache/

#&& pip config --global set install.trusted-host mirrors.aliyun.com:443 \

CMD ["ash"]

#WORKDIR /work

