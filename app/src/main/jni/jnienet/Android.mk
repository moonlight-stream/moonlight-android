# Android.mk for Moonlight's ENet JNI binding
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := jnienet

LOCAL_SRC_FILES := jnienet.c \
                   enet/callbacks.c \
                   enet/compress.c \
                   enet/host.c \
                   enet/list.c \
                   enet/packet.c \
                   enet/peer.c \
                   enet/protocol.c \
                   enet/unix.c \
                   enet/win32.c \

LOCAL_CFLAGS := -DHAS_SOCKLEN_T=1
LOCAL_C_INCLUDES := $(LOCAL_PATH)/enet/include

include $(BUILD_SHARED_LIBRARY)
