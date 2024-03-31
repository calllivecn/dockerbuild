#!/bin/bash
# date 2022-03-16 09:28:00
# author calllivecn <calllivecn@outlook.com>

# 配合 ddns.pyz 使用的轻客户端

set -e

# 0-9a-zA-Z 的 16 字节的串(一定要是16字节)
SECRET_ID=ncGDgE6gRpNTmLqo
# 需要配置的secret, 可以直接使用 uuidgen 命令生成
SECRET=d21ae05b-97fe-4b05-8bb9-c10033706180

# interval 时间间隔; 需要大于ACK超时时间.
INTERVAL=300

# 例如: ddns.example.com OR 1.2.1.2
DDNS_SERVER=ddns.example.com
DDNS_PORT=2022

# log 写入文件, 0: 不写入文件， 1: 写入文件和标准输出， 2：只写入文件
LOG_FILE=1

logs(){

    if [ "$LOG_FILE"x = 0x ];then
        echo "$*"
    elif [ "$LOG_FILE"x = 1x ];then
        echo "$*"
        echo "$*" >> dns-client.logs
    elif [ "$LOG_FILE"x = 2x ];then
        echo "$*" >> dns-client.logs
    else
        echo "\$LOG_FILE配置错误"
        exit 1
    fi
}


# bash UDP 只能 发送数据，不能接收。。。
send(){
    local response
    echo -n "$1" >/dev/udp/$DDNS_SERVER/$DDNS_PORT
}

generate_signature(){
    timestamp=$(date +%s)
    sign=$(echo "${SERCRET}${timestamp}${SECRET_ID}" | sha256sum)
    sign=${sign::64}
    logs "签名串: $sign"
    echo ${sign}
}


while :
do
    logs "请求ddns server"
    sign=$(generate_signature)
    send "${SECRET_ID}${sig}"
    logs "sleep $INTERVAL"
    sleep $INTERVAL
done
