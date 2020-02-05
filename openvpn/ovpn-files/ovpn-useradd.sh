#!/bin/bash
# date 2020-02-04 19:59:23
# author calllivecn <c-all@qq.com>

set -e

. /ovpn-files/libovpn-dirs.sh

# 这里使用的是 EASY_RSA_VERSION=3
PKI_DIR=$EASYRSA/pki

for user in "$@"
do
	if [ -d "$USERS/$user" ]; then
		rm -rf $USERS/$user
		rm -rf  $PKI_DIR/reqs/$user.req
		sed -i '/'"$user"'/d' $PKI_DIR/index.txt
	fi

	cd $EASYRSA/

	# 生成客户端 ssl 证书文件
	./easyrsa build-client-full "$user" nopass
	
	# 整理下生成的文件
	mkdir -p  $USERS/$user
	cp $PKI_DIR/ca.crt $USERS/$user/   # CA 根证书
	cp $PKI_DIR/issued/$user.crt $USERS/$user/   # 客户端证书
	cp $PKI_DIR/private/$user.key $USERS/$user/  # 客户端证书密钥
	#cp /etc/openvpn/client/sample.ovpn $USERS/$user/$user.ovpn # 客户端配置文件
	cp /ovpn-files/client.ovpn $USERS/$user/$user.ovpn # 客户端配置文件
	sed -i 's/clientuser/'"$user"'/g' $USERS/$user/$user.ovpn
	cp $SERVER/ta.key $USERS/$user/ta.key  # tls-auth 文件
	cd $USERS
	zip -r $user.zip $user

	echo -e "\033[34muser config file: $EASYRSA/$user.zip \033[0m"
done
