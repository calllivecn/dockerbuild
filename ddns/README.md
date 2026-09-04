# DDNS

目前只支持阿里云 DNS 和 IPv6 记录，运行在 Linux 或 Android Termux 环境。

## 使用方式

### 服务端 + 客户端

这是项目目前主要的使用方式：

- 服务端运行 `ddns`，监听 IPv6 UDP `2022` 端口。
- 服务端配置阿里云 AccessKey、服务端密钥，以及每个客户端的 `ClientID`、客户端密钥和 `multidns`。
- 客户端运行 `ddnsclient`，定时获取本机 IPv6 地址并发送给服务端。
- 服务端验证请求后，更新该客户端对应的阿里云 AAAA 记录。
- 一个服务端可以通过不同的 `ClientID` 管理多个客户端。
- 服务端可在保留 UDP 的同时启用 Quart HTTP API，HTTP 为默认方式；也可显式配置 TLS 后使用 HTTPS。需要使用 UDP 时，将客户端协议明确设置为 `udp`。

服务端配置模板见 `ddns.toml.md`，客户端配置模板见 `ddnsclient.toml.md`。服务端的 `Clients[].ClientID` 必须与客户端的 `ClientId` 对应，客户端需要同时配置客户端 `Secret` 和服务端 `ServerSecret`。

systemd 用户服务使用 `ddns.service` 和 `ddnsclient.service`。服务端或客户端程序、对应的 TOML 配置和工作目录都应放在 `%h/.ddns/` 下，服务单元会使用 `--not-logtime` 参数启动程序。

### 客户端获取 IP 的方式

客户端支持两种获取地址的方式：

- 不配置 `Cmd`：使用 `pyroute2` 获取默认 IPv6 路由接口上的 IPv6 地址。
- 配置 `Cmd`：执行 `getipcmd/` 下的脚本获取地址，例如 `Cmd="linux-ipv6.sh"`。

外部命令的第一个词必须是 `getipcmd/` 中的文件名，后续词作为参数传递，不经过 shell；命令超时时间为 15 秒，IPv4 或 IPv6 地址必须输出到标准输出。已有脚本见 `getipcmd/`。

### HTTP/HTTPS 客户端

服务端默认在 `[Http]` 中监听明文 HTTP，适合放在 Nginx 等反向代理之后。若不使用反向代理，也可以在 `[Https]` 中设置 `Enabled=true`，配置监听地址、端口、TLS 证书 `CertFile` 和私钥 `KeyFile`；启用 HTTPS 后，程序使用 HTTPS 监听，UDP 服务仍会同时运行。

Python 客户端在 `ddnsclient.toml` 中设置：

```toml
Protocol="http"
HttpUrl="http://example.com:8080/api/v1/update"
VerifyTLS=true
```

HTTP/HTTPS 请求使用客户端密钥进行 HMAC-SHA256 签名，签名原文严格为 `ClientID\nUnix时间戳\nIP地址`。Bash 客户端依赖 `curl`、`openssl` 和 `awk`，配置模板见 `ddnsclient.conf.md`，启动方式为：

```shell
./ddnsclient.sh /path/to/ddnsclient.conf
```

直接使用 HTTPS 时应使用有效证书并校验证书；通过 Nginx 等反向代理时，建议客户端访问代理提供的 HTTPS 地址，由代理转发到本程序的 HTTP 端口。

`ddns.toml.md` 中的 `[SelfDomainName]` 是历史配置说明，当前服务端没有启用服务端自身 IP 自动更新逻辑，不要将其当作当前支持的使用方式。

## PyInstaller 打包

```shell
python -m venv /tmp/ddns

. /tmp/ddns/bin/activate

pip install -r requirements.txt
pip install -r requirements-build.txt

pyinstaller build.spec

rm -rf /tmp/ddns/

# 产物 dist/ddns/

```

打包结果中包含 `ddns` 和 `ddnsclient` 两个可执行文件。

## 使用容器

- `docker build -t ddns .`（也可使用 Podman）。

容器配置和启动路径应以当前 `Dockerfile` 为准；目前 Dockerfile 将 `src/` 内容复制到 `/`，但启动命令引用 `/src/ddns.py`，构建后启动前需要先核对这一处路径。
