FROM alpine-py3:latest

RUN pip install ansible || pip install --break-system-packages ansible \
&& apk add git \
&& rm -fr $HOME/.cache/

WORKDIR /data

CMD ["ash"]

