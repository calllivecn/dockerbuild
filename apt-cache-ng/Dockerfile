FROM ubuntu-base:latest


RUN apt -y update \
&& DEBIAN_FRONTEND=noninteractive apt -y install apt-cacher-ng \
&& ln -sf /dev/stdout /var/log/apt-cacher-ng/apt-cacher.log \
&& ln -sf /dev/stderr /var/log/apt-cacher-ng/apt-cacher.err \
&& DEBIAN_FRONTEND=noninteractive apt install -y tzdata \
&& dpkg-reconfigure -f noninteractive tzdata \
&& ln -vsf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& apt clean all \
&& rm -rf /var/lib/apt/lists/*

COPY acng.conf /etc/apt-cacher-ng/acng.conf
EXPOSE 80
VOLUME ["/var/cache/apt-cacher-ng"]
ENTRYPOINT ["/usr/sbin/apt-cacher-ng"]
CMD ["-c","/etc/apt-cacher-ng"]

# 设置容器里的时区
