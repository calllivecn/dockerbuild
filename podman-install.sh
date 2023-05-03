#!/bin/bash
# date 2022-11-22 08:19:08
# author calllivecn <c-all@qq.com>

# Ubuntu 22.04 仓库中只有 Podman 3.4.4，但是您可以使用以下命令从 Podman 官方网站安装最新版本的 Podman： 
# 
# ```shell
# sudo sh -c 'echo "deb https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_22.04/ /" > /etc/apt/sources.list.d/devel:kubic:libcontainers:stable.list'
# 
# curl -L https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_22.04/Release.key | sudo apt-key add -
# 
# sudo apt-get update
# 
# sudo apt-get install podman
# 
# 这将安装 Podman 的最新版本（目前为 4.4）¹³⁴。
# 
# 希望这可以帮助您。如果您有任何其他问题，请告诉我。😊
# ```

#URL="https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/xUbuntu_$(lsb_release -rs)"
URL="https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/unstable/xUbuntu_$(lsb_release -rs)"


# 2023-05-04 修改
#sudo mkdir -p /etc/apt/keyrings
#curl -fsSL $URL/Release.key | gpg --dearmor | sudo apt-key add -

curl -fsSL $URL/Release.key | sudo apt-key add -

echo "deb [arch=$(dpkg --print-architecture)] $URL/ /" | sudo tee /etc/apt/sources.list.d/podman-stable.list
sudo apt -y update
sudo apt -y install podman
