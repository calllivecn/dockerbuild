#!/bin/bash
# date 2020-02-04 20:45:53
# author calllivecn <calllivecn@outlook.com>

set -e

if [ -f /ovpn-files/ovpn-init-done ];then
	echo "already init exit."
	exit 0
fi

. /ovpn-files/libovpn-dirs.sh

cp -va /usr/share/easy-rsa/ /etc/openvpn/

cd "$EASYRSA"

cp -v vars.example vars

./easyrsa init-pki

echo -e "certificate\n" |./easyrsa build-ca nopass

./easyrsa build-server-full server nopass

./easyrsa gen-dh 

openvpn --genkey --secret $SERVER/ta.key

cp -v $EASYRSA/pki/ca.crt $SERVER/
cp -v $EASYRSA/pki/dh.pem $SERVER/
cp -v $EASYRSA/pki/issued/server.crt $SERVER/
cp -v $EASYRSA/pki/private/server.key $SERVER/

cp -v $OVPNFILES/server.ovpn $SERVER/

if [ ! -d /dev/net ];then
	mkdir /dev/net
fi

if [ ! -c /dev/net/run ];then
	if [ -e /dev/net/run ];then
		rm -rf /dev/net/run
	fi
	mknod /dev/net/tun c 10 200
fi

touch /ovpn-files/ovpn-init-done
