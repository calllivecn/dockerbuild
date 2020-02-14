FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add linux-headers build-base python3-dev \
&& pip3 install uwsgi \
&& apk del -r linux-headers build-base python3-dev

CMD ["ash"]

