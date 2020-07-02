# docker 里运行 Wine

- containers/

	容器build时的脚本

### 测试 wine 里安装新exe应用时：

```shell
bash run-wine-gui.sh

docker exec -it winegui bash

su - wine

# 配置wine
winecfg

# 这里可以开始测试exe应用, 测试好之后可以在封装一个新应用镜像。

```


```bash
bash build.sh
```
