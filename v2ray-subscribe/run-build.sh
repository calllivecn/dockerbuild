#!/bin/bash
# date 2022-02-14 22:29:15
# author calllivecn <c-all@qq.com>

set -x

v2ray_pkg="v2ray-linux-64.zip"

v2ray_path="/v2ray"

# 下载 jsonfmy.py
wget -O /usr/local/bin/jsonfmt.py https://github.com/calllivecn/mytools/raw/master/jsonfmt.py

tag_name=$(wget -O- https://api.github.com/repos/v2ray/v2ray-core/releases/latest | jsonfmt.py -d tag_name)

wget -O "/tmp/$v2ray_pkg" https://github.com/v2fly/v2ray-core/releases/download/$latest_tag/$v2ray_pkg

unzip -d "$v2ray_path" "$v2ray_pkg"

rm "$v2ray_pkg"

mv -v "$v2ray_path/config.json" "$v2ray_path/config.json-bak"


