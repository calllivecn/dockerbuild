FROM ubuntu-base:latest

ARG https_proxy python_version=3.14

COPY uv.toml /root/.config/uv/

ENV PATH=/root/.local/bin:$PATH

RUN curl -LsSf https://astral.sh/uv/install.sh | sh \
&& uv python install $python_version \
&& ln -vs /root/.local/bin/python$python_version /root/.local/bin/python

