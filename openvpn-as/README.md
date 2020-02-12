# ubuntu:latest
- openvpn
- [openvpn-as docs](https://hub.docker.com/r/linuxserver/openvpn-as)

```shell
docker pull linuxserver/openvpn-as

docker create \
  --name=openvpn-as \
  --cap-add=NET_ADMIN \
  -e PUID=1000 \
  -e PGID=1000 \
  -e TZ=Europe/London \
  -e INTERFACE=eth0 `#optional` \
  -p 943:943 \
  -p 9443:9443 \
  -p 1194:1194/udp \
  -v path to data:/config \
  --restart unless-stopped \
  linuxserver/openvpn-as
```
