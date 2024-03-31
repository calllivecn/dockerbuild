#!/bin/bash
# date 2023-04-19 02:51:16
# author calllivecn <calllivecn@outlook.com>


echo "生成nginx htpasswd用户名和密码"


input(){
	local user prompt="$1"
	while :;
	do
		read -p "$prompt" user
		if [ "$user"x = x ];then
			echo "用户名不能为空。"
		else
			break
		fi
	done
	echo "$user"
}

getpass(){
	stty -icanon -echo
	input "$1"
	stty icanon echo
}


username=$(input "用户名：")

password=$(getpass "密码：")
echo

echo -n "$username:"
openssl passwd "$pw"
