# AGENTS.md

## 项目概述

基于 Podman 的 v2ray 容器，通过订阅地址自动获取代理节点列表，测速后选择最优节点写入 v2ray 配置，并持续监控连通性。

## 构建

```shell
podman build -t <name> .
```

项目使用 **podman**，不是 docker。`build.sh` 会通过 `../libbuild-depends.sh`（仓库外的脚本）设置 `IMAGE_NAME`。

基础镜像是自定义的 `alpine-py3:latest`，不是公开镜像。

## 运行时

入口脚本为 `app/entrypoint.py`，容器通过 shebang 直接调用 `python3` 执行。

### 必需环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `SERVER_URL` | 订阅地址 | **必填** |
| `UPDATE_INTERVAL` | 更新间隔（小时） | `3` |
| `V2RAY_PATH` | v2ray 二进制及配置目录 | `/v2ray` |
| `CHECK_URL` | 连通性检测地址 | `https://www.google.com` |
| `LOGS_PATH` | 日志输出目录 | `/logs` |
| `API_COUNTER` | 流量查询 API | 无 |

### 端口

| 端口 | 协议 |
|---|---|
| 9999 | HTTP 代理 |
| 10000 | SOCKS5 代理 |
| 10001 | HTTP 代理（x10 路由） |
| 10002 | SOCKS5 代理（x10 路由） |

## 架构

- `app/config.json` — v2ray 配置**模板**。`outbounds` 段会在运行时被 `entrypoint.py` 用测速后选出的节点覆盖，不要依赖此文件中的 outbounds 内容。
- `app/entrypoint.py` — 主循环：获取订阅 → 解码 vmess:// / vless:// 链接 → TCP 连接测速 → 写入配置 → 启动/管理 v2ray 进程 → 定期连通性检测。
- `run-build.sh` — 在 Dockerfile 构建阶段下载并解压 v2ray 二进制，执行后自删除。
- v2ray 二进制路径：`/v2ray/xray`，同时创建了 `v2ray -> xray` 符号链接兼容旧路径。

## 关键实现细节

- `.json` 和 `.json-bak` 文件已被 gitignore，运行时的 v2ray 配置是生成文件，不纳入版本管理。
- 订阅解码支持 `vmess://` 和 `vless://` 协议，`ss://` 明确跳过。
- VLESS 节点 `security=reality` 可正常使用，因为已切换到 Xray-core（v2fly/v2ray-core 不支持 Reality，仅限 TLS）。
- `ps` 字段包含 `s801.` 的节点会被标记为 `vmess-out-x10`（对应 10001/10002 端口的路由标签）。
- `httpx[http2]` 是硬依赖，未安装时脚本以退出码 1 直接退出。
- 假定 v2ray v5.x 版本，使用 `v2ray run` 子命令，不是 v4 的 `-config` 方式。

## 测试

无测试套件，仅靠人工验证。
