# Android.mk for Limelight's AV decoder
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := nv_av_dec
LOCAL_SRC_FILES := nv_opus_dec.c nv_opus_dec_jni.c \
                   nv_avc_dec.c nv_avc_dec_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libopus/inc \
                    $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include

# Link to libopus and ffmpeg libraries
LOCAL_STATIC_LIBRARIES := libopus
LOCAL_SHARED_LIBRARIES := libavcodec libavformat libswscale libavutil libavfilter libwsresample

include $(BUILD_SHARED_LIBRARY)
