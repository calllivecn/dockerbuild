FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"
 
# 这个时间（2022-02-15）github 不知道怎么了，wget -O 不能下载 ， 先临时 COPY 下。
#COPY run-build.sh entrypoint.py v2ray-linux-64.zip /
# 直接 -v <v2ray_dir>:/v2ray 挂载方式

#COPY run-build.sh entrypoint.py /
COPY entrypoint.py /

#RUN sh /run-build.sh

#CMD [""]

ENTRYPOINT ["/entrypoint.py"]

#WORKDIR /work

