//
// Created by Viktor Pih on 2020/8/6.
//

#include "VideoDecoder.h"
#include <stdlib.h>

#include <media/NdkMediaExtractor.h>

#include <sys/system_properties.h>

#include <pthread.h>

#include <android/log.h>

#   define  LOG_TAG    "VideoDecoder"
#   define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)


//
//Decoder decoder = {-1, NULL, NULL, NULL, 0, false, false, false, false};

int VIDEO_FORMAT_MASK_H264 = 0x00FF;


#define Build_VERSION_CODES_KITKAT 19
#define Build_VERSION_CODES_M 23
#define Build_VERSION_CODES_R 30

int sdk_version() {
    static char sdk_ver_str[PROP_VALUE_MAX+1];
    int sdk_ver = 0;
    if (__system_property_get("ro.build.version.sdk", sdk_ver_str)) {
        sdk_ver = atoi(sdk_ver_str);
    } else {
        // Not running on Android or SDK version is not available
        // ...
    }
    return sdk_ver;
}

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* name, const char* mimeType, int width, int height, int fps, int lowLatency) {

    int sdk_ver = sdk_version();

    // Codecs have been known to throw all sorts of crazy runtime exceptions
    // due to implementation problems
    AMediaCodec* codec = AMediaCodec_createCodecByName(name);

    AMediaFormat* videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, mimeType);

    // Avoid setting KEY_FRAME_RATE on Lollipop and earlier to reduce compatibility risk
    if (sdk_ver >= Build_VERSION_CODES_M) {
        // We use prefs.fps instead of redrawRate here because the low latency hack in Game.java
        // may leave us with an odd redrawRate value like 59 or 49 which might cause the decoder
        // to puke. To be safe, we'll use the unmodified value.
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, fps);
    }

    // Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
    // so we don't fill these pre-KitKat
    if (/* adaptivePlayback &&  */ sdk_ver >= Build_VERSION_CODES_KITKAT) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
    }

    if (sdk_ver >= Build_VERSION_CODES_R && lowLatency) {
        AMediaFormat_setInt32(videoFormat, "latency", lowLatency);
    }
    else if (sdk_ver >= Build_VERSION_CODES_M) {
//        // Set the Qualcomm vendor low latency extension if the Android R option is unavailable
//        if (MediaCodecHelper.decoderSupportsQcomVendorLowLatency(selectedDecoderName)) {
//            // MediaCodec supports vendor-defined format keys using the "vendor.<extension name>.<parameter name>" syntax.
//            // These allow access to functionality that is not exposed through documented MediaFormat.KEY_* values.
//            // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/common/inc/vidc_vendor_extensions.h;l=67
//            //
//            // Examples of Qualcomm's vendor extensions for Snapdragon 845:
//            // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp
//            // https://cs.android.com/android/_/android/platform/hardware/qcom/sm8150/media/+/0621ceb1c1b19564999db8293574a0e12952ff6c
//            videoFormat.setInteger("vendor.qti-ext-dec-low-latency.enable", 1);
//        }
//
//        if (MediaCodecHelper.decoderSupportsMaxOperatingRate(selectedDecoderName)) {
//            videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
//        }
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);

    media_status_t status = AMediaCodec_configure(codec, videoFormat, window, 0, 0);
    if (status != 0)
    {
        LOGD("AMediaCodec_configure() failed with error %i for format %u", (int)status, 21);
        return 0;
    }

//    videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
//    if (USE_FRAME_RENDER_TIME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//        videoDecoder.setOnFrameRenderedListener(new MediaCodec.OnFrameRenderedListener() {
//            @Override
//            public void onFrameRendered(MediaCodec mediaCodec, long presentationTimeUs, long renderTimeNanos) {
//                long delta = (renderTimeNanos / 1000000L) - (presentationTimeUs / 1000);
//                if (delta >= 0 && delta < 1000) {
//                    if (USE_FRAME_RENDER_TIME) {
//                        activeWindowVideoStats.totalTimeMs += delta;
//                    }
//                }
//            }
//        }, null);
//    }
//
//    LimeLog.info("Using codec "+selectedDecoderName+" for hardware decoding "+mimeType);
//
//    // Start the decoder
//    videoDecoder.start();
//
//    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//        legacyInputBuffers = videoDecoder.getInputBuffers();
//    }

    VideoDecoder* videoDeoder = (VideoDecoder*)malloc(sizeof(VideoDecoder));
    videoDeoder->window = window;
    videoDeoder->codec = codec;
    videoDeoder->threadInfo = 0;

    return videoDeoder;
}

void VideoDecoder_release(VideoDecoder* videoDeoder) {

    // 停止
    VideoDecoder_stop(videoDeoder);

    AMediaCodec_delete(videoDeoder->codec);
    free(videoDeoder);

    LOGD("VideoDecoder_release: released!");
}

pthread_t pid;

void* rendering_thread(ThreadInfo* info)
{
    while(!info->stop) {

        LOGD("rendering_thread: Rendering ...");

        sleep(1);
    }

    // 释放info
    free(info);

    LOGD("rendering_thread: Thread quited!");
}

void VideoDecoder_start(VideoDecoder* videoDeodec) {

    if (AMediaCodec_start(videoDeodec->codec) != AMEDIA_OK) {
        LOGD("AMediaCodec_start: Could not start encoder.");
    }
    else {
        LOGD("AMediaCodec_start: encoder successfully started");
    }

    // 启动线程
    ThreadInfo* info = (ThreadInfo*)malloc(sizeof(ThreadInfo));
    pthread_create(&pid, 0, rendering_thread, info);

    videoDeodec->threadInfo = info;
}

void VideoDecoder_stop(VideoDecoder* videoDeoder) {

    videoDeoder->threadInfo->stop = 1;
}

int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDeoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs) {

    LOGD("VideoDecoder_submitDecodeUnit: submit %p", decodeUnitData);

    int codecFlags = 0;

    // H264 SPS
//    if (((char*)decodeUnitData)[4] == 0x67) {
//
//    }
    return 0;
}