#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

DEPENDS=py3
. ../libbuild-depends.sh

docker build -t ${IMAGE_NAME} .
