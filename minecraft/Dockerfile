FROM java:latest


#ARG apk="apk --no-cache"

RUN apk update \
&& apk add python3 \
&& rm -r /var/cache/apk/* || true

#FROM base_py3 as MCS

COPY minecraft_shell.py /

CMD ["python3","/minecraft_shell.py"]

WORKDIR /work

EXPOSE 25565/tcp
