FROM ubuntu-devel:latest


#&& apt -y install gcc make zlib1g-dev libssl-dev \

RUN apt -y update && apt -y upgrade \
&& apt -y install checkinstall auto-apt

RUN cd /python \
&& auto-apt run ./configure --enable-optimizations \
&& checkinstall
