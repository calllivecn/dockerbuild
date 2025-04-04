### samba in docker

- build:
```shell
sh build.sh
```

- Usage: 查看 quitck-run.help


- 您可以使用以下命令查看Samba目前使用的默认配置项：

```shell
testparm --show-all-parameters
```


## 添加用户

- 方式一：

```sehll
createuser.sh <username> <password>
```

- 方式二：

```
# 创建本地用户
useradd -D smb1

#通过pdbedit -L可以查看samba数据库中的用户有哪些，只有在samba数据库中的用户才能访问samba服务端共享资源。
# 需要root权限，不然会出现错误提醒
pdbedit -L

pdbedit -a smb1

# 例如：删除smb1这个用户
pdbedit -x  smb1
```


## 共享配置

- 通过软件包管理工具安装的samba服务，配置文件一般是 /etc/samba/smb.conf 文件。可以通过编辑这个配置文件来指定需要共享的目录。

- 配置共享目录时的格式大致是这样的

```ini
[xxxx]              # 共享模块的名称，这里的xxxx是共享名，可以自定义
comment = xxxx      # 共享模块的描述信息
path = /path        # 共享目录的具体路径，应替换为实际路径
available = yes     # 指定该共享是否可用，yes表示可用
browseable = yes    # 是否在网络邻居中显示此共享，yes表示显示，no则隐藏
public = yes        # yes表示共享是公开的，任何人都可以访问。如果设置为 no，则需要通过身份验证才能访问。
writable = yes      # 是否允许用户写入数据到共享目录，yes表示可写
valid users = smb1  # 指定哪些用户或用户组可以访问该共享，不指定表示所有用户，如果指定用户，可以是单个用户或多个用户，用空格分隔
```

