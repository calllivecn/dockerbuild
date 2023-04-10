FROM alpine-core:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

# 设置容器里的时区
RUN apk --no-cache add nginx

#CMD ["ash"]

CMD ["nginx", "-g", "daemon off;"]

