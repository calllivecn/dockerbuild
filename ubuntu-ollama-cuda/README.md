# ubuntu-ollama-cuda:latest 

- 安装 ollama
- 提供使用宿主机nvidia-GPU 的方法

```bash
bash build.sh
```

- 使用宿主机nvidia GPU在需要在启动时，添加上相关的设备+库。查看ollama+webui.yml

## 注意事项，在构建时可能需要代理

- podman build -t ollama  ...

