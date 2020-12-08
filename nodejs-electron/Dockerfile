FROM ubuntu-devel:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apt update && \
&& apt install -y nodejs npm \
&& npm install -g cnpm --registry=https://registry.npm.taobao.org \
&& cnpm install -g electron-forge electron-packager electron-builder electron-asar
