FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add rclone fuse3 fuse \
&& rm -fr $HOME/.cache/

CMD ["ash"]

#WORKDIR /work

