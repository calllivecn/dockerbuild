### from alpine-py3:latest build rclone


- rclone 使用步骤

```shell
pomdna run -itd --name rclone -v <host_dir>:/webdav --privileged --network host --device /dev/fuse localhost/rclone:latest ash

podman run exec -it rclone ash

# 这会进入交互配置, 根据提示来配置
rclone config

# 之后挂载--vfs-cache-mode writes或full
rclone mount webdav:/ /webdav --vfs-cache-mode minimal


```

- 关于 --vfs-cache-mode

```
rclone支持以下几种缓存模式：no-cache，writes，full和minimal。
默认情况下，rclone使用no-cache模式，这意味着它不会缓存任何文件。
如果您使用的是–vfs-cache-mode writes选项，
rclone将缓存所有写入文件的数据。
如果您使用的是–vfs-cache-mode full选项，rclone将缓存所有文件。
如果您使用的是–vfs-cache-mode minimal选项，rclone将仅缓存目录和元数据。
```
