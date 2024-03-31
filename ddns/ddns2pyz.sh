#!/bin/bash
# date 2022-03-15 16:59:33
# author calllivecn <calllivecn@outlook.com>


CWD=$(pwd -P)
TMP1=$(mktemp -d -p "$CWD")
TMP2=$(mktemp -d -p "$CWD")

DEPEND_CACHE="${CWD}/depend-cache"

if [ -d "$DEPEND_CACHE" ];then
	echo "使用depend-cache ~"
	(cd "$DEPEND_CACHE";cp -rv . "$TMP1")
else
	mkdir -v "${DEPEND_CACHE}"
	#pip3 install --no-compile --target "$DEPEND_CACHE" "${NAME}"
	pip3 install --target "$DEPEND_CACHE" -r ddns-ali-requiments.txt
	(cd "$DEPEND_CACHE";cp -rv . "$TMP1")
fi

clean(){
	echo "clean... ${TMP1} ${TMP2}"
	rm -rf "${TMP1}" "${TMP2}"
	echo "done"
}

trap clean SIGINT SIGTERM EXIT ERR

cp -rv ddns.py utils.py aliyunlib.py logs.py libnetlink.py "${TMP1}/"
shiv --site-packages "$TMP1" --compressed -p '/usr/bin/python3 -sE' -o "ddns.pyz" -e ddns:main


cp -v ddnsclient.py utils.py logs.py "${TMP2}/"
python3 -m zipapp --python '/usr/bin/env python3' --main ddnsclient:main --compress --output ddnsclient.pyz "${TMP2}" 

#cp -rv ddns.py "$TMP/bin/"
#shiv --site-packages "$TMP" --compressed -p '/usr/bin/python3 -sE' -o "ddns.pyz" -c ddns.py
