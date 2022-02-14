# 给 搬瓦工服务 v2ray 添加自动更新订阅

- v2ray: https://github.com/v2ray/v2ray-core/releases

- 启动方式：

```shell
podman run -d --name v2ray -p 9999:9999 -p 10000:10000 -e SUB_URL=<$your subscription address>
ro
podman run -d --name v2ray -p <http_proxy port>:9999 -p <socks5_proxy port>:10000 -e SUB_URL=<$your subscription address> -e SUB_INTERVAL=<unit hour>
```
