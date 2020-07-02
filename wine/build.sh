#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>


DEPENDS=ubuntu
. ../libbuild-depends.sh

set +e

cat >dockerfile<<EOF
FROM ${DEPENDS}:latest

LABEL author="ZhangXu <c-all@qq.com>" repositories="https://github.com/calllivecn/dockerbuild/"

ARGS APT_PROXY

COPY containers/ /tmp/

RUN bash /tmp/containers/RUN.sh \
&& mv /tmp/contaners/entrypoint.sh / \
&& rm -rf /tmp/containers/ 

WORKDIR /home/wine

CMD ["/entrypoint.sh"]

EOF


if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi

[ -f dockerfile ] && rm dockerfile
