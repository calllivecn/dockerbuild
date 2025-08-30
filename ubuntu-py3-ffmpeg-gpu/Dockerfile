FROM ubuntu-core:25.04

#RUN apt -y update \
#&& apt -y install python3 python3-pip python3-venv ffmpeg \
#&& python3 -m venv /pytorch \
#&& pip3 config --global set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple \
#&& source /pytorch/bin/activate \
#&& pip install torch torchaudio torchvision \
#&& apt clean \
#&& rm -rf /var/lib/apt/lists


COPY run.sh /tmp/

RUN bash /tmp/run.sh \
&& rm -f /tmp/run.sh

CMD ["bash"]

