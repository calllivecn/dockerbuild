FROM ubuntu-devel:latest


ARG USER
ARG UID
ARG PASSWORD

RUN apt -y update && \
apt install -y --no-install-recommends openssh-server stow libzstd1 openssl libsnappy-jni libisal2 && \
apt clean && \
mkdir /run/sshd


ADD jdk*.tar.gz /usr/local/

ENV JAVA_HOME=/usr/local JRE_HOME=/usr/local

RUN useradd -u ${UID:-1000} -m -s /bin/bash ${USER:-zx} && \
echo "${USER:-root}:${PASSWORD:-root}" |chpasswd && \
echo "PermitRootLogin yes" >> /etc/ssh/sshd_config && \
echo "StrictHostKeyChecking no" >> /etc/ssh/ssh_config

COPY sshkey.pub sshkey /tmp/

RUN mkdir -m 0700 /home/${USER:-zx}/.ssh && \
mkdir -m 0700 /root/.ssh && \
cp /tmp/sshkey.pub /root/.ssh/id_ras.pub && \
cp /tmp/sshkey.pub /root/.ssh/authorized_keys && \
cp /tmp/sshkey /root/.ssh/id_ras && \
cp /tmp/sshkey.pub /home/${USER:-zx}/.ssh/id_rsa.pub && \
mv /tmp/sshkey.pub /home/${USER:-zx}/.ssh/authorized_keys && \
mv /tmp/sshkey /home/${USER:-zx}/.ssh/id_rsa && \
chown -R ${USER:-zx}.${USER:-zx} /home/${USER:-zx}/.ssh/

CMD ["/usr/sbin/sshd", "-D"]

EXPOSE 22
