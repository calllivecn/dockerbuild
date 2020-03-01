### Minecraft Overviewer in docker

## [github](https://github.com/overviewer/Minecraft-Overviewer)


## 运行Minecarft-Overviewer必要的东西

- 对应 MC 版本的 客户端jar文件: ${VERSION}.jar


## 运行注意事项：

- docker run -it --rm -v $(pwd):/overviewer mc-overviewer:latest -v /overviewer/world /overviewer/output-world
