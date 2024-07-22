
## run --cpus 限制,查看。需要开启.

1. $ cat "/sys/fs/cgroup/user.slice/user-$(id -u).slice/user@$(id -u).service/cgroup.controllers"

2. 如输出为：memory pids ，

需要添加: systemctl edit user@.service

```
[Service]
Delegate=memory pids cpu cpuset
```

3. 然后注销用户重新登录生效。



## 使用docker-compose 插件

1. 下载安装: bash compose-install.sh

2. 安装podman-unix.service --> ~/.config/systemd/user/; systemctl daemon-reload; systemctl enable --now podman-unix

- ！root 用户的默认就在：/run/podman/podman.sock

