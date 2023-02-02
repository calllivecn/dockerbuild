#!/bin/sh


if [ "$DAV_UID"x = x ];then
	DAV_UID=0
	echo "set DAV_UID=0"
else
	DAV_UID="$1"
fi

ROOT=/webdav
RW=$ROOT/rw

if [ ! -d $RW ];then
	mkdir -vp $RW
fi


##aliyundrive-webdav -r $TOKEN -w $ROOT $RW &

mount -t davfs http://127.0.0.1:8080/ /webdav -o uid=$UID

