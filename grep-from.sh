#!/bin/bash
# date 2020-11-26 22:35:47
# author calllivecn <calllivecn@outlook.com>


if [ -z "$1" ];then
	echo "需要指定一个docker build context "
	exit 1
fi

build_dir="$1"

DF="$build_dir/dockerfile"

if [ ! -f $DF ];then
	echo "这里没有 dockerfile"
	exit 1
fi

get_depend(){
	grep -m 1 -oE '^FROM (.*):' "$DF" |cut -d' ' -f2 |cut -d':' -f1
}

get_depend
