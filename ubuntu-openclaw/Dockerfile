FROM ubuntu-devel:latest

#ARG node_version

#RUN wget -O /tmp/node.tar.xz https://nodejs.org/dist/v${node_version}/node-v${node_version}-linux-x64.tar.xz \
#&& tar -xf /tmp/node.tar.xz -C /tmp/ \
#&& rm -v /tmp/node.tar.xz \
#&& cd /tmp/node-v${node_version}-linux-x64/ \
#&& cp -av . /usr/local/ \
#&& npm cache verify

#&& npm config -g set registry https://mirrors.tuna.tsinghua.edu.cn/npm/ \
#&& npm config -g get registry \

RUN apt -y update \
&& apt -y install tmux ffmpeg

ARG https_proxy

RUN curl -fsSL https://openclaw.ai/install.sh | bash -s -- --no-onboard

