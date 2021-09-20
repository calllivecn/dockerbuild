#!/bin/bash
# date 2021-09-15 15:42:26
# author calllivecn <c-all@qq.com>

# hostname

if [ "$1" == "--help" ];then
	echo "Description: 构建rabbitmq 集群"
	echo "Usage: [--help]"
	echo 'Usage: podman run -d -e RABBITMQ_NODE_PORT=5672 \'
	echo 'RABBITMQ_NODENAME=rabbit01 \'
	echo 'ERLANG_COOKIE=<20长度的大写字母> \ # 可以使用 tr -cd "A-Z" < /dev/urandom |head -c 20 生成'
	echo '-p 5672:5672 -p 15672:15672'
	exit 0
fi

if [ "$ERLANG_COOKIE"x == x ];thne
	echo "需要设置 .erlang.cookie"
	exit 0
fi

# 启用 web 管理页面
cat > /etc/rabbitmq/enabled_plugins <EOF
[rabbitmq_management].
EOF

cat > /var/lib/rabbitmq/.erlang.cookie <EOF
$ERLANG_COOKIE
EOF


