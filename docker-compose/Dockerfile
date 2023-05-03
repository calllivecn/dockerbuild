FROM alpine-py3:latest

LABEL author="calllivecn <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

RUN apk --no-cache add linux-headers build-base python3-dev \
&& pip3 --no-cache-dir install docker-compose \
&& apk del -r linux-headers build-base python3-dev

ENTRYPOINT ["docker-compose"]

CMD ["--version"]

