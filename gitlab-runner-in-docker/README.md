## FROM gitlab/gitlab-runner:latest 在docker里运行type:docker


### executor type: 选择 docker！！！ 


- 启动:

```shell
docker run -d --name gitlab-runner --restart always \
  -v /srv/gitlab-runner/config:/etc/gitlab-runner \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gitlab/gitlab-runner:v16.11.1
```

## ~~ -v /var/run/docker.sock:/var/run/docker.sock \ ~~


- 注册runner:

```shell
docker run -it gitlab-runner register --url http://<your gitlab>:<8080>/ --registration-token <token>
```
