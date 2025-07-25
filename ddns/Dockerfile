FROM alpine-py3:latest

COPY src/ /
COPY requirements.txt /

RUN pip install --break-system-packages -r /requirements.txt || pip install -r /requirements.txt

CMD ["python", "/src/ddns.py"]

EXPOSE 2022

