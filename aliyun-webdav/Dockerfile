FROM alpine-py3:latest


RUN apk add davfs2

COPY entrypoint.sh /

CMD ["/entrypoint.sh"]

EXPOSE 8080

VOLUME /webdav
