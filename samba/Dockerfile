FROM alpine-core:latest

RUN apk --no-cache add samba

COPY smb.conf /etc/samba/
COPY entrypoint.sh createuser.sh /

CMD ["/entrypoint.sh"]

EXPOSE 1445

#VOLUME /ro /rw

