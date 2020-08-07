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
        LOGD("sdk_version fail!");
    }
    return sdk_ver;
}

// 获取空的输入缓冲区
bool getEmptyInputBuffer(VideoDecoder* videoDeoder, VideoInputBuffer* inputBuffer) {

    int bufidx = AMediaCodec_dequeueInputBuffer(videoDeoder->codec, 0);
    if (bufidx < 0) {
        return 0;
    }

    size_t bufsize;
    void* buf = AMediaCodec_getInputBuffer(videoDeoder->codec, bufidx, &bufsize);
    if (buf == NULL) {
        return false;
    }

    inputBuffer->index = bufidx;
    inputBuffer->buffer = buf;
    inputBuffer->bufsize = bufsize;

    return 1;
}

const int InputBufferMaxSize = 1;

void makeInputBuffer(VideoDecoder* videoDeoder) {
    int count = 0;
    int size = sizeof(videoDeoder->inputBufferCache)/sizeof(videoDeoder->inputBufferCache[0]);
    for (int i = 0; i < size && count < InputBufferMaxSize; i++) {
        VideoInputBuffer* inputBuffer = &videoDeoder->inputBufferCache[i];
        if (inputBuffer->isFree) {
            getEmptyInputBuffer(videoDeoder, inputBuffer);
            inputBuffer->isFree = false;
        }
        
        count ++;
    }
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
    videoDeoder->stop = false;
    videoDeoder->stopCallback = 0;
    pthread_mutex_init(&videoDeoder->lock, 0);

    videoDeoder->inputBufferCache = malloc(sizeof(VideoInputBuffer)*InputBufferMaxSize);
    for (int i = 0; i < InputBufferMaxSize; i++) {
        VideoInputBuffer inputBuffer = {
            .index = 0,
            .buffer = 0,
            .bufsize = 0,
            .timestampUs = 0,
            .codecFlags = 0,
            .isFree = true
        };
        
        videoDeoder->inputBufferCache[i] = inputBuffer;
    }

    return videoDeoder;
}

void releaseVideoDecoder(VideoDecoder* videoDeoder) {
    AMediaCodec_delete(videoDeoder->codec);

    pthread_mutex_destroy(&videoDeoder->lock);
    free(videoDeoder->inputBufferCache);
    free(videoDeoder);
}

void VideoDecoder_release(VideoDecoder* videoDeoder) {

    pthread_mutex_lock(&videoDeoder->lock);

    // 停止
    if (!videoDeoder->stop) {
        // release at stop
        videoDeoder->stopCallback = releaseVideoDecoder;
        VideoDecoder_stop(videoDeoder);
    } else {
        releaseVideoDecoder(videoDeoder);
    }

    LOGD("VideoDecoder_release: released!");

    pthread_mutex_unlock(&videoDeoder->lock);
}

pthread_t pid;
pthread_mutex_t inputCache_lock;

void* rendering_thread(VideoDecoder* videoDeoder)
{
    while(!videoDeoder->stop) {

        LOGD("rendering_thread: Rendering ...");

        pthread_mutex_lock(&inputCache_lock); {
            int maxCount = 1; // Too much caching doesn't make sense
            // if (videoDeoder->inputBufferCount < maxCount) {
            //     videoDeoder->inputBufferCache.add(getEmptyInputBuffer());
            //     videoDeoder->inputBufferCount ++;
            // }
            
        } pthread_mutex_unlock(&inputCache_lock);

        sleep(1);
    }

    if (videoDeoder->stopCallback) {
        videoDeoder->stopCallback(videoDeoder);
    }

    LOGD("rendering_thread: Thread quited!");
}

void VideoDecoder_start(VideoDecoder* videoDeoder) {

    LOGD("VideoDecoder_start pthread_mutex_lock");
    pthread_mutex_lock(&videoDeoder->lock);

    assert(!videoDeoder->stop);

    if (AMediaCodec_start(videoDeoder->codec) != AMEDIA_OK) {
        LOGD("AMediaCodec_start: Could not start encoder.");
    }
    else {
        LOGD("AMediaCodec_start: encoder successfully started");
    }

    // 启动线程
    pthread_create(&pid, 0, rendering_thread, videoDeoder);    

    pthread_mutex_unlock(&videoDeoder->lock);

    LOGD("VideoDecoder_start pthread_mutex_unlock");
}

void VideoDecoder_stop(VideoDecoder* videoDeoder) {

    videoDeoder->stop = true;
}

int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDeoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs) {

    pthread_mutex_lock(&videoDeoder->lock);

    LOGD("VideoDecoder_submitDecodeUnit: submit %p", decodeUnitData);

    int codecFlags = 0;

    // H264 SPS
//    if (((char*)decodeUnitData)[4] == 0x67) {
//
//    }

    pthread_mutex_unlock(&videoDeoder->lock);

    return 0;
}

