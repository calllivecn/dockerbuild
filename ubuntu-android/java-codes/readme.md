# 编码过程






# 在安卓上运行命令格式：

```shell

# 运行程序
# app_process /system/bin/ 表示 app_process 运行在 system/bin 目录下
# 后面跟着的是你的主类名
CLASSPATH=$(pwd)/CameraVideoRecorder.jar app_process /system/bin/ CameraVideoRecorder

```



## 可以使用 testing/ffmpeg/PyAv--/50-tcp4h265.py 远程录制。

- 问题1: 当前录制的mkv文件，能被vlc ffplay 正常播放。但是不能被mpv播放。

