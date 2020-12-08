FROM alpine-core:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN addgroup -g 1000 vue \
&& adduser -D -s /bin/bash -u 1000 -G vue vue \
&& apk --no-cache add nodejs npm bash \
&& npm install -g cnpm --registry=https://registry.npm.taobao.org \
&& cnpm install -g webpack vue-cli

WORKDIR /vue

