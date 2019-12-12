## FROM gitlab/gitlab-runner:latest 安装 ssh、ansible 做CD


### executor type: 选择 shell！！！ 

- 注册runner:

```shell
docker run -it --rm \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
   gitlab-runner-docker-cli:latest register
```

- 然后启动:

```shell
docker run -d --name gitlab-runner \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /root/.ssh/:/root/.ssh/ \
    -v /etc/ansible:/etc/ansible \ 
    gitlab-runner-docker-cli:latest
```


