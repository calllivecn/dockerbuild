#!/bin/bash
# date 2022-03-15 16:13:34
# author calllivecn <c-all@qq.com>

CWD=$(pwd -P)
TMP=$(mktemp -d -p "$CWD")

clean(){
	echo "clean... ${TMP}"
	rm -rf "${TMP}"
	echo "done"
}

trap clean SIGINT SIGTERM EXIT ERR

cp -v ddns.py "$TMP"

shiv --site-packages "$TMP" --compressed -p '/usr/bin/python3 -sE' -o "${NAME}.pyz" -e ddns:main
