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
