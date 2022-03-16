#!/bin/bash
# date 2022-03-15 16:59:33
# author calllivecn <c-all@qq.com>


CWD=$(pwd -P)
TMP=$(mktemp -d -p "$CWD")

DEPEND_CACHE="${CWD}/depend-cache"

if [ -d "$DEPEND_CACHE" ];then
	echo "使用depend-cache ~"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP")
else
	mkdir -v "${DEPEND_CACHE}"
	#pip3 install --no-compile --target "$DEPEND_CACHE" "${NAME}"
	pip3 install --target "$DEPEND_CACHE" -r ddns-ali-requiments.txt
	(cd "$DEPEND_CACHE";cp -rv . "$TMP")
fi

clean(){
	echo "clean... ${TMP}"
	rm -rf "${TMP}"
	echo "done"
}

trap clean SIGINT SIGTERM EXIT ERR

cp -rv ddns.py "$TMP/"
shiv --site-packages "$TMP" --compressed -p '/usr/bin/python3 -sE' -o "ddns.pyz" -e ddns:main

#cp -rv ddns.py "$TMP/bin/"
#shiv --site-packages "$TMP" --compressed -p '/usr/bin/python3 -sE' -o "ddns.pyz" -c ddns.py
