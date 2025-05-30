
build="build"
if [ -d "$build" ];then
	rm -rf "$build"
	mkdir "$build"
fi

#javac --release 8 $SRC -d "$build"
javac --release 8 MyClass.java -d "$build"

d8 MyClass.class --output "$build"

cd "$build"
jar cf ../MyClass.jar classes.dex


