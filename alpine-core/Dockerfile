FROM alpine:latest


#ARG apk="apk --no-cache"
#ARG ALPINE_VERSION="3.8"
#ARG MIRROR="mirrors.ustc.edu.cn"
#ARG MIRROR="https://mirrors.aliyun.com/alpine/v3.10/"
#ARG MIRROR="mirrors.aliyun.com"

# 不用 aliyun 源了
#RUN sed -i "s/dl-cdn.alpinelinux.org/${MIRROR}/g" /etc/apk/repositories

# 设置容器里的时区
RUN sed -i "s/dl-cdn.alpinelinux.org/mirrors.tuna.tsinghua.edu.cn/g" /etc/apk/repositories \
&& apk --no-cache add tzdata \
&& ln -svf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

CMD ["ash"]

