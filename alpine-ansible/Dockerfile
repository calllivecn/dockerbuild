FROM alpine-py3:latest


RUN apk add openssh-client \
&& pip install ansible==9.13 || pip install --break-system-packages ansible==9.13 \
&& rm -fr $HOME/.cache/

CMD ["ash"]

WORKDIR /

