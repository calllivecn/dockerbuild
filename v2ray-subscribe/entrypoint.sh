#!/bin/bash
# date 2022-02-14 22:37:20
# author calllivecn <c-all@qq.com>


cat > "$v2ray_path/config.json" <EOF
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
            "address": "$MVESS_ADDR",
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
