### from alpine-py3:latest build rclone


- rclone 使用步骤

```shell
pomdna run -itd --name rclone -v <host_dir>:/webdav --privileged --network host --device /dev/fuse ash

podman run exec -it rclone ash

# 这会进入交互配置, 根据提示来配置
rclone config

# 之后挂载--vfs-cache-mode writes或full
rclone mount <remote name>:/ /webdav --vfs-cache-mode full 



```
