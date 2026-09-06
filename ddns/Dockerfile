FROM alpine-py3:latest

COPY src /
COPY requirements.txt /

RUN pip install --break-system-packages -r /requirements.txt || pip install -r /requirements.txt

# 默认跑的是服务端
CMD ["python", "/ddns.py"]

# 启行时，需要把配置文件挂载到这里
WORKDIR /conf

EXPOSE 2022/udp
EXPOSE 2022/tcp
