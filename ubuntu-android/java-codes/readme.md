# 编码过程

## 确认的信息：

- 从MediaCodec onOutputBufferAvailable() 拿到的 buffer 都是 AnnexB格式。

  - BUFFER_FLAG_CODEC_CONFIG
  - BUFFER_FLAG_KEY_FRAME






# 在安卓上运行命令格式：

```shell

# 运行程序
# app_process /system/bin/ 表示 app_process 运行在 system/bin 目录下
# 后面跟着的是你的主类名
CLASSPATH=$(pwd)/CameraVideoRecorder.jar app_process /system/bin/ CameraVideoRecorder

```



## 可以使用 testing/ffmpeg/PyAv--/50-tcp4h265.py 远程录制。

- 以解决 ~~问题1: 当前录制的mkv文件，能被vlc ffplay 正常播放。但是不能被mpv播放。~~


## debug 

- 查看所有摄像头详细参数，包括支持的分辨率和 FPS 列表

```shell
adb shell dumpsys media.camera
```
