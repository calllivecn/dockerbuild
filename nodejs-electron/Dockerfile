FROM ubuntu-devel:latest


RUN apt update && \
&& apt install -y nodejs npm \
&& npm install -g cnpm --registry=https://registry.npm.taobao.org \
&& cnpm install -g electron-forge electron-packager electron-builder electron-asar
