FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

COPY entrypoint.sh /

RUN apk --no-cache add borgbackup openssh \
&& rm -fr $HOME/.cache/


#ENV SSH_PUBKEY=

#CMD ["/usr/sbin/sshd", "-D"]
CMD ["ash", "/entrypoint.sh"]

EXPOSE 22

WORKDIR /borgbackups
#VOLUME backups

