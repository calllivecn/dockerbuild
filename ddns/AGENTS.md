# 代理开发指引

## 项目边界

- 这是面向 Linux 的 Python IPv6 DDNS 服务，服务端入口是 `src/ddns.py`，客户端入口是 `src/ddnsclient.py`；DNS 操作固定依赖阿里云 Alidns。
- `src/utils.py` 用启动文件名推导配置路径：运行 `ddns`/`ddns.pyz` 时读取同目录的 `ddns.toml`，运行 `ddnsclient`/`ddnsclient.pyz` 时读取同目录的 `ddnsclient.toml`；不要仅按当前 shell 目录判断配置位置。
- 服务端监听 IPv6 UDP（默认 `::`、端口 `2022`），客户端通过 UDP 发送带签名的地址更新请求；修改协议字段时需同时检查 `src/utils.py` 的打包、校验和 ACK 逻辑。
- 服务端还可启动 Quart HTTP API（默认 `8080`）；`Https.Enabled=true` 时改为由 Quart 直接提供 TLS，UDP 始终保留，通常由前置 Nginx/Caddy 负责 HTTPS。
- `getipcmd/` 中的脚本是客户端可配置的取 IP 后端；`Cmd` 的第一个词必须是该目录中的文件名，后续词作为参数传递，不经过 shell，命令超时为 15 秒，IP 必须输出到 stdout。

## 依赖与构建

- 运行依赖锁定在 `requirements.txt`，打包依赖为 `requirements-build.txt`；当前使用 Python 3.11+ 时由内置 `tomllib` 读取 TOML，旧版本才需要 `tomli`。
- PyInstaller 的正式打包入口是根目录的 `build.spec`：在安装两份 requirements 后运行 `pyinstaller build.spec`，产物为 `dist/ddns/`，其中包含 `ddns` 和 `ddnsclient` 两个可执行文件。
- 最小打包流程：`python -m venv /tmp/ddns && . /tmp/ddns/bin/activate && pip install -r requirements.txt && pip install -r requirements-build.txt && pyinstaller build.spec`。
- Docker 构建命令是 `docker build -t ddns .`（也可使用 Podman）；当前 Dockerfile 执行 `COPY src/ /` 却以 `/src/ddns.py` 启动，修改容器相关代码时先核对这个路径不一致，不能假定镜像能直接启动。

## 运行与验证

- systemd 单元假定程序、对应 TOML 配置和工作目录都位于 `%h/.ddns/`，入口参数为 `--not-logtime`；服务端和客户端分别使用 `ddns.service`、`ddnsclient.service`。
- 仓库没有 lint、格式化或类型检查配置；协议单元测试位于 `tests/`，运行 `python -m unittest discover -s tests`。提交前还应运行 `python -m compileall src`，并在依赖和配置齐全时用 `python src/ddns.py --parse` 或 `python src/ddnsclient.py --parse` 做入口冒烟检查。
- `--parse` 是隐藏的参数，仅用于参数解析冒烟，不会替代真实网络、阿里云 API 或 IPv6 路由验证；默认启动会持续运行并读取配置。
- 修改 `src/` 后应同步考虑 PyInstaller 的模块收集和 Docker 的文件布局；不要把 `dist/`、缓存目录或运行时 `.cache` 文件当作源码编辑。

## 配置与安全

- `src/ddns.toml`、`src/ddnsclient.toml` 是运行配置位置，包含 AccessKey 和共享密钥字段；这些文件不得再写入或传播真实凭据，新增示例应使用占位符，模板参考 `ddns.toml.md` 和 `ddnsclient.toml.md`。
- 服务端 `Clients[].ClientID` 必须与客户端 `ClientId` 对应；客户端同时需要客户端 `Secret` 和服务端 `ServerSecret`，服务端更新 DNS 的记录由 `multidns` 定义。
