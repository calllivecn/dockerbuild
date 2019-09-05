# docker-registry 配置

Docker Registry之删除镜像、垃圾回收
Docker仓库在2.1版本中支持了删除镜像的API，但这个删除操作只会删除镜像元数据，不会删除层数据。在2.4版本中对这一问题进行了解决，增加了一个垃圾回收命令，删除未被引用的层数据。本文对这一特性进行了体验，具体步骤如下。

1、部署镜像仓库

- （1）启动仓库容器

```shell
docker run -d -v /home/config.yml:/etc/docker/registry/config.yml -p 4000:5000 --name test_registry registry:latest
```

这里需要说明一点，在启动仓库时，需在配置文件中的storage配置中增加delete=true配置项，允许删除镜像，本次试验采用如下配置文件：

```shell
# cat /home/config.yml
version: 0.1
log:
  fields:
    service: registry
storage:
    delete:
        enabled: true
    cache:
        blobdescriptor: inmemory
    filesystem:
        rootdirectory: /var/lib/registry
http:
    addr: :5000
    headers:
        X-Content-Type-Options: [nosniff]
health:
  storagedriver:
    enabled: true
    interval: 10s
    threshold: 3
``` 
- （2）上传镜像

```shell
# docker tag centos 10.229.43.217:4000/xcb/centos
# docker push 10.229.43.217:4000/xcb/centos
Thepushrefersto a repository [10.229.43.217:4000/xcb/centos]
5f70bf18a086: Pushed 
4012bfb3d628: Pushed
latest: digest: sha256:5b367dbc03f141bb5246b0dff6d5fc9c83d8b8d363d0962f3b7d344340e458f6 size: 1331
```

- （3）查看数据进行仓库容器中，通过du命令查看大小，可以看到当前仓库数据大小为61M。

```shell
# docker exec -it test_registry /bin/bash
# du -sch /var/lib/registry
61M .
61M total
```

## 2、删除镜像
删除镜像对应的API如下：

DELETE /v2/<name>/manifests/<reference>
 
name:镜像名称

reference: 镜像对应sha256值

- （1）发送请求，删除刚才上传的镜像

```shell
# curl -I -X DELETE http://10.229.43.217:4000/v2/xcb/centos/manifests/sha256:5b367dbc03f141bb5246b0dff6d5fc9c83d8b8d363d0962f3b7d344340e458f6
HTTP/1.1 202 Accepted
Docker-Distribution-Api-Version: registry/2.0
X-Content-Type-Options: nosniff
Date: Wed, 06 Jul 2016 09:24:15 GMT
Content-Length: 0
Content-Type: text/plain; charset=utf-8
```

- （2）查看数据大小

```shell
/var/lib/registry# du -sch
61M .
61M total
```

可以看到数据大小没有变化（只删除了元数据）

## 3、垃圾回收
- （1）进行容器执行垃圾回收命令

```shell
命令：registry garbage-collect config.yml

/var/lib/registry# registry garbage-collect /etc/docker/registry/config.yml
INFO[0000] Deletingblob: /docker/registry/v2/blobs/sha256/96/9687900012707ea43dea8f07a441893903dd642d60668d093c4d4d2c5bedd9eb  go.version=go1.6.2 instance.id=4d875a6c-764d-4b2d-a7c2-4e85ec2b9d58
INFO[0000] Deletingblob: /docker/registry/v2/blobs/sha256/a3/a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4  go.version=go1.6.2 instance.id=4d875a6c-764d-4b2d-a7c2-4e85ec2b9d58
INFO[0000] Deletingblob: /docker/registry/v2/blobs/sha256/c3/c3bf6062f354b9af9db4481f24f488da418727673ea76c5162b864e1eea29a4e  go.version=go1.6.2 instance.id=4d875a6c-764d-4b2d-a7c2-4e85ec2b9d58
INFO[0000] Deletingblob: /docker/registry/v2/blobs/sha256/5b/5b367dbc03f141bb5246b0dff6d5fc9c83d8b8d363d0962f3b7d344340e458f6  go.version=go1.6.2 instance.id=4d875a6c-764d-4b2d-a7c2-4e85ec2b9d58
```

- （2）查看数据大小

```shell
/var/lib/registry# du -sch                                                
108K    .
108K    total
```
