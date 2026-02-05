ARG IMAGE_TAG

FROM docker.io/jellyfin/jellyfin:${IMAGE_TAG}

RUN sed -i 's/main$/main contrib non-free non-free-firmware/' /etc/apt/sources.list.d/debian.sources \
&& apt -y update \
&& apt -y install fonts-noto-cjk-extra fonts-wqy-microhei intel-media-va-driver-non-free \
&& fc-cache -vf \
&& apt clean \
&& rm -rf /var/lib/apt/lists
