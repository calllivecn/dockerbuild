# docker 里运行 Wine

- containers/

	容器build时的脚本

### 测试 wine 里安装新exe应用时：

```shell
bash run-wine-gui.sh

docker exec -it winegui su - wine

# 配置wine
winecfg

# 这里可以开始测试exe应用, 测试好之后可以在封装一个新应用镜像。

```

### 可以设置安装winehq 时的代理 export APT_PROXY="http://127.0.0.1" 
```bash
bash build.sh
```