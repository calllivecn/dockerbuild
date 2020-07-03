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


## build可能的问题

```
阿里云的源总会(也不确定是阿里云的锅。)：
E: Failed to fetch http://mirrors.aliyun.com/ubuntu/pool/main/libv/libvdpau/vdpau-driver-all_1.3-1ubuntu2_amd64.deb Undetermined Error [IP: 113.96.109.246 80]
E: Unable to fetch some archives, maybe run apt-get update or try with --fix-missing?
				    
目前解决方式，多执行几次: apt install winehq-stable
不行的话: 可以手动执行构建过程，完成后，docker commit 
```
