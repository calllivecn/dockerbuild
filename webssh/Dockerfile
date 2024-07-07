FROM alpine-py3:latest


RUN apk --no-cache add gcc python3-dev musl-dev libffi-dev \
&& pip install -U pip \
&& pip install webssh \
&& apk del -r gcc python3-dev musl-dev libffi-dev \
&& rm -fr $HOME/.cache/



#COPY --from=alpine-py3:latest / /

#CMD ["wssh", "--port=8000", "--log-file-prefix=webssh.log"]
CMD ["wssh", "--port=8000"]

EXPOSE 8000

#WORKDIR /work

