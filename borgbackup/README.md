
[TOC]

# borg v1.2.x 自动备份 二进制版 borg(之后可以就出borg v2.0了)

## 这里开始使用shell + borg 二进制版使用流程：

- [二进制下载路径](https://github.com/borgbackup/borg/releases)

## 注意事项：

- 远程仓库需要确保远程服务器上安装了相同或兼容版本的 borgbackup


## 创建备份仓库

```shell
borg init -e repokey-blake2
# 跟据示输入两次密码，创建成功。-e 还可以 keyfile-blake2 使用来使用文件当做key。(在输入密码直接按回车,使用keyfile)

# 可以使用 export BORG_PASSPHRASE="your password"。
or
# 还可以使用文件：export BORG_KEY_FILE=~/.config/borg/keys/my.key
or

# 不使用密码-e none
```

## 定时备份

- 执行定时备份的脚本:

```shell
borg create --patterns-from path.txt -s ~/borg-backups::{hostname}-{user}-$(date +%F_%T)
```

- paths.txt 文本文件，一行一个要备份的目录:

```text
P pf
R /home/user1/.ssh/
R /home/.config/systemd/
R /etc/systemd/
```

## 恢复

- 恢复时只是会恢复到当前目录下。所有注意提前cd。

```shell
# 只恢复 home/ 目录
borg extract -v repo::archive home/
# or 恢复 repo::archive 归档到当前目录
borg extract -v repo::archive
```

## borg prune 清理多余的备份 + borg compact 回收空间

```shell
# 清理归档，保留最新的2个归档
borg prune -s --keep-last 2 ssh://route/mnt/data/borgs/zxdiy

# 回收空间
borg compact -s ssh://route/mnt/data/borgs/zxdiy

```

## 使用 system.timer + shell + EnvironmentFile= 的方式做定时备份

- 当前仓库目录下：
- borg-auto.service
- borg-auto.sh
- borg-auto.timer

- borg-auto.env:

	```
	SSH_REMOTE_BORG_DIR=ssh://remote/mnt/data/borgs
	BORG_PATTERNS_FROM=/home/you/.config/borg/paths.txt
	BORG_KEY_FILE=/home/you/.config/borg/my.key
	```

---

# from alpine-py3:latest build borgbackup 备份工具(可以和ssh配合做远程备份+加密备份)

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

---

