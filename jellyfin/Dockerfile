FROM docker.io/jellyfin/jellyfin:2025120105

RUN apt -y update \
&& apt -y install fonts-noto-cjk-extra fonts-wqy-microhei \
&& fc-cache -vf \
&& apt clean \
&& rm -rf /var/lib/apt/lists
