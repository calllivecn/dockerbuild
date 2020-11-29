FROM ubuntu-devel:latest

RUN apt update \
&& apt -y install python3 python3-pip openssh-server openssh-client \
&& echo "PermitRootLogin yes" >> /etc/ssh/sshd_config \
&& pip3 config --global set global.index-url https://mirrors.aliyun.com/pypi/simple/

# ceph-deploy ceph官方说这个已经没有维护了

WORKDIR /run/sshd/

CMD ["/usr/sbin/sshd", "-D"]

