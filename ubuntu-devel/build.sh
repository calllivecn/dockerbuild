#!/bin/bash
# date 2018-08-28 15:27:02
# author calllivecn <c-all@qq.com>

<<<<<<< HEAD
DEPENDS=ubuntu-base
. ../libbuild-depends.sh

docker build -t ${IMAGE_NAME} .
=======
DEPENDS=ubuntu
. ../libbuild-depends.sh

if [ -n $NO_CACHE ];then
	docker build --no-cache -t ${IMAGE_NAME} .
else
	docker build -t ${IMAGE_NAME} .
fi
>>>>>>> e8642dcbd7db77615db9e50d578d97aa0388e2c4
