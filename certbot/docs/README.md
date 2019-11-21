## Let's Encrypt and certbot 签名证书和自动更新。

### Docs:

- [Let's Encrypt: 验证方式](https://letsencrypt.org/zh-cn/docs/challenge-types/)
- [Let's Encrypt: 的运作方式](https://letsencrypt.org/zh-cn/how-it-works/)
- [Let's Encrypt 社区](https://community.letsencrypt.org/)
- [certbot 命令使用方法](https://certbot.eff.org/docs/using.html)

### 一篇好的博客:
- [blog](http://blog.dreamlikes.cn/archives/1028)

### certbot 三方hook工具：

- [acme.sh/dns_ali.sh](https://github.com/Neilpang/acme.sh/raw/master/dnsapi/dns_ali.sh)

---

## 验证前后挂钩: 用法 和 环境变量参数

当在手动模式下运行时，Certbot允许指定验证前和验证后挂钩。用于指定这些脚本的标志分别是--manual-auth-hook 和--manual-cleanup-hook，可以如下使用：

certbot certonly --manual --manual-auth-hook /path/to/http/authenticator.sh --manual-cleanup-hook /path/to/http/cleanup.sh -d secure.example.com

这将运行authenticator.sh脚本，尝试进行验证，然后运行cleanup.sh脚本。此外，certbot会将相关的环境变量传递给以下脚本：

CERTBOT_DOMAIN：正在验证的域

CERTBOT_VALIDATION：验证字符串（仅HTTP-01和DNS-01）

CERTBOT_TOKEN：HTTP-01挑战的资源名称部分（仅适用于HTTP-01）

### 另外用于清理：

### CERTBOT_AUTH_OUTPUT：无论auth脚本写到stdout的是什么

### HTTP-01的示例用法：

```shell
certbot certonly --manual --preferred-challenges=http --manual-auth-hook /path/to/http/authenticator.sh --manual-cleanup-hook /path/to/http/cleanup.sh -d secure.example.com
```

/path/to/http/authenticator.sh

```shell
#!/bin/bash
echo $CERTBOT_VALIDATION > /var/www/htdocs/.well-known/acme-challenge/$CERTBOT_TOKEN
/路径/到/http/cleanup.sh
```

```shell
#!/bin/bash
rm -f /var/www/htdocs/.well-known/acme-challenge/$CERTBOT_TOKEN
DNS-01（Cloudflare API v4）的用法示例（仅出于示例目的，请勿原样使用）
```

```shell
$ certbot certonly --manual --preferred-challenges=dns --manual-auth-hook /path/to/dns/authenticator.sh --manual-cleanup-hook /path/to/dns/cleanup.sh -d secure.example.com
```