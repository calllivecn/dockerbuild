[Client]
# 统一地址: udp://、http:// 或 https://；未写端口时使用协议默认端口
Address="http://example.com/api/v1/update"

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

# IP 未变化时，超过该小时数也重新上报
ForceUpdateHours=6

# 是否校验 HTTPS 服务器证书；生产环境应保持 true
VerifyTLS=true

# 获取IP的命令行脚本
# 例如：/usr/local/bin/getip6.sh
# 如果不设置, 则使用默认内部方法,拿到默认路由接口的ip, 只支持ipv6。
# Cmd=""
