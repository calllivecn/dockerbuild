FROM ubuntu-devel:latest

ARG zfs_github_url=https://github.com/openzfs/zfs/releases/download/zfs-2.0.0/zfs-2.0.0.tar.gz 
ARG zfs_pack=zfs-2.0.0.tar.gz

RUN apt update \
&& apt -y install build-essential autoconf automake libtool gawk alien fakeroot dkms libblkid-dev uuid-dev libudev-dev libssl-dev zlib1g-dev libaio-dev libattr1-dev libelf-dev linux-headers-$(uname -r) python3 python3-dev python3-setuptools python3-cffi libffi-dev \
&& wget ${zfs_github_url} \
&& cd ${zfs_pack%.tar.gz} \
&& ./configure --enalbe-systemd \
&& make -j1 deb-utils deb-kmod \
&& cp -av *.deb /zfs-deb/

VOLUME /zfs-deb/
