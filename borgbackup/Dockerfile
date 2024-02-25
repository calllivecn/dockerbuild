FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add borgbackup openssh \
&& rm -fr $HOME/.cache/

#&& pip config --global set install.trusted-host mirrors.aliyun.com:443 \

CMD ["ash"]

WORKDIR /backups

