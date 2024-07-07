#!/bin/bash
# date 2022-02-14 22:29:15
# author calllivecn <calllivecn@outlook.com>

set -ex

v2ray_pkg="v2ray-linux-64.zip"

v2ray_path="/v2ray"

# 下载 jsonfmy.py
#wget -O /usr/local/bin/jsonfmt.py https://github.com/calllivecn/mytools/raw/master/jsonfmt.py

download(){
tag_name_json="/tmp/tag_name.json"

wget -O $tag_name_json https://api.github.com/repos/v2ray/v2ray-core/releases/latest


cat > /tmp/json_tag_name.py<<EOF
import json
with open("$tag_name_json") as f:
   data = json.load(f)
print(data["tag_name"])
EOF

tag_name=$(python3 /tmp/json_tag_name.py)

rm -v /tmp/json_tag_name.py

wget -O "/tmp/$v2ray_pkg" https://github.com/v2fly/v2ray-core/releases/download/$tag_name/$v2ray_pkg

}

if [ -f /$v2ray_pkg ];then
	unzip -d "$v2ray_path" "/$v2ray_pkg"
else
	download
fi

rm -v "/$v2ray_pkg"

mv -v "$v2ray_path/config.json" "$v2ray_path/config.json-bak"

chmod -v +x "$v2ray_path/v2ray"


rm -rfv /run-build.sh
