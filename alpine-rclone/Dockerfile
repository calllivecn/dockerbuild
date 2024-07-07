FROM alpine-py3:latest


RUN apk --no-cache add rclone fuse3 fuse rsync \
&& rm -fr $HOME/.cache/

CMD ["ash"]

#WORKDIR /work

