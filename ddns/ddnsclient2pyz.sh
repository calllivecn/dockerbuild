#!/bin/bash
# date 2022-03-15 16:59:33
# author calllivecn <calllivecn@outlook.com>


CWD=$(pwd -P)
TMP1=$(mktemp -d -p "$CWD")

DEPEND_CACHE="${CWD}/depend-cache"

PYROUTE2=$(grep "pyroute2==" requirements.txt)

if [ -d "$DEPEND_CACHE" ];then
	echo "使用depend-cache ~"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP1")
else
	mkdir -v "${DEPEND_CACHE}"
	pip3 install --no-compile --target "$DEPEND_CACHE" "$PYROUTE2"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP1")
fi

clean(){
	echo "clean... ${TMP1} ${TMP2}"
	rm -rf "${TMP1}" "${TMP2}"
	echo "done"
}

trap clean SIGINT SIGTERM EXIT ERR

(cd src/;cp -rv . "${TMP1}/")

rm -rf "${TMP1}/__pycache__/"

python3 -m zipapp --python '/usr/bin/env python3' --main ddnsclient:main --compress --output ddnsclient.pyz "${TMP1}" 

