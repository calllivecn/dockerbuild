
# 假设你的代码文件名为 CameraVideoRecorder.java
# ANDROID_HOME 指向你的 Android SDK 根目录
# 选择一个适合你目标设备的 Android API 级别 (例如，Android 34)

ANDROID_JAR=$(find "$ANDROID_SDK_ROOT/platforms" -name "android.jar" | grep "android-[0-9][0-9]" | sort -V | tail -n 1)

if [ -z "$ANDROID_JAR" ]; then
    echo "未找到 android.jar。请检查 ANDROID_HOME 环境变量或 SDK 安装。"
    exit 1
fi

echo "使用 Android Jar: $ANDROID_JAR"


build="build"
if [ -d "$build" ];then
	rm -rf "$build"
fi

# 输出前缀
PREFIX="$1"
shift

# 编译 Java 文件，生成 .class 文件
# -source 1.8 -target 1.8 确保兼容性
# --release 8 是 Java 8 的简写
#javac --release 8 -classpath "$ANDROID_JAR" CameraVideoRecorder.java InitializeAndroidEnvironment.java -d "$build"
javac --release 8 -classpath "$ANDROID_JAR" "$@" -d "$build"

# 检查编译是否成功
if [ $? -ne 0 ]; then
    echo "Java 编译失败。"
    exit 1
fi

echo "Java 代码编译成功。"

pushd "$build"
# 使用 d8 将 .class 文件转换为 .dex 文件, --min-api 28 确保兼容性(28 对应 Android 9)
d8 *.class --min-api 28 --output . 

# 将 .class 文件打包成 .jar 文件
# jar -cvf CameraVideoRecorder.jar CameraVideoRecorder.class

mv -v classes.dex ../${PREFIX}.dex
popd

# 检查 jar 包是否生成
if [ -f ${PREFIX}.jar ] || [ -f ${PREFIX}.dex ];then
    echo "Jar 或 dex 包创建成功。"
else
    echo "Jar or dex 包创建失败。"
    exit 1
fi


