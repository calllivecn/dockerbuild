### from py3:latest build certbot command

### build:

```shell
bash build.sh
```

### HELP:

```shell
docker run -it --rm certbot:latest [--help]
```

### 创建新的证书(目前只能自动签发通用证书(yourdomainname.com 和 \*.yourdomainname.com)之后更新ali_dns.py以支持指定域名自动签发)：

```shell
docker run -it --rm -v ${CERT_PATH}/letencrypt/:/etc/letsencrypt/ certbot:latest certonly -n --preferred-challenges dns-01 \
	--manual --manual-auth-hook "python3 /ali_dns.py auth ${APPID} ${SECRETKEY}" --manual-cleanup-hook "python3 /ali_dns.py cleanup ${APPID} ${SECRETKEY}" \
	-m "c-all@qq.com" --domain "yourdomainname.cc" --domain "*.yourdomainname.com"

Or

docker run -it --rm -v ${CERT_PATH}/letencrypt/:/etc/letsencrypt/ certbot:latest certonly -n --config-dir ${dir} --work-dir ${dir} --logs-dir ${dir}\
	--preferred-challenges dns-01 \
	--manual --manual-auth-hook "python3 /ali_dns.py auth ${APPID} ${SECRETKEY}" --manual-cleanup-hook "python3 /ali_dns.py cleanup ${APPID} ${SECRETKEY}" \
	-m "c-all@qq.com" --domain "yourdomainname.cc" --domain "*.yourdomainname.com"

Or 使用 certbot.pyz 

certbot.pyz certonly -n --preferred-challenges dns-01 \
	--manual --manual-auth-hook "python3 /ali_dns.py auth ${APPID} ${SECRETKEY}" --manual-cleanup-hook "python3 /ali_dns.py cleanup ${APPID} ${SECRETKEY}" \
	-m "c-all@qq.com" --domain "yourdomainname.cc" --domain "*.yourdomainname.com"
```

## **！！！${CERT_PATH}/letencrypt/ 路径是保存证书和配置的。**

## **！！！你的阿里云DNS API管理appid、secretkey会被保存在/etc/letsencrypt/renewal/${yourdomain}.conf

### 更新证书：

```shell
docker run -i --rm -v ${CERT_PATH}/letsencrypt/:/etc/letsencrypt/ certbot:latest renew
# 可以加入 cron 每周执行一次更新，证书没快到期之前certbot是不会执行证书更新的（有效期少于30天时更新证书）。

Or # 使用 certbot.pyz


```


## 腾讯云请使用 tencent_dns.py
