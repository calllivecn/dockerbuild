# openVPN server


### build
```bash
bash build.sh
```

### 使用方法：

- 启动: 
	```shell
	docker run -d \
	--name=openvpn \
	--cap-add=NET_ADMIN \
	-p 1194:1194/tcp \
	-p 1194:1194/udp \
	--restart unless-stopped \
	openvpn
	```

- 添加用户: /ovpn-files/ovpn-useradd.sh <username>

- 删除用户：/ovpn-files/ovpn-userdel.sh <username>
