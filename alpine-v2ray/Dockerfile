FROM alpine-py3:latest

 
# 这个时间（2022-02-15）github 不知道怎么了，wget -O 不能下载 ， 先临时 COPY 下。
#COPY run-build.sh entrypoint.py v2ray-linux-64.zip /
# 直接 -v <v2ray_dir>:/v2ray 挂载方式

COPY app/ /app

RUN pip install --break-system-packages httpx[http2]

ENTRYPOINT ["/app/entrypoint.py"]

#WORKDIR /v2ray

