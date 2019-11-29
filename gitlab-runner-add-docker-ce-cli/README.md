## 使用！！！



### executor type: 选择 shell！！！ 最好只用它来构建docker镜像！！！

- 注册runner:

```shell
docker run -it --rm \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
   gitlab-runner-docker-cli:latest register
```

- 然后:

```shell
docker run -d --name gitlab-runner \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
    -v /var/run/docker.sock:/var/run/docker.sock  \  # 这个是能构建镜像的关键
    gitlab-runner-docker-cli:latest
```
