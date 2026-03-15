FROM ubuntu-core:latest


RUN apt -y update \
&& apt -y --no-install-recommends install iproute2 vim bash-completion less iputils-ping telnet wget curl git \
&& apt clean \
&& rm -rf /var/lib/apt/lists

