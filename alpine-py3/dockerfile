FROM alpine-core:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add python3 py3-pip \
&& pip config --global set global.index-url https://mirrors.aliyun.com/pypi/simple/ \
&& pip config --global set install.trusted-host mirrors.aliyun.com:443 \
&& rm -fr $HOME/.cache/

CMD ["ash"]

#WORKDIR /work

