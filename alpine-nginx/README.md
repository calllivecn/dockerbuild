### from alpine:latest build nginx


```shell
podman run -d --name nginx --network host -v <etc-nginx>:/etc/nginx -v <log-nginx>:/var/log/nginx localhost/alpiine-nginx:latest
```
