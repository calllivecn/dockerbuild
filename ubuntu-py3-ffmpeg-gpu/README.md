# ubuntu-py3-ffmpeg-gpu:latest 

- 构建

```bash
bash build.sh
```

- 使用podman 运行时，使用宿主机的nvidia GPU

```shell
  #--security-opt=label=disable \  # 关 SELinux 限制
  #-v /usr/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:ro \
  #-v /usr/lib/x86_64-linux-gnu:/host-driver-libs:ro \
  #-e LD_LIBRARY_PATH=/host-driver-libs \
podman run -it --rm \
  --device /dev/nvidia0 \
  --device /dev/nvidiactl \
  --device /dev/nvidia-uvm \
  --device /dev/nvidia-uvm-tools \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so:/usr/lib/x86_64-linux-gnu/libcuda.so:ro \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so.1:/usr/lib/x86_64-linux-gnu/libcuda.so.1:ro \
  -v /usr/lib/x86_64-linux-gnu/libcuda.so.575.64.03:/usr/lib/x86_64-linux-gnu/libcuda.so.575.64.03:ro \
  localhost/ubuntu-py3-ffmpeg-gpu:latest bash
```
