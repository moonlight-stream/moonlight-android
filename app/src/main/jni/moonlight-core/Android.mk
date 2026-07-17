# Android.mk for moonlight-core and binding
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

AUDIO_HAPTICS_SHADOW ?= 0
AUDIO_HAPTICS_OUTPUT ?= 0
ifneq ($(filter 1,$(AUDIO_HAPTICS_SHADOW) $(AUDIO_HAPTICS_OUTPUT)),)
ifeq ($(strip $(AUDIO_HAPTICS_SDK_DIR)),)
$(error AUDIO_HAPTICS_SDK_DIR is required when audio haptics is enabled)
endif
endif
ifeq ($(AUDIO_HAPTICS_SHADOW),1)
include $(AUDIO_HAPTICS_SDK_DIR)/Android.mk
endif

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := moonlight-core

LOCAL_SRC_FILES := moonlight-common-c/src/AudioStream.c \
                   moonlight-common-c/src/ByteBuffer.c \
                   moonlight-common-c/src/Connection.c \
                   moonlight-common-c/src/ConnectionTester.c \
                   moonlight-common-c/src/ControlStream.c \
                   moonlight-common-c/src/FakeCallbacks.c \
                   moonlight-common-c/src/InputStream.c \
                   moonlight-common-c/src/LinkedBlockingQueue.c \
                   moonlight-common-c/src/Misc.c \
                   moonlight-common-c/src/Platform.c \
                   moonlight-common-c/src/PlatformCrypto.c \
                   moonlight-common-c/src/PlatformSockets.c \
                   moonlight-common-c/src/RtpAudioQueue.c \
                   moonlight-common-c/src/RtpVideoQueue.c \
                   moonlight-common-c/src/RtspConnection.c \
                   moonlight-common-c/src/RtspParser.c \
                   moonlight-common-c/src/SdpGenerator.c \
                   moonlight-common-c/src/SimpleStun.c \
                   moonlight-common-c/src/VideoDepacketizer.c \
                   moonlight-common-c/src/VideoStream.c \
                   moonlight-common-c/src/MicrophoneStream.c \
                   moonlight-common-c/nanors/rs.c \
                   moonlight-common-c/nanors/deps/obl/oblas_common.c \
                   moonlight-common-c/nanors/deps/obl/oblas_lite.c \
                   moonlight-common-c/enet/callbacks.c \
                   moonlight-common-c/enet/compress.c \
                   moonlight-common-c/enet/host.c \
                   moonlight-common-c/enet/list.c \
                   moonlight-common-c/enet/packet.c \
                   moonlight-common-c/enet/peer.c \
                   moonlight-common-c/enet/protocol.c \
                   moonlight-common-c/enet/unix.c \
                   moonlight-common-c/enet/win32.c \
                   simplejni.c \
                   callbacks.c \
                   minisdl.c \
                   OpusEncoder.c \
                   bass_energy_bridge.cpp \
                   audio_haptics_shadow_bridge.cpp \
                   audio_haptics_android_adapter_bridge.cpp \

LOCAL_C_INCLUDES := $(LOCAL_PATH)/moonlight-common-c/enet/include \
                    $(LOCAL_PATH)/moonlight-common-c/nanors \
                    $(LOCAL_PATH)/moonlight-common-c/nanors/deps/obl \
                    $(LOCAL_PATH)/moonlight-common-c/nanors/deps \
                    $(LOCAL_PATH)/moonlight-common-c/src \

LOCAL_CFLAGS := -DHAS_SOCKLEN_T=1 -DLC_ANDROID -DHAVE_CLOCK_GETTIME=1 \
                 -DMOONLIGHT_AUDIO_HAPTICS_SHADOW=$(AUDIO_HAPTICS_SHADOW) \
                 -DMOONLIGHT_AUDIO_HAPTICS_OUTPUT=$(AUDIO_HAPTICS_OUTPUT)

ifeq ($(AUDIO_HAPTICS_OUTPUT),1)
LOCAL_C_INCLUDES += $(AUDIO_HAPTICS_SDK_DIR)/platform/android/src/main/cpp/include
endif

ifeq ($(NDK_DEBUG),1)
LOCAL_CFLAGS += -DLC_DEBUG
endif

LOCAL_LDLIBS := -llog -landroid -ldl

LOCAL_STATIC_LIBRARIES := libopus libssl libcrypto cpufeatures
ifeq ($(AUDIO_HAPTICS_SHADOW),1)
LOCAL_STATIC_LIBRARIES += moonlight_haptics_core
endif
LOCAL_LDFLAGS += -Wl,--exclude-libs,ALL

LOCAL_BRANCH_PROTECTION := standard

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)
