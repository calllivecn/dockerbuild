#!/bin/bash
# date 2020-06-07 07:08:55
# author calllivecn <calllivecn@outlook.com>

CWD=$(pwd -P)
TMP=$(mktemp -d -p "$CWD")

DEPEND_CACHE="${CWD}/depend-cache"

NAME="certbot"
EXT=".pyz"

if [ -d "$DEPEND_CACHE" ];then
	echo "使用depend-cache ~"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP")
else
	mkdir -v "${DEPEND_CACHE}"
	#pip3 install --no-compile --target "$DEPEND_CACHE" "${NAME}"
	pip3 install --target "$DEPEND_CACHE" "${NAME}"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP")
fi

clean(){
	echo "clean... ${TMP}"
	rm -rf "${TMP}"
	echo "done"
}

trap clean SIGINT SIGTERM EXIT ERR

cp -rv ali_dns.py tencent_dns.py "$TMP"

shiv --site-packages "$TMP" --compressed -p '/usr/bin/python3 -sE' -o "${NAME}.pyz" -e certbot.main:main
