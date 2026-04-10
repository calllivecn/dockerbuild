# jellyfin

- [地址](https://jellyfin.org/downloads/docker)

- 修CJK字幕 (好像是jellyfin的bug)
- 安装intel 非开源驱动库：intel-media-va-driver-non-free


## 构建

```bash
#bash build.sh
podman build -t jellyfin .

podman build --build-arg IMAGE_TAG=10.11.8 -t jellyfin .
```
