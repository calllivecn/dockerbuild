#!/bin/bash
# date 2024-02-25 23:42:59
# author calllivecn <c-all@qq.com>

if ls /etc/ssh/ssh_host_* >/dev/null 2>&1 ;then
	:
else
	ssh-keygen -A
fi


exec /usr/sbin/sshd -D


