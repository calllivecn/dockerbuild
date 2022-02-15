#!/bin/bash
# date 2022-02-14 22:37:20
# author calllivecn <c-all@qq.com>

# 拿到 vmess addr
BANDWAGON_DATA=$(wget -O- "${SERVER_URL}")

proxy_urls=$(echo "$BANDWAGON_DATA" |base64 -d)

#vmess_count=$(echo "$proxy_urls" |grep vmess|wc -l)

VMESS_INFO=$(echo "$proxy_urls" |tr ' ' '\n' |grep -m 1 vmess)

VMESS_JOSN="$(echo "${VMESS_INFO#vmess://}" |base64 -d)"

# 提取vmess 信息
VMESS_ADDR=$(echo "$VMESS_JSON" |jsonfmt.py -d add)
VMESS_PORT=$(echo "$VMESS_JSON" |jsonfmt.py -d port)
VMESS_UUID=$(echo "$VMESS_JSON" |jsonfmt.py -d id)
VMESS_AID=$(echo "$VMESS_JSON" |jsonfmt.py -d aid)

cat > "$v2ray_path/config.json" <<EOF
{
  "log": {
    "loglevel": "info"
  },
  "inbounds": [
    {
      "port": 9999,
      "protocol": "http",
      "sniffing": {
        "enabled": true,
        "destOverride": ["http", "tls"]
      },
      "settings": {
        "auth": "noauth"
      }
    },
    {
      "port": 10000,
      "protocol": "socks",
      "sniffing": {
        "enabled": true,
        "destOverride": ["http", "tls"]
      },
      "settings": {
        "auth": "noauth"
      }
    }
  ],
  "outbounds": [
    {
      "protocol": "vmess",
      "settings": {
        "vnext": [
          {
            "address": "$VMESS_ADDR",
            "port": $VMESS_PORT,
            "users": [
              {
                "id": "$VMESS_UUID",
                "alterId": $VMESS_AID
              }
            ]
          }
        ]
      }
    }
  ]
}
EOF
