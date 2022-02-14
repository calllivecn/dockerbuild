FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN tag_name=$(curl https://api.github.com/repos/v2ray/v2ray-core/releases/latest | jsonfmt.py -d tag_name) \
&& curl -fL -O https://github.com/v2fly/v2ray-core/releases/download/$latest_tag/v2ray-linux-64.zip \
&& unzip -d /v2ray/ v2ray-linux-64.zip \

CMD ["ash"]

#WORKDIR /work

