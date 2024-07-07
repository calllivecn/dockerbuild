FROM ubuntu-base:latest


RUN apt -y update \
&& apt -y install keepalived \
&& apt clean \
&& rm -rf /var/lib/apt/lists


CMD ["/bin/bash"]
