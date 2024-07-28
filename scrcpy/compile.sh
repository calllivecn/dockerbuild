mkdir "${BUILD_OUTPUT}"
cd "${BUILD_OUTPUT}"
git clone https://github.com/Genymobile/scrcpy.git

cd scrcpy
sed -i -e 's/\<sudo\> //' install_release.sh
./install_release.sh

mkdir -vp ${BUILD_OUTPUT}/scrcpy/bin/ && mkdir -vp ${BUILD_OUTPUT}/scrcpy/share/scrcpy/
cp -va build-auto/app/scrcpy ${BUILD_OUTPUT}/scrcpy/bin/
cp -av build-auto/server/scrcpy-server ${BUILD_OUTPUT}/scrcpy/share/scrcpy/


