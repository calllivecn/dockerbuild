FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk add davfs2

COPY entrypoint.sh /

CMD ["/entrypoint.sh"]

EXPOSE 8080

VOLUME /webdav
