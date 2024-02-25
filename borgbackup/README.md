## from alpine-py3:latest build borgbackup 备份工具(可以和ssh配合做远程备份+加密备份)

## 启动

```shell
podman run -d --name borg -p 8822:22 -v /mnt/data/borgbackups/:/borgbackups localhost/borgbackup:latest
#or  启动后在添加ssh authorized key: podman cp -v ~/.ssh/id_ed25519.pub borg:/root/.ssh/authorized_key

#or 在启动时给出ssh public key
podman run -d --name borg -p 8822:22 -e SSH_PUBKEY="ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEATkMTJEshMhpATqAxgrcfRlpKq80V64lBWpcOiZHSW 这是borgbackup 的key" -v /mnt/data/borgbackups/:/borgbackups localhost/borgbackup:latest
```


## 远程备份时执行示例。这样不对，run 时没有ssh key呀？！？

```shell
podman run -it --rm borg borg create ssh_remote_user@ssh_remote_host:/borgbackup/repo_name <要备份的本机目录 dir1> [<dir2> ...]

```

