FROM ubuntu-base:24.04


RUN apt -y update \
&& apt -y install software-properties-common \
&& add-apt-repository -y ppa:deadsnakes \
&& apt -y install python3.13-nogil \
&& apt clean \
&& rm -rf /var/lib/apt/lists

