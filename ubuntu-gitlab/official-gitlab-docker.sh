#!/bin/bash
# date 2019-11-15 10:27:32
# author calllivecn <c-all@qq.com>

docker run --detach \
	--hostname gitlab.example.com \
	--publish 8443:443 --publish 8880:80 --publish 8822:22 \
	--name gitlab \
	--restart always \
	--volume /home/zx/gitlab-store/config:/etc/gitlab \
	--volume /home/zx/gitlab-store/logs:/var/log/gitlab \
	--volume /home/zx/gitlab-store/ata:/var/opt/gitlab \
	gitlab/gitlab-ce:latest
