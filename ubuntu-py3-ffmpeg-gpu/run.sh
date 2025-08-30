apt -y update
apt -y install python3 python3-pip python3-venv ffmpeg ca-certificates
python3 -m venv /pytorch

pip3 config --global set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple

source /pytorch/bin/activate
pip install torch torchaudio torchvision
pip cache purge

apt clean
rm -rf /var/lib/apt/lists

