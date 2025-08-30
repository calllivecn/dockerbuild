好，我给你一个**最小可用**的方案，完全不用 `nvidia-container-toolkit`，只靠 `--device` 和 `-v` 手动挂载驱动库，让 Podman 容器里的 PyTorch 直接用上宿主机 GPU。

---

## 1. 找到宿主机 GPU 设备文件

通常 NVIDIA 会有：

```bash
ls -l /dev/nvidia*
```

可能输出：

```
/dev/nvidia0
/dev/nvidiactl
/dev/nvidia-uvm
/dev/nvidia-uvm-tools
```

---

## 2. 找到 NVIDIA 驱动库路径

驱动库一般在这些目录之一：

```bash
/usr/lib/x86_64-linux-gnu/
/usr/lib64/
/usr/lib/nvidia-<version>/
```

在 Ubuntu/Debian，可用：

```bash
dpkg -L nvidia-driver-<版本号> | grep '\.so'
```

在 RHEL/CentOS/Fedora：

```bash
rpm -ql kmod-nvidia | grep '\.so'
```

我假设你是 Ubuntu/Debian，并且库主要在 `/usr/lib/x86_64-linux-gnu`。

---

## 3. Podman 运行命令

示例（假设 CUDA 12.1，对应 PyTorch 2.3.1）：

```bash
  #--security-opt=label=disable \  # 关 SELinux 限制
podman run -it --rm \
  --device /dev/nvidia0 \
  --device /dev/nvidiactl \
  --device /dev/nvidia-uvm \
  --device /dev/nvidia-uvm-tools \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so:/usr/lib/x86_64-linux-gnu/libcuda.so:ro \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so.1:/usr/lib/x86_64-linux-gnu/libcuda.so.1:ro \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so.575.64.03:/usr/lib/x86_64-linux-gnu/libcuda.so.575.64.03:ro \
  localhost/ubuntu-py3-ffmpeg-gpu:latest
```

进入容器后测试：

```python
import torch
print(torch.cuda.is_available())  # True
print(torch.cuda.get_device_name(0))
```

---

## 4. 注意事项

1. **库路径必须匹配驱动版本**
   宿主机驱动升级后，你必须重新挂载新路径。
2. **PyTorch 镜像的 CUDA 版本要和宿主机驱动兼容**
   例如：

   * 驱动 535.x 支持 CUDA ≤ 12.2
   * 驱动 550.x 支持 CUDA ≤ 12.4
3. 如果找不到 `libcuda.so`，用 `find` 查：

   ```bash
   find /usr/lib* -name 'libcuda.so*'
   ```

   然后把包含它的目录也挂载进容器。
4. rootless Podman 这种方法也能用，但要保证当前用户有权限访问 `/dev/nvidia*`（`usermod -aG video $USER`）。

---

我可以帮你写一个 **`podman-compose.yml`**，直接把这些设备和挂载写进去，一条 `podman-compose up` 就能起 GPU PyTorch 容器，不用每次手敲命令。
你要我帮你写这个 `compose` 文件吗？这样以后跑起来就方便了。

