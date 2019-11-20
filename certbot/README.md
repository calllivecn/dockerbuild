### from py3:latest build certbot command

### build:

```shell

```

### HELP:

```shell
docker run -it --rm certbot:latest [--help]
```

### 创建新的证书：

```shell
docker run -it --rm -v $(pwd):/var/letsencrypt/ certbot:latest certonly --preferred-challenges dns-01 \
	--manual --manual-auto-hook "python3 /ali_dns.py auth ${APPID} ${SECRETKEY}" --manual-cleanup-hook "python3 /ali_dns.py cleanup ${APPID} ${SECRETKEY}" \
	-m "c-all@qq.com" --domain "yourdomainname.cc" --domain "*.yourdomainname.com"
```


