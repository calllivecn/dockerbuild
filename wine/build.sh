#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>


. ../libbuild-depends.sh

set +e

cat >dockerfile<<EOF
FROM ${DEPENDS}:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

ARG APT_PROXY=$APT_PROXY

COPY containers/ /tmp/

RUN bash /tmp/RUN.sh \
&& rm -rf /tmp/* || true

WORKDIR /home/wine

CMD ["/entrypoint.sh"]

EOF


if [ -n $NO_CACHE ];then
	podman build --no-cache -t ${IMAGE_NAME} .
else
	podman build -t ${IMAGE_NAME} .
fi

[ -f dockerfile ] && rm dockerfile
