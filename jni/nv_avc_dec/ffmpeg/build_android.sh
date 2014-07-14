ANDROID_API_TARGET=16
PARALLEL_JOBS=$(nproc)

function build_one
{
PREFIX=$(pwd)/android/$CPU
SYSROOT=$NDK/platforms/android-$ANDROID_API_TARGET/arch-$CPU
TOOLCHAIN_PATH=$NDK/toolchains/$TOOLCHAIN_DIR/prebuilt/linux-x86_64
./configure \
    --prefix=$PREFIX \
    --enable-shared \
    --disable-static \
    --disable-programs \
    --disable-doc \
    --disable-symver \
    --disable-debug \
    --disable-everything \
    --disable-avdevice \
    --disable-avfilter \
    --enable-decoder=h264 \
    --cross-prefix=$TOOLCHAIN_PATH/bin/$TOOLCHAIN_BIN_PREFIX- \
    --target-os=linux \
    --arch=$CPU \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --enable-pic \
    --extra-cflags="-O2 -fpic $ADDI_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS" \
    $ADDI_CONFIGURE_FLAGS
make clean
make -j$PARALLEL_JOBS
make install
}

function build_mips
{
CPU=mips
TOOLCHAIN_BIN_PREFIX=mipsel-linux-android
TOOLCHAIN_DIR=mipsel-linux-android-4.8
ADDI_CFLAGS="-mips32 -mhard-float -EL -mno-dsp"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS="--disable-mips32r2 --disable-mipsdspr1 --disable-mipsdspr2"
build_one
}

function build_x86
{
CPU=x86
TOOLCHAIN_BIN_PREFIX=i686-linux-android
TOOLCHAIN_DIR=x86-4.8
ADDI_CFLAGS="-march=i686 -mtune=atom -mstackrealign -msse3 -mfpmath=sse -m32"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

function build_armv7
{
CPU=arm
TOOLCHAIN_BIN_PREFIX=arm-linux-androideabi
TOOLCHAIN_DIR=arm-linux-androideabi-4.8
ADDI_CFLAGS="-marm -mfpu=vfpv3-d16"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

build_mips
build_x86
build_armv7
