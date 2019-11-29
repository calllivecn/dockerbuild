FROM alpine-base:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk update \
&& apk add openjdk8-jre-base \
&& rm -r /var/cache/apk/* || true

