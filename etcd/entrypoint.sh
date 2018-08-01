#!/bin/ash
# date 2018-07-30 16:00:10
# author calllivecn <c-all@qq.com>

set -e

uuid(){
if [ -z $UUID ];then
	echo "required new cluster UUID environment."
	exit 2
fi
}

if [ -z $NODE_NAME ];then
	NODE_NAME=etcd_$(hostname)
fi

## etcd discovery begin

set_cluster_nodes(){
while :
do
	if etcdctl set /discovery/${UUID}/_config/size ${CLUSTER_SIZE} 2>/dev/null > /dev/null;then
		break
	else
		sleep 1
	fi
done
echo "etcdctl set /discovery/${UUID}/_config/size ${CLUSTER_SIZE} ... done"
}

discovery(){

	uuid

	CLUSTER_SIZE=${CLUSTER_SIZE:-3}

	if echo ${CLUSTER_SIZE} |grep -qE '[3579]';then
		:
	else
		echo 'etcd cluster size 3|5|7|9.'
		exit 2
	fi

	set_cluster_nodes &
	
	exec etcd --name ${NODE_NAME} \
	 --listen-client-urls http://0.0.0.0:2379 \
	 --listen-peer-urls http://0.0.0.0:2380 \
	 --advertise-client-urls http://0.0.0.0:2379
}

## etcd discovery end

if [ "$1"x = "--discovery"x ];then
	discovery
fi

## etcd cluster begin

case "$1" in
	--help|-h)
		echo 'Using: docker run -it -e UUID=$UUID \
		-e CURRENT_URL=${http://iface ip:2379} \
		-e DISCOVEERY_URL=$DISCOVERY_URL \
		[-v ${volume}:/etcd_dir ] \
		-p 2380:2380 -p 2379:2379 ${image_name}'
		echo
		echo 'UUID is etcd cluster'
		echo 'CURRENT_URL is docker host ip+etcd_port'
		echo 'DISCOVERY_URL is etcd discovery url'
		exit 0
		;;
	--entrypoint.sh)
		echo "ENV variable...BEGIN"
		env
		echo "ENV variable...END"
		echo "$0...BEGIN"
		cat $0
		echo "$0...END"
		exit 0
		;;
esac

uuid

if [ -z $DISCOVERY_URL ];then
	echo "required DISCOVERY_URL environment."
	exit 2
fi

if [ -z $CURRENT_URL ];then
	echo "required CURRENT_URL environment."
	exit 2
fi


exec etcd --name ${NODE_NAME} \
--initial-advertise-peer-urls ${CURRENT_URL%:*}:2380 \
--listen-peer-urls http://0.0.0.0:2380 \
--listen-client-urls http://0.0.0.0:2379 \
--advertise-client-urls ${CURRENT_URL} \
--discovery ${DISCOVERY_URL}/v2/keys/discovery/${UUID}

## etcd cluster end
