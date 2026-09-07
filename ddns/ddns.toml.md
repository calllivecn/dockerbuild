[Ali]
# 阿里云
AccessKeyId="xxxxxxxxxxxxxxxxx"
AccessKeySecret="xxxxxxxxxxxxxxxxx"


[Server]
# UDP 和 HTTP/TCP 分别配置；未写端口时，UDP 默认 2022、HTTP 默认 80、HTTPS 默认 443
UDPAddress="udp://[::]:2022"
Address="http://[::]"
# server 的 secret
Secret="xxxxxxxxxxxxxxxxxxxxxxxxx"
# Address 使用 https:// 时需要配置证书
# CertFile="/path/to/server.crt"
# KeyFile="/path/to/server.key"


[[Clients]]
# 其他轻客户端的UUID (预计使用很少的 bash 就可以实现; bash 不行，不能接收UDP数据包。。。还是需要用golang和py写)
# 范围：1 ~ 4字节 无符号
# 更多client一直添加...
ClientID=1234

# client 的 secret
Secret="xxxxxxxxxxxxxxxxxxxxxxxxx"

# 例如域名是：dns.example.com
# 记录类型, A: ipv4, AAAA: ipv6, TXT: 文本记录
# RR: dns; Domain: example.com;
multidns = [
    {Type="AAAA", RR="client", Domain="example.com"},
]
