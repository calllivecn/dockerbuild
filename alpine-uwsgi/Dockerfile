FROM alpine-py3:latest


RUN apk --no-cache add linux-headers build-base python3-dev \
&& pip3 install uwsgi \
&& apk del -r linux-headers build-base python3-dev

CMD ["ash"]

