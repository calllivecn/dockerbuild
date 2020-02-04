#!/bin/bash
# date 2020-01-06 18:40:50
# author calllivecn <c-all@qq.com>

#docker create \
#  --name=openvpn-as \
#  --cap-add=NET_ADMIN \
#  -e PUID=1000 \
#  -e PGID=1000 \
#  -e TZ=Europe/London \
#  -e INTERFACE=eth0 `#optional` \
#  -p 943:943 \
#  -p 9443:9443 \
#  -p 1194:1194/udp \
#  -v path to data:/config \
#  --restart unless-stopped \
#  linuxserver/openvpn-as


docker create \
  --name=openvpn-as \
  --network host \
  --cap-add=NET_ADMIN \
  -e PUID=1000 \
  -e PGID=1000 \
  -e TZ=Asia/Shanghai \
  -p 943:943 \
  -p 9443:9443 \
  -p 1194:1194/udp \
  -v /home/zx/work/openvpn-config:/config \
  --restart on-failure:5 \
  linuxserver/openvpn-as


