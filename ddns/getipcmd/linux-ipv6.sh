#!/usr/bin/bash

iface=$(ip -6 route get 2400:3200:baba::1 |awk '{print $7}')

ip -6 addr show "$iface" |awk '$0~/mngtmpaddr/{print $2}' |awk -F'/' '{print $1}'


