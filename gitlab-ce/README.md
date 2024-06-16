# 在容器中安装部署 gitlab-ce + gitlab-runner

- [官方文档](https://docs.gitlab.com/ee/install/docker.html)

## 1. 首先下载镜像 podman pull gitlab/gitlab-ce:latest


## 2.启动

```shell
export GITLAB_HOEM=<volumn dir>

podman run --detach \
  --hostname gitlab.example.com \
  --env GITLAB_OMNIBUS_CONFIG="external_url 'http://gitlab.example.com'" \
  --publish 443:443 --publish 80:80 --publish 22:22 \
  --name gitlab-ce \
  --restart always \
  --volume $GITLAB_HOME/config:/etc/gitlab \
  --volume $GITLAB_HOME/logs:/var/log/gitlab \
  --volume $GITLAB_HOME/data:/var/opt/gitlab \
  --shm-size 256m \
  gitlab/gitlab-ce:latest

```

-  # 使用对应的版本 gitlab/gitlab-ee:<version>-ee.0


## 初始化过程可能需要很长时间。您可以通过以下方式跟踪此过程：

```shell
podman logs -f gitlab-ce
```

- 启动容器后，可以访问 gitlab.example.com 。 Docker 容器可能需要一段时间才能开始响应查询。

- 访问 GitLab URL，并通过以下命令使用用户名 root 和密码登录：

```shell
sudo docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

- 密码文件在24小时后第一次容器重启时自动删除。

## 修改为中文

- 进入容器 vim /etc/gitlab/gitlab.rb
- 修改 将en改为zh-CN或zh-TW，分别代表简体中文和繁体中文。
- 保存配置文件并重新启动Gitlab CE服务。

```ini
# Default language to use for the GitLab web interface.
Gitlab::I18n.default_locale = 'en'

```


# 使用docker-compose

```yaml
version: '3.6'
services:
  gitlab:
    image: gitlab/gitlab-ee:<version>-ee.0
    container_name: gitlab
    restart: always
    hostname: 'gitlab.example.com'
    environment:
      GITLAB_OMNIBUS_CONFIG: |
        # Add any other gitlab.rb configuration here, each on its own line
        external_url 'https://gitlab.example.com'
    ports:
      - '80:80'
      - '443:443'
      - '22:22'
    volumes:
      - '$GITLAB_HOME/config:/etc/gitlab'
      - '$GITLAB_HOME/logs:/var/log/gitlab'
      - '$GITLAB_HOME/data:/var/opt/gitlab'
    shm_size: '256m'

```

- 确保您与 docker-compose.yml 位于同一目录中并启动 GitLab：

```shell
docker compose up -d
```

