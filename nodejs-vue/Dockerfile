FROM alpine-core:latest


RUN addgroup -g 1000 vue \
&& adduser -D -s /bin/bash -u 1000 -G vue vue \
&& apk --no-cache add nodejs npm bash \
&& npm config set registry https://registry.npm.taobao.org \
&& npm install -g webpack vue-cli

#&& npm install -g cnpm --registry=https://registry.npm.taobao.org

WORKDIR /vue

