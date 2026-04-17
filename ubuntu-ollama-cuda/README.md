# ubuntu-ollama-cuda:latest 

- 安装 ollama
- 提供使用宿主机nvidia-GPU 的方法

```bash
bash build.sh
```

- 使用宿主机nvidia GPU在需要在启动时，添加上相关的设备+库。查看ollama+webui.yml

## 注意事项，在构建时可能需要代理


- podman run -it --rm ubuntu-ollama-cuda:latest ollama -v
- 查看版本号后，加上版本号tag


## 优化参数

```shell
# 1. 开启 Flash Attention (CUDA 核心优化)
# 极大降低长文本时的显存峰值，且几乎不损耗精度
export OLLAMA_FLASH_ATTENTION=1

# 2. 开启 KV 缓存量化 (最强优化)
# 默认是 f16。设置为 q8_0 或 q4_0 可以将 KV 缓存占用的空间压缩 50% 或 75%
# 这能让你在 12GB 显存上强行跑通更长的对话历史
export OLLAMA_KV_CACHE_TYPE=q4_0

# 3. 限制默认上下文窗口 (防止显存溢出)
# 3080 Ti 建议初始设为 8192，如果显存依然告急，降至 4096
export OLLAMA_CONTEXT_LENGTH=8192
```

- 其他参数

```
LLAMA_NUM_PARALLEL: 控制单个模型同时处理的请求数。默认通常是 1。

建议值: 4 到 8。对于 20 人团队，建议设为 4 或 6。

注意: 增加此值会成倍消耗显存（VRAM），因为每个并发请求都需要独立的 KV Cache 空间。

OLLAMA_MAX_LOADED_MODELS: 控制显存中同时常驻的模型数量。

建议值: 如果你们只用一个模型（如 Llama 3），设为 1；如果需要同时用多个不同模型，设为 2-3。

OLLAMA_MAX_QUEUE: 队列最大长度。

建议值: 512（默认通常足够），防止瞬间并发过高导致 503 错误。
```
