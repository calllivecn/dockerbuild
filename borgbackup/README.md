## from alpine-py3:latest build borgbackup 备份工具(可以和ssh配合做远程备份+加密备份)

## 启动 borg-server

```shell
podman run -d --name borg -p 8822:22 -v /mnt/data/borgbackups/:/borgbackups localhost/borgbackup:latest
#or  启动后在添加ssh authorized key: podman cp -v ~/.ssh/id_ed25519.pub borg:/root/.ssh/authorized_key

#or 在启动时给出ssh public key
podman run -d --name borg -p 8822:22 -e SSH_PUBKEY="ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEATkMTJEshMhpATqAxgrcfRlpKq80V64lBWpcOiZHSW 这是borgbackup 的key" -v /mnt/data/borgbackups/:/borgbackups localhost/borgbackup:latest
```


## 远程备份时执行示例。这样不对，run 时没有ssh key的时候可以手动输入密码

```shell
podman exec -it borg-client borg create ssh://ssh_remote_user@ssh_remote_host:ssh_remote_port/borgbackups/repo_name <要备份的本机目录 dir1> [<dir2> ...]

```

## 远程备份时执行示例。使用ssh key 做自动备份。

- 在borg-client 容器中生成sshkey 并添加到远程的borg-server
- 在borg-client 使用环境变量传递 borg 密码

```shell
1. podman exec -it borg-client ash
2. ssh-keygen 
3. 添加 ~/.ssh/id_ed25519.pub 到远程 borg-server.

4. 在borg-client 执行加密备份时，可以使用 -e BORG_PASSPHRASE=<password> 环境变量传递密码。
```

- 在创建borg-client 时注意使用 --volume 挂载主机上需要备份的目录到borg-client 中。

``shell
podman run -itd --name borg-client -v <host dir1>:<dir1> -v <host dir2>:<dir2> localhost/borgbackup:latest ash
```

- 使用borg-client 进行备份。

```shell
podman exec -it borg-client borg create ssh://ssh_remote_user@ssh_remote_host:ssh_remote_port/borgbackups/repo_name <要备份的本机目录 dir1> [<dir2> ...]

```



## 在使用system.timer配合做自动远程备份

- 1. 使用system.timer

```
[Unit]
Desctiption= borg 自动备份 容器版 borg-client 

[Timer]
OnbootSec=15m
...
```

