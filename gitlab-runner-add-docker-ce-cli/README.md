## 使用！！！



### executor type: 选择 shell！！！ 只用来，构建docker镜像！！！

```shell
docker run -it --rm \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
   gitlab-runner-docker-cli:latest register

# 然后，
docker run -d --name gitlab-runner \
    -v /etc/gitlab-runner:/etc/gitlab-runner \
    -v /var/run/docker.sock:/var/run/docker.sock  \  # 这个是能构建镜像的关键
    gitlab/gitlab-runner:latest
```
