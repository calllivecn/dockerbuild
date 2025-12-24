apt -y update
apt -y install python3 python3-pip python3-venv ffmpeg ca-certificates vim locales

# 启用 zh_CN.UTF-8 locale
sed -i '/zh_CN.UTF-8/s/^# //g' /etc/locale.gen
locale-gen

python3 -m venv /pytorch

pip3 config --global set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple

source /pytorch/bin/activate
pip install ipython cryptography torch torchaudio torchvision
pip cache purge

apt clean
rm -rf /var/lib/apt/lists

