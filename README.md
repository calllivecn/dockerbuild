## docker files

保存一些dockerfile

### apline 国内镜像源
- 编辑/etc/apk/repositories，然后在文件的最顶端添加(注意将3.3换成需要的版本)

- `echo http://mirrors.ustc.edu.cn/alpine/v3.3/main/ > /etc/apk/repssitories`


## base enviroument
### py3
- `cd py3`
- `docker build -t alpine_py3 .`

### java
- `cd java`
- `docker build -t alpine_jre8 .`

