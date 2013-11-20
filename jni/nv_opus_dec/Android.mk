# Android.mk for Limelight's Opus decoder
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := nv_opus_dec
LOCAL_SRC_FILES := nv_opus_dec.c nv_opus_dec_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libopus/inc

# Link to libopus library
LOCAL_STATIC_LIBRARIES := libopus

include $(BUILD_SHARED_LIBRARY)
