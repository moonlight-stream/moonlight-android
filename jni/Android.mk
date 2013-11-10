# Android.mk for Limelight's Opus decoder

# This allows the the build system to find the source files
LOCAL_PATH := $(call my-dir)

# This clears local variables for us
include $(CLEAR_VARS)

# Declare our module name and source files
LOCAL_MODULE    := nv_opus_dec
LOCAL_SRC_FILES := nv_opus_dec.c nv_opus_dec_jni.c

# Set the local include path to the GMP directory for our architecture
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libopus/inc

# Link to libopus
LOCAL_LDLIBS := -L$(LOCAL_PATH)/libopus/$(TARGET_ARCH_ABI) -lopus

# Build a shared library
include $(BUILD_SHARED_LIBRARY)
