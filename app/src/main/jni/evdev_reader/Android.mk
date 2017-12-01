# Android.mk for Limelight's Evdev Reader
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

# Only build evdev_reader for the rooted APK flavor
ifeq (root,$(PRODUCT_FLAVOR))
    include $(CLEAR_VARS)
    LOCAL_MODULE    := evdev_reader
    LOCAL_SRC_FILES := evdev_reader.c
    LOCAL_LDLIBS    := -llog


    # This next portion of the makefile is mostly copied from build-executable.mk but
    # creates a binary with the libXXX.so form so the APK will install and drop
    # the binary correctly.

    LOCAL_BUILD_SCRIPT := BUILD_EXECUTABLE
    LOCAL_MAKEFILE     := $(local-makefile)

    $(call check-defined-LOCAL_MODULE,$(LOCAL_BUILD_SCRIPT))
    $(call check-LOCAL_MODULE,$(LOCAL_MAKEFILE))
    $(call check-LOCAL_MODULE_FILENAME)

    # we are building target objects
    my := TARGET_

    $(call handle-module-filename,lib,$(TARGET_SONAME_EXTENSION))
    $(call handle-module-built)

    LOCAL_MODULE_CLASS := EXECUTABLE
    include $(BUILD_SYSTEM)/build-module.mk
endif

