#!/usr/bin/bash

#ip -4 route get 1.1.1.1 | grep -oP "(?<=1.1.1.1 via )(\d{1,3}\.){1,3}\d{1,3} (?=dev )"

iface=$(ip -4 route get 1.1.1.1 |awk '{print $5}')

if [ "$iface"x = "lo"x ];then
	exit 1
fi

ip -4 addr show "$iface" |awk '$0~/inet / {print $2}'|awk -F'/' '{print $1}'

