# 给搬瓦工服务 v2ray 添加自动更新订阅

- v2ray: https://github.com/v2fly/v2ray-core/releases

- 启动方式：

```shell
# 可选参数 V2RAY_PATH: v2ray 目录
podman run -d --name v2ray -p 9999:9999 -p 10000:10000 -v <v2ray_dir>:/v2ray -e SERVER_URL=https://v2ray.server.com/
ro
podman run -d --name v2ray -p <http_proxy port>:9999 -p <socks5_proxy port>:10000 -v <v2ray_dir>:/v2ray -e SERVER_URL=<$your subscription address>
```

- SERVER_URL: 地址
- API_COUNTER: 流量使用情况
- UPDATE_INTERVAL: 更新间隔时间（小时, default: 3）
- V2RAY_PATH: 解压的v2ray.zip 目录。（default: /v2ray/）
- ~~SKIP_CA: 不检查ca~~
