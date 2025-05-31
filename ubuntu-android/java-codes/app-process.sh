# 后面跟着的是你的主类名
#

if [ -f "$1" ];then
    JAR_DEX="$1"
else
    echo "需要指定 *.jar or *.dex 文件"
fi

shift

PWD=$(pwd)

echo "CLASSPATH=$PWD/$JAR_DEX app_process $PWD ${JAR_DEX%.*} $@"
CLASSPATH=$PWD/"$JAR_DEX" app_process $PWD "${JAR_DEX%.*}" "$@"



