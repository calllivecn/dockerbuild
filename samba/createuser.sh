#!/bin/sh

if [ -z "$1" ];then
	echo "username is required."
	exit 1
else
	USERNAME="$1"
fi

if [ ! -d "/homes/${USERNAME}" ];then
	mkdir -vp "/homes/${USESRNAME}"
fi

if [ -z "$2" ];then
	adduser -DH -G root "$USERNAME"
	#addgroup "$USERNAME" root
	pdbedit -t -a -u "$USERNAME"
else
	adduser -DH -G root "$USERNAME"
	#addgroup "$USERNAME" root
	echo -e "${2}\n${2}" |pdbedit -t -a -u "$USERNAME"
fi

