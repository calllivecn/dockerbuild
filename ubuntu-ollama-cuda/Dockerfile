FROM ubuntu-devel:latest

#&& apt -y install gcc make zlib1g-dev libssl-dev \

# 在构建的过程中使用代码，但是不固定到镜像中。
ARG https_proxy=

RUN curl -fsSL https://ollama.com/install.sh | sh

CMD ["ollama", "serve"]
