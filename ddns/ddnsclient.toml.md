[Client]
# 服务端地址，可以是域名，或者ipv6 ipv4
Address=
Port=2022

# 检查间隔时间单位秒
Interval=180

# 在服务端需要是唯一的
ClientId=

# client 的 secret
Secret=

# server 的 secret
ServerSecret=

# 等待ACK的超时时间
TimeOut=10

# 没有收到ACK时，重试次数
Retry=3

# 获取IP的命令行脚本
# 例如：/usr/local/bin/getip6.sh
# 如果不设置, 则使用默认内部方法,拿到默认路由接口的ip, 只支持ipv6。
# Cmd=""