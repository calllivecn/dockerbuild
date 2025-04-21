# 使用 Ubuntu 24.04 作为基础镜像
FROM ubuntu-devel:latest

# 设置环境变量，方便后续操作
# ANDROID_SDK_ROOT 是 SDK 的安装位置
ENV ANDROID_SDK_ROOT /opt/android-sdk

# 设置 Java 环境 (Ubuntu 24.04 通常默认安装 OpenJDK 17)
ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64

# 将 SDK 工具添加到 PATH 环境变量中
# 注意这里的 cmdline-tools/latest/bin 是 sdkmanager 的位置
# platform-tools 是 adb, fastboot 等的位置
# build-tools 是 dx, d8, aapt 等的位置，后面会动态添加到 PATH
ENV PATH "$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"

# 更新系统并安装必要的软件包：
# openjdk-17-jdk: Java 开发工具包，用于编译 Java 代码
# wget: 下载 Android SDK 命令行工具
# unzip: 解压下载的工具
# --no-install-recommends: 避免安装不必要的推荐软件包
RUN apt update && apt install -y openjdk-17-jdk wget unzip android-tools-adb android-tools-fastboot --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# 创建 SDK 安装目录
RUN mkdir -vp $ANDROID_SDK_ROOT/cmdline-tools/latest

# 下载并解压 Android SDK Command-Line Tools
# 请访问 https://developer.android.com/tools/releases/command-line 查找最新的 Linux 下载链接
# 这里的 URL 是一个示例，可能需要更新
ENV https_proxy "http://[fc03::1]:9999"
ENV http_proxy "http://[fc03::1]:9999"
ARG SDK_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" # 示例链接，请更新

RUN wget "$SDK_TOOLS_URL" -O /tmp/commandlinetools.zip && \
    unzip /tmp/commandlinetools.zip -d /tmp/unzipped_sdk/ && \
	mv /tmp/unzipped_sdk/cmdline-tools/* $ANDROID_SDK_ROOT/cmdline-tools/latest/ && \
    rm /tmp/commandlinetools.zip && \
	rm -rv /tmp/unzipped_sdk/

# 安装所需的 SDK 组件：
# platforms;android-XX: 安装指定版本的 Android 平台，包含 android.jar
# build-tools;XX.X.X: 安装指定版本的构建工具，包含 d8 (或 dx)
# platform-tools: 安装平台工具，包含 adb, fastboot
# --licenses: 自动接受所有 SDK 许可证
# 选择合适的版本，这里以 Android 34 和对应的 Build-Tools 为例
ARG PLATFORM_VERSION="android-34"
ARG BUILD_TOOLS_VERSION="34.0.0"

# 将 Build-Tools 目录添加到 PATH，以便可以直接运行 d8 等命令
ENV PATH "$PATH:$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"

RUN yes | sdkmanager --licenses && \
    sdkmanager "platforms;$PLATFORM_VERSION" "build-tools;$BUILD_TOOLS_VERSION" "platform-tools"


# 取消代理变量
ENV http_proxy= https_proxy=

# 设置容器启动后的工作目录，方便操作
WORKDIR /app

# 容器启动时执行的默认命令（可选，但方便进入 bash）
CMD ["bash"]


