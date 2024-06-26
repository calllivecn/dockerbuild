#!/bin/bash
# date 2020-07-02 18:42:38
# author calllivecn <calllivecn@outlook.com>


DEVICE_ARGS=()
for dev in /dev/video* /dev/snd;
do
	DEVICE_ARGS+=("--device" "$dev")
done


podman run \
	-id --name winehqgui \
	-v /tmp/.X11-unix:/tmp/.X11-unix \
	-e DISPLAY=$DISPLAY \
	-e XMODIFIERS=$XMODIFIERS \
	-e GTK_IM_MODULE=$GTK_IM_MODULE \
	-e QT_IM_MODULE=$QT_IM_MODULE \
	-e AUDIO_GID="$(getent group audio |cut -d: -f3)" \
	-e VIDEO_GID="$(getent group video |cut -d: -f3)" \
	-e UID="$(id -u)" \
	-e GID="$(id -g)" \
	--ipc=host \
	--privileged \
	$DEVICE_ARGS \
	--entrypoint bash \
	winehq
