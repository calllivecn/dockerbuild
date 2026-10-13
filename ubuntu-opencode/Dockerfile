FROM opencode-base:latest

ARG https_proxy PYTHON_VERSION=3.14

ENV PATH=/root/.local/bin:$PATH

RUN curl -fsSL https://opencode.ai/install | bash \
&& curl -LsSf https://astral.sh/uv/install.sh | bash \
&& mkdir -vp ~/.config/uv/ \
&& echo '[[index]]' > ~/.config/uv/uv.toml \
&& echo 'url = "https://pypi.tuna.tsinghua.edu.cn/simple"' >> ~/.config/uv/uv.toml \
&& echo 'default = true' >> ~/.config/uv/uv.toml \
&& uv python install $PYTHON_VERSION

