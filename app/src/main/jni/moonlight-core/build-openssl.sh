PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
OUTPUT_DIR=~/openssl

BASE_ARGS="no-shared no-ssl3 no-stdio no-engine no-hw"

set -e

./Configure android-arm $BASE_ARGS -D__ANDROID_API__=16
make clean
make build_libs -j`nproc`
cp lib*.a $OUTPUT_DIR/armeabi-v7a/

./Configure android-arm64 $BASE_ARGS -D__ANDROID_API__=21
make clean
make build_libs -j`nproc`
cp lib*.a $OUTPUT_DIR/arm64-v8a/

./Configure android-x86 $BASE_ARGS -D__ANDROID_API__=16
make clean
make build_libs -j`nproc`
cp lib*.a $OUTPUT_DIR/x86/

./Configure android-x86_64 $BASE_ARGS -D__ANDROID_API__=21
make clean
make build_libs -j`nproc`
cp lib*.a $OUTPUT_DIR/x86_64/
cp -R include/ $OUTPUT_DIR/include