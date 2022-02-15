FROM alpine-py3:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

COPY run-build.sh entrypoint.py /

RUN sh /run-build.sh

#CMD [""]

ENTRYPOINT ["/entrypoint.py"]

#WORKDIR /work

