#!/bin/bash
# date 2018-10-26 10:18:32
# author calllivecn <c-all@qq.com>

sudo xhost +

sudo docker run -it --rm -e DISPLAY=$DISPLAY -v /etc/localtime:/etc/localtime:ro -v /tmp/.X11-unix:/tmp/.X11-unix docker-run-gui gedit
