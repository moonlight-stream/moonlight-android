# Application.mk for Limelight

# Our minimum version is Android 4.1
APP_PLATFORM := android-16

# NOTE: our armeabi-v7a libraries require NEON support
APP_ABI := armeabi-v7a x86

# We want an optimized build
APP_OPTIM := release
