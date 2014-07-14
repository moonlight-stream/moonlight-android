# Android.mk for Limelight's H264 decoder
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := nv_avc_dec
LOCAL_SRC_FILES := nv_avc_dec.c nv_avc_dec_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -landroid

# Link to ffmpeg libraries
LOCAL_SHARED_LIBRARIES := libavcodec libavformat libswscale libavutil libwsresample

include $(BUILD_SHARED_LIBRARY)
