FROM ubuntu-devel:latest

RUN apt -y update && apt -y upgrade \
&& apt -y install fonts-noto-cjk-extra \
&& fc-cache -f -v \
&& apt clean \
&& rm -rf /var/lib/apt/lists
