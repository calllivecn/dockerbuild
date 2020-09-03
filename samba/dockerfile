FROM alpine-core:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add samba

COPY smb.conf /etc/samba/
COPY entrypoint.sh /

CMD ["/entrypoint.sh"]

EXPOSE 445

VOLUME /ro /rw
