FROM ubuntu-base:latest


COPY *.zip *.jar /tmp/

##  RUN apt update \
##  && apt -y install zip unzip \
##  && cd /tmp/ \
##  && unzip /tmp/*.zip \
##  && cd Minecraft-Overviewer-master/ \
##  && pwd -P \
##  && ls -lh

RUN apt update \
&& apt -y install python3 build-essential python3-pillow python3-numpy python3-dev \
&& apt -y install zip unzip \
&& cd /tmp/ \
&& JAR=$(ls *.jar) \
&& VERSION=${JAR%.jar} \
&& mkdir -p ~/.minecraft/versions/${VERSION}/ \
&& mv -v ${JAR} ~/.minecraft/versions/${VERSION}/ \
&& unzip /tmp/*.zip \
&& cd Minecraft-Overviewer-master/ \
&& python3 setup.py install \
&& rm -rfv Minecraft-Overviewer-master/

ENTRYPOINT ["/usr/local/bin/overviewer.py"]

# wget 下载太慢
#&& wget -O /tmp/Overviewer.zip https://github.com/overviewer/Minecraft-Overviewer/archive/master.zip \
#&& unzip -d /tmp/ /tmp/Overviewer.zip \
