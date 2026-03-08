FROM alpine-core:latest


# 设置容器里的时区
RUN apk --no-cache add nginx nginx-mod-http-zstd nginx-mod-http-brotli 

#CMD ["ash"]

CMD ["nginx", "-g", "daemon off;"]

