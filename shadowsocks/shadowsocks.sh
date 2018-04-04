#!/bin/bash
# date 2018-04-04 22:44:10
# author calllivecn <c-all@qq.com>

using(){

echo "docker run [...] \\
-e [CONFIG] \\
\
-e SERVER_ADDR [...] \\
\
-e [SERVER_PORT] defautl: 8388 \\
\
-e [LOCAL_ADDR] default: 127.0.0.1 \\
\
-e [LOCAL_PORT] default: 1080 \\
\
-e PASSWORD \\
\
-e [METHOD] default: aes-256-cfb \\
\
-e [TEIMOUT] defautl: 300 \\
\
-e [FAST_OPEN] \\
\
-e POLIPO"

}

if [ "$1"x = "-h"x ] || [ "$1"x = "--help"x ];then
	using
	exit 0
fi

config_flag=0

check_config(){

if [ -n "$CONFIG" ] && [ -f "$CONFIG" ];then
	config_flag=1
	return 
fi

if [ "$SERVER_ADDR" ];then
	SERVER_ADDR='-s '"$SERVER_ADDR"
else
	using
fi

if [ "$SERVER_PORT" ];then
	SERVER_PORT='-p '"$SERVER_PORT"
else
	SERVER_PORT='-p 8388'
fi

if [ "$LOCAL_ADDR" ];then
	LOCAL_ADDR='-b '"$SERVER_ADDR"
else
	LOCAL_ADDR='-b 127.0.0.1'
fi

if [ "$LOCAL_PORT" ];then
	LOCAL_PORT='-l '"$LOCAL_PORT"
else
	LOCAL_PORT='-l 1080'
fi

if [ "$PASSWORD" ];then
	PASSWORD='-k '"$PASSWORD"
else
	using
fi

if [ "$METHOD" ];then
	METHOD='-m '"$METHOD"
else
	METHOD='-m aes-256-cfb'
fi

if [ "$FAST_OPEN" ];then
	FAST_OPEN='--fast-open'
fi

}

check_config

#### 

safe_exit(){
echo "safe exit... pid "$*""
kill "$@"
echo "done safe exit."
}


######

if [ "$config_flag"x = "1"x ];then
	echo 'sslocal -q -c' "$CONFIG"
	sslocal -q -c "$CONFIG" &
	pid=$!
else
	echo 'sslocal -q' "$SERVER_ADDR" "$SERVER_PORT" \
	"$LOCAL_ADDR" "$LOCAL_PORT" "$PASSWORD" \
	"$METHOD" "$TIMEOUT" "$FAST_OPEN"

	sslocal -q "$SERVER_ADDR" "$SERVER_PORT" \
	"$LOCAL_ADDR" "$LOCAL_PORT" "$PASSWORD" \
	"$METHOD" "$TIMEOUT" "$FAST_OPEN" &
	pid=$!
fi

if [ -f "$POLIPO" ];then
	polipo -c "$POLIPO" & 
	pid2=$!
fi

trap "safe_exit $pid $pid2" SIGTERM SIGINT

wait $pid $pid2
