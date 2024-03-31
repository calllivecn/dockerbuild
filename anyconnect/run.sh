#!/bin/bash
# date 2018-10-26 10:18:32
# author calllivecn <calllivecn@outlook.com>

sudo xhost +



docker run -it --rm --cap-add NET_ADMIN -e DISPLAY=$DISPLAY -v /etc/localtime:/etc/localtime:ro -v /tmp/.X11-unix:/tmp/.X11-unix docker-run-gui /opt/cisco/anyconnect/bin/vpnui
