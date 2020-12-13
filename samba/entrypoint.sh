#!/bin/sh

#UID=${UID:-0}
#gid=${GID:-0}

USERNAME="samba"
USERNAME="root"

if [ -z "$PW" ];then
	echo "passwd is required."
	exit 1
fi

LOGLEVEL=${LOGLEVEL:-1}

#adduser -D -u $UID $USERNAME

#smbpasswd -a $USERNAME

echo -e "${PW}\n${PW}" |pdbedit -t -a -u $USERNAME

exec smbd -FS --no-process-group -d ${LOGLEVEL} -s /etc/samba/smb.conf -p 445
