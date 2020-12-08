#FROM docker-run-gui:latest
FROM ubuntu-devel:latest

COPY anyconnect/ /tmp/anyconnect/

# install depends
#RUN apt install -y libxml2 kmod libgtk2.0-0 \
RUN apt update \
&& DEBIAN_FRONTEND=noninteractive apt install -y libxml2 kmod \
&& ls -lh /tmp/
#gedit

RUN cd /tmp/anyconnect/vpn \
&& ./vpn_install.sh <(y)


