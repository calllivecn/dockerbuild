# ubuntu-openclaw:latest 

- 直接安装

```bash
~~bash build.sh~~

podman build --build-arg https_proxy="http://[fc03::1]:10003" -t ubuntu-openclaw .

```


## 第一次启动时需要先初始化下

- podman run -it --rm -v <保存配置目录>:/root/ --network host ubuntu-openclaw:latest openclaw gateway setup


- 之后就可以正常启动了: podman-compsoe or podman run 等
