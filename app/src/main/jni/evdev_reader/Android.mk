# Android.mk for Limelight's Evdev Reader
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := evdev_reader
LOCAL_SRC_FILES := evdev_reader.c
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)
