#!/data/data/com.termux/files/usr/bin/bash
###!/usr/bin/bash

# ~~如果开启了wifi热点，优先使用热点的ipv6~~
# 不行，这样公网连接不了
#if [ -L /sys/class/net/wlan1 ];then
#
#       ip -6 addr show wlan1 |awk '$0~/global/{print $2}' |awk -F'/' '{print $1}'
#
#else
#
#       iface=$(ip -6 route get 2400:3200:baba::1 |awk '{print $5}')
#
#       ip -6 addr show "$iface" |awk '$0~/mngtmpaddr/{print $2}' |awk -F'/' '{print $1}'
#
#fi


iface=$(ip -6 route get 2400:3200:baba::1 |awk '{print $5}')

if [ "$iface"x = "lo"x ];then
        exit 1
fi

ip -6 addr show "$iface" |awk '$0~/mngtmpaddr/ && $0 !~/deprecated/{print $2}' |awk -F'/' '{print $1}'

