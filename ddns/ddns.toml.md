[Ali]
# 阿里云
AccessKeyId="xxxxxxxxxxxxxxxxx"
AccessKeySecret="xxxxxxxxxxxxxxxxx"

[SelfDomainName]
# 检查间隔时间单位秒
Interval=180

# 检测server自己所在机器ip, 并更新指向自己的域名
# 例如域名是：dns.example.com
# Type 记录类型有, A: ipv4, AAAA: ipv6, TXT: 文本记录
# RR: dns,  Domain: example.com
# 如果有多个记录需要更新为同一ip。
# 格式如下:
multidns = [
    {Type="AAAA", RR="dns1", Domain="example.com"},
    #{Type="AAAA", RR="dns2", Domain="example.com"},
    #{Type="AAAA", RR="dns3", Domain="example.com"},
]


[Server]
Address="::"
Port=2022
# server 的 secret
Secret="xxxxxxxxxxxxxxxxxxxxxxxxx"

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

