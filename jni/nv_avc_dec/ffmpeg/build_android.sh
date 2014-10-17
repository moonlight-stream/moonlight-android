ANDROID_API_TARGET=21
PARALLEL_JOBS=$(nproc)

rm -r ./android

function build_one
{
PREFIX=$(pwd)/android/$CPU
SYSROOT=$NDK/platforms/android-$ANDROID_API_TARGET/arch-$SYSROOT_CPU
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
SYSROOT_CPU=mips
TOOLCHAIN_BIN_PREFIX=mipsel-linux-android
TOOLCHAIN_DIR=mipsel-linux-android-4.9
ADDI_CFLAGS="-mips32 -mhard-float -EL -mno-dsp"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS="--disable-mips32r2 --disable-mipsdspr1 --disable-mipsdspr2"
build_one
}

# Currently broken due to what seems to be an ffmpeg issue
function build_mips64
{
CPU=mips64
SYSROOT_CPU=mips64
TOOLCHAIN_BIN_PREFIX=mips64el-linux-android
TOOLCHAIN_DIR=mips64el-linux-android-4.9
ADDI_CFLAGS="-mips64r6"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

function build_x86
{
CPU=x86
SYSROOT_CPU=x86
TOOLCHAIN_BIN_PREFIX=i686-linux-android
TOOLCHAIN_DIR=x86-4.9
ADDI_CFLAGS="-march=i686 -mtune=atom -mstackrealign -msse -msse2 -msse3 -mssse3 -mfpmath=sse -m32"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

function build_x86_64
{
CPU=x86_64
SYSROOT_CPU=x86_64
TOOLCHAIN_BIN_PREFIX=x86_64-linux-android
TOOLCHAIN_DIR=x86_64-4.9
ADDI_CFLAGS="-msse -msse2 -msse3 -mssse3 -msse4 -msse4.1 -msse4.2 -mpopcnt -m64"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

function build_armv7
{
CPU=arm
SYSROOT_CPU=arm
TOOLCHAIN_BIN_PREFIX=arm-linux-androideabi
TOOLCHAIN_DIR=arm-linux-androideabi-4.9
ADDI_CFLAGS="-marm -mfpu=vfpv3-d16"
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

function build_armv8
{
CPU=aarch64
SYSROOT_CPU=arm64
TOOLCHAIN_BIN_PREFIX=aarch64-linux-android
TOOLCHAIN_DIR=aarch64-linux-android-4.9
ADDI_CFLAGS=""
ADDI_LDFLAGS=""
ADDI_CONFIGURE_FLAGS=""
build_one
}

build_mips
#build_mips64
build_x86
build_x86_64
build_armv7
build_armv8
