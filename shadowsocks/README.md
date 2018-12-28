# shadowsocket to docker

build:
```
bash build.sh
```

run:
```
docker run -d --name ss -p 1080:1080 -p 1080:1080/udp -p 8123:8123 -v ${server.json}:${map.json}
```

随便记录下先。