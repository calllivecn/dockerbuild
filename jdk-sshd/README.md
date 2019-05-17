### from java-jre8:latest build 一个与主机用户同id的容器内用户

## Usage:

```shell
docker run -d --name sshd --volumes-from $container-storage java-jre:latest
```

```shell
docker exec -it $container su - zx # (与主机用户对应的用户, 方便操作mount的文件)
```
