#!/bin/ash
# date 2024-02-25 23:42:59
# author calllivecn <calllivecn@outlook.com>

if ls /etc/ssh/ssh_host_* >/dev/null 2>&1 ;then
	:
else
	ssh-keygen -A
fi


USER_SSH_DIR="/root/.ssh"

if [ -d "$USER_SSH_DIR" ];then
	:
else
	mkdir -vp "$USER_SSH_DIR"
fi


if [ -n "$SSH_PUBKEY" ];then
	echo "设置ssh pubkey --> ${USER_SSH_DIR}/authorized_keys"
	echo "$SSH_PUBKEY" >> "${USER_SSH_DIR}/authorized_keys"
else
	echo "需要设置ssh public key: 在 podman run 时使用 -e SSH_PUBKEY 环境变量设置"
	exit 1
fi

exec /usr/sbin/sshd -D


