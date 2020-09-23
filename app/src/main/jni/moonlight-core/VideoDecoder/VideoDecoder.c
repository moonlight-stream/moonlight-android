//
// Created by Viktor Pih on 2020/8/6.
//

#include "VideoDecoder.h"
#include "h264bitstream/h264_stream.h"
#include <stdlib.h>
#include <sys/system_properties.h>
#include <pthread.h>
#include <string.h>

#include <media/NdkMediaExtractor.h>
#include <android/log.h>
#include <dlfcn.h>
#include "MediaCodecHelper.h"
#include "libopus/include/opus_types.h"

#define LOG_TAG    "VideoDecoder"
#ifdef LC_DEBUG
#define LOGD(...)  //{__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); /*printCache();*/}
#define LOGT(...)  {__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); /*printCache();*/}
#else
#define LOGD(...) 
#define LOGT(...)  
#endif

// API 28 Support
#define VD_USE_CACHE 1
#define INPUTBUFFER_SUBMIT_IMMEDIATE 1

static const bool USE_FRAME_RENDER_TIME = false;
static const bool FRAME_RENDER_TIME_ONLY = USE_FRAME_RENDER_TIME && false;

static VideoDecoder* currentVideoDecoder = 0;

typedef enum {
    BUFFER_TYPE_SPS = 1,
    BUFFER_TYPE_PPS = 2,
    BUFFER_TYPE_VPS = 3,
};

typedef enum {
    INPUT_BUFFER_STATUS_INVALID,
    INPUT_BUFFER_STATUS_FREE,
    INPUT_BUFFER_STATUS_WORKING,
    INPUT_BUFFER_STATUS_QUEUING,
    INPUT_BUFFER_STATUS_TEMP,
};

typedef enum {
    OUTPUT_BUFFER_STATUS_INVALID,
    OUTPUT_BUFFER_STATUS_WORKING,
};

// buffer index
typedef enum {
    SPS, PPS, VPS,
    __BUFFER_MAX
};

void printBufferHex(void* data, size_t size) {
    char tmp[1024];
    memset(tmp, 0, 1024);

    for (int i=0; i<size; i++) {
        sprintf(tmp, "%s %x", tmp, ((char*)data)[i]);
    }
    LOGT("buffer: %s", tmp);
}

// 获取空的输入缓冲区
bool getEmptyInputBuffer(VideoDecoder* videoDecoder, VideoInputBuffer* inputBuffer) {

    assert(inputBuffer->status == INPUT_BUFFER_STATUS_INVALID);

    int bufidx = AMediaCodec_dequeueInputBuffer(videoDecoder->codec, 0);
    if (bufidx < 0) {
        return false;
    }

    size_t bufsize;
    void* buf = AMediaCodec_getInputBuffer(videoDecoder->codec, bufidx, &bufsize);
    if (buf == 0) {
        assert(!"error");
        return false;
    }

    inputBuffer->index = bufidx;
    inputBuffer->buffer = buf;
    inputBuffer->bufsize = bufsize;
    inputBuffer->timestampUs = 0;
    inputBuffer->codecFlags = 0;
    inputBuffer->status = INPUT_BUFFER_STATUS_FREE;

    return true;
}

const int InputBufferCacheSize = 20;
const int OutputBufferCacheSize = 20;

int _dequeueInputBuffer(VideoDecoder* videoDecoder) {

    int index = -1;

#if VD_USE_CACHE
    // get one
    pthread_mutex_lock(&videoDecoder->inputCacheLock); {

        for (int i = 0; i < InputBufferCacheSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) {
                inputBuffer->status = INPUT_BUFFER_STATUS_WORKING;
                index = i;
                break;
            }
        }

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);
    if (index == -1) {
        LOGT("[test] 没有空闲的input buffer");
    } else {
        LOGT("[test] 拿到空闲的input buffer");
    }
#else
    while (index < 0 && !videoDecoder->stopping) {
        index = AMediaCodec_dequeueInputBuffer(videoDecoder->codec, 10000);
    }
#endif

    return index;
}

static inline void* _getInputBuffer(VideoDecoder* videoDecoder, int index, size_t* bufsize) {

#if VD_USE_CACHE
    VideoInputBuffer* inputBuffer;
    pthread_mutex_lock(&videoDecoder->inputCacheLock); {

        inputBuffer = &videoDecoder->inputBufferCache[index];
        if (inputBuffer->status == INPUT_BUFFER_STATUS_WORKING) {
            LOGD("VideoDecoder_getInputBuffer index [%d]%d", index, inputBuffer->index);
        } else {
            LOGD("VideoDecoder_getInputBuffer error index %d", index);
            inputBuffer = 0;
        }

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);

    void* buf = 0;

    if (inputBuffer) {
        buf = inputBuffer->buffer;
        *bufsize = inputBuffer->bufsize;
    } else {
        // buf = AMediaCodec_getInputBuffer(videoDecoder->codec, index, bufsize);
        // if (buf == 0) {
        //     assert(!"error");
        //     return 0;
        // }
    }
    return buf;
#else
    return AMediaCodec_getInputBuffer(videoDecoder->codec, index, bufsize);
#endif
}

bool _queueInputBuffer2(VideoDecoder* videoDecoder, int index, size_t bufsize, uint64_t timestampUs, uint32_t codecFlags) {

#if VD_USE_CACHE

    pthread_mutex_lock(&videoDecoder->inputCacheLock); {

        // add to list
        VideoInputBuffer *inputBuffer = &videoDecoder->inputBufferCache[index];
        if (inputBuffer->status != INPUT_BUFFER_STATUS_TEMP) {
            assert(inputBuffer->status == INPUT_BUFFER_STATUS_WORKING);
        }

        inputBuffer->timestampUs = timestampUs;
        inputBuffer->codecFlags = codecFlags;
        inputBuffer->bufsize = bufsize;

        inputBuffer->status = INPUT_BUFFER_STATUS_QUEUING;

        LOGT("[test] Push to codec [%d]%d buffer %p", index, inputBuffer->index, inputBuffer->buffer);

        // 立即提交
#if INPUTBUFFER_SUBMIT_IMMEDIATE
        index = inputBuffer->index;
        inputBuffer->status = INPUT_BUFFER_STATUS_INVALID;
        inputBuffer->index = -1;
        inputBuffer->buffer = 0;
        inputBuffer->bufsize = 0;
        inputBuffer->codecFlags = 0;
        inputBuffer->timestampUs = 0;
#else
        // or 异步提交 貌似会卡死
        index = -1;
#endif

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);

#endif
    if (index >= 0) {
        // Push to codec
        AMediaCodec_queueInputBuffer(videoDecoder->codec, index, 0, bufsize, timestampUs,
                                    codecFlags);

        // LOGT("[test] Push to codec %d ok", index);
    } else {
        sem_post(&videoDecoder->queuing_sem);
    }


    return true;
}

int dequeueOutputBuffer(VideoDecoder* videoDecoder, AMediaCodecBufferInfo *info, int64_t timeoutUs) {

    int outputIndex = -1;

#if VD_USE_CACHE

    const bool drop_enabled = true; // 丢弃多余的帧

    long start_time = getTimeUsec();
    long usTimeout = timeoutUs;//1000000 / videoDecoder->refreshRate;
    bool exit = false;
    while(!exit) {
        pthread_mutex_lock(&videoDecoder->outputCacheLock); {

            int validCount = 0;
            VideoOutputBuffer* minBuffer = 0;
            VideoOutputBuffer* maxBuffer = 0;
            
            for (int i = 0; i < OutputBufferCacheSize; i++) {
                VideoOutputBuffer* outputBuffer = &videoDecoder->outputBufferCache[i];
                if (outputBuffer->status == OUTPUT_BUFFER_STATUS_WORKING) {

                    int64_t presentationTimeUs = outputBuffer->bufferInfo.presentationTimeUs;

                    if (minBuffer == 0) {
                        minBuffer = outputBuffer;
                    } else if (presentationTimeUs < minBuffer->bufferInfo.presentationTimeUs) {
                        minBuffer = outputBuffer;
                    }

                    if (maxBuffer == 0) {
                        maxBuffer = outputBuffer;
                    } else if (presentationTimeUs > maxBuffer->bufferInfo.presentationTimeUs) {
                        maxBuffer = outputBuffer;
                    }

                    // 丢弃，直接导致界面显示的解码时间边长
                    if (drop_enabled && minBuffer != maxBuffer && outputBuffer != minBuffer && outputBuffer != maxBuffer) {
                        AMediaCodec_releaseOutputBuffer(videoDecoder->codec, outputBuffer->index, false);
                        LOGT("[test] drop")
                    } else {
                        validCount ++;
                    }
                }
            }

            if (minBuffer) {
                long currentTime = getTimeUsec();
                bool isTimeout = (currentTime - start_time) >= usTimeout;
                if (validCount >= 2 || isTimeout) {
                    minBuffer->status = OUTPUT_BUFFER_STATUS_INVALID;
                    outputIndex = minBuffer->index;
                    *info = minBuffer->bufferInfo;
                    exit = true;
                }
            }

        } pthread_mutex_unlock(&videoDecoder->outputCacheLock);

        if (!exit)
            usleep(1000);
    }

#else
    outputIndex = AMediaCodec_dequeueOutputBuffer(videoDecoder->codec, &info, timeoutUs); // -1 to block test
#endif

    return outputIndex;
}

#if VD_USE_CACHE
// static
void OnInputAvailableCB(
        AMediaCodec *  aMediaCodec ,
        void *userdata,
        int32_t index) {
    LOGT("OnInputAvailableCB: index(%d)", index);

    VideoDecoder* videoDecoder = (VideoDecoder*)userdata;
    pthread_mutex_lock(&videoDecoder->inputCacheLock); {
        for (int i = 0; i < InputBufferCacheSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_INVALID) {
                inputBuffer->index  = index;
                inputBuffer->status = INPUT_BUFFER_STATUS_FREE;

                inputBuffer->buffer = AMediaCodec_getInputBuffer(videoDecoder->codec, index, &inputBuffer->bufsize);

                break;
            }
        }

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);

//    sp<AMessage> msg = sp<AMessage>((AMessage *)userdata)->dup();
//    msg->setInt32("callbackID", CB_INPUT_AVAILABLE);
//    msg->setInt32("index", index);
//    msg->post();
}
// static
void OnOutputAvailableCB(
        AMediaCodec *  aMediaCodec ,
        void *userdata,
        int32_t index,
        AMediaCodecBufferInfo *bufferInfo) {
    LOGT("OnOutputAvailableCB: index(%d), (%d, %d, %lld, 0x%x)",
          index, bufferInfo->offset, bufferInfo->size,
          (long long)bufferInfo->presentationTimeUs, bufferInfo->flags);

    VideoDecoder* videoDecoder = (VideoDecoder*)userdata;
    pthread_mutex_lock(&videoDecoder->outputCacheLock); {
        for (int i = 0; i < OutputBufferCacheSize; i++) {
            VideoOutputBuffer* outputBuffer = &videoDecoder->outputBufferCache[i];
            if (outputBuffer->status == OUTPUT_BUFFER_STATUS_INVALID) {
                outputBuffer->status = OUTPUT_BUFFER_STATUS_WORKING;
                outputBuffer->index = index;
                outputBuffer->bufferInfo = *bufferInfo;
                break;
            }
        }

    } pthread_mutex_unlock(&videoDecoder->outputCacheLock);

    // 解码完成
    videoDecoder->activeWindowVideoStats.totalFramesRendered ++;
    // Add delta time to the totals (excluding probable outliers)
    long delta = (getTimeUsec() - bufferInfo->presentationTimeUs) / 1000;
    if (delta >= 0 && delta < 1000) {
        videoDecoder->activeWindowVideoStats.decoderTimeMs += delta;
        if (!USE_FRAME_RENDER_TIME) {
            videoDecoder->activeWindowVideoStats.totalTimeMs += delta;
        }
    }

    sem_post(&videoDecoder->rendering_sem);

//    sp<AMessage> msg = sp<AMessage>((AMessage *)userdata)->dup();
//    msg->setInt32("callbackID", CB_OUTPUT_AVAILABLE);
//    msg->setInt32("index", index);
//    msg->setSize("offset", (size_t)(bufferInfo->offset));
//    msg->setSize("size", (size_t)(bufferInfo->size));
//    msg->setInt64("timeUs", bufferInfo->presentationTimeUs);
//    msg->setInt32("flags", (int32_t)(bufferInfo->flags));
//    msg->post();
}
// static
void OnFormatChangedCB(
        AMediaCodec *  aMediaCodec ,
        void *userdata,
        AMediaFormat *format) {
//    sp<AMediaFormatWrapper> formatWrapper = new AMediaFormatWrapper(format);
//    sp<AMessage> outputFormat = formatWrapper->toAMessage();
//    ALOGV("OnFormatChangedCB: format(%s)", outputFormat->debugString().c_str());
//    sp<AMessage> msg = sp<AMessage>((AMessage *)userdata)->dup();
//    msg->setInt32("callbackID", CB_OUTPUT_FORMAT_CHANGED);
//    msg->setMessage("format", outputFormat);
//    msg->post();
}
// static
void OnErrorCB(
        AMediaCodec *  aMediaCodec ,
        void *userdata,
        media_status_t err,
        int32_t actionCode,
        const char *detail) {
    LOGT("OnErrorCB: err(%d), actionCode(%d), detail(%s)", err, actionCode, detail);

}
#endif

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* decoderName, const char* mimeType, int width, int height, int refreshRate, int prefsFps, bool lowLatency, bool adaptivePlayback, bool maxOperatingRate) {

    // Codecs have been known to throw all sorts of crazy runtime exceptions
    // due to implementation problems
    AMediaCodec* codec = AMediaCodec_createDecoderByType(mimeType);//AMediaCodec_createCodecByName(decoderName);

    AMediaFormat* videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, mimeType);

//    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_IS_SYNC_FRAME, 0);

    // Avoid setting KEY_FRAME_RATE on Lollipop and earlier to reduce compatibility risk
    if (Build_VERSION_SDK_INT >= Build_VERSION_CODES_M) {
        // We use prefs.fps instead of redrawRate here because the low latency hack in Game.java
        // may leave us with an odd redrawRate value like 59 or 49 which might cause the decoder
        // to puke. To be safe, we'll use the unmodified value.
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, prefsFps);
    }

    // Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
    // so we don't fill these pre-KitKat
    if (adaptivePlayback &&  Build_VERSION_SDK_INT >= Build_VERSION_CODES_KITKAT) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
    }

    if (Build_VERSION_SDK_INT >= Build_VERSION_CODES_R && lowLatency) {
        AMediaFormat_setInt32(videoFormat, "latency", 0);
    }
    else if (Build_VERSION_SDK_INT >= Build_VERSION_CODES_M) {
        // Set the Qualcomm vendor low latency extension if the Android R option is unavailable
        if (MediaCodecHelper_decoderSupportsQcomVendorLowLatency(decoderName)) {
            // MediaCodec supports vendor-defined format keys using the "vendor.<extension name>.<parameter name>" syntax.
            // These allow access to functionality that is not exposed through documented MediaFormat.KEY_* values.
            // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/common/inc/vidc_vendor_extensions.h;l=67
            //
            // Examples of Qualcomm's vendor extensions for Snapdragon 845:
            // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp
            // https://cs.android.com/android/_/android/platform/hardware/qcom/sm8150/media/+/0621ceb1c1b19564999db8293574a0e12952ff6c
            AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-dec-low-latency.enable", 1);
        }

        if (maxOperatingRate) {
            AMediaFormat_setInt32(videoFormat, "operating-rate", 32767); // Short.MAX_VALUE
        }
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);

    media_status_t status = AMediaCodec_configure(codec, videoFormat, window, 0, 0);
    if (status != 0)
    {
        LOGD("AMediaCodec_configure() failed with error %i for format %u", (int)status, 21);
        return 0;
    }

    const char* string = AMediaFormat_toString(videoFormat);
    LOGT("videoFormat %s", string);

    VideoDecoder* videoDecoder = (VideoDecoder*)malloc(sizeof(VideoDecoder));
    videoDecoder->window = window;
    videoDecoder->codec = codec;
    videoDecoder->decoderName = malloc(strlen(decoderName)+1);
    strcpy(videoDecoder->decoderName, decoderName);
    videoDecoder->initialWidth = width;
    videoDecoder->initialHeight = height;
    videoDecoder->refreshRate = refreshRate;

    videoDecoder->stopping = false;
    videoDecoder->stopCallback = 0;
    pthread_mutex_init(&videoDecoder->lock, 0);
    pthread_mutex_init(&videoDecoder->inputCacheLock, 0);
    pthread_mutex_init(&videoDecoder->outputCacheLock, 0);
    
    sem_init(&videoDecoder->rendering_sem, 0, 0);

    // Initialize
    for (int i = 0; i < __BUFFER_MAX; i++) {
        FrameBuffer framebuffer;
        framebuffer.data = 0;
        framebuffer.size = 0;
        videoDecoder->buffers[i] = framebuffer;
    }

    videoDecoder->legacyFrameDropRendering = false;

    videoDecoder->adaptivePlayback = adaptivePlayback;
    videoDecoder->needsBaselineSpsHack = false;
    videoDecoder->constrainedHighProfile = false;
    videoDecoder->refFrameInvalidationActive = false;

    videoDecoder->infoBuffer = malloc(1024);

    videoDecoder->inputBufferCache = malloc(sizeof(VideoInputBuffer)*InputBufferCacheSize);
    for (int i = 0; i < InputBufferCacheSize; i++) {
        VideoInputBuffer inputBuffer = {
                .index = -1,
                .buffer = 0,
                .bufsize = 0,
                .timestampUs = 0,
                .codecFlags = 0,
                .status = INPUT_BUFFER_STATUS_INVALID
        };

        videoDecoder->inputBufferCache[i] = inputBuffer;
    }

    videoDecoder->outputBufferCache = malloc(sizeof(VideoOutputBuffer)*OutputBufferCacheSize);
    for (int i = 0; i < OutputBufferCacheSize; i++) {
        VideoOutputBuffer outputBuffer = {
                .index = -1,
                .bufferInfo = 0,
                .status = OUTPUT_BUFFER_STATUS_INVALID
        };

        videoDecoder->outputBufferCache[i] = outputBuffer;
    }

    return videoDecoder;
}

void releaseVideoDecoder(VideoDecoder* videoDecoder) {
    AMediaCodec_delete(videoDecoder->codec);
    ANativeWindow_release(videoDecoder->window);

    pthread_mutex_destroy(&videoDecoder->lock);
    pthread_mutex_destroy(&videoDecoder->inputCacheLock);
    pthread_mutex_destroy(&videoDecoder->outputCacheLock);

    sem_destroy(&videoDecoder->rendering_sem);

    free(videoDecoder->inputBufferCache);

    for (int i = 0; i < __BUFFER_MAX; i++) {
        FrameBuffer* framebuffer = &videoDecoder->buffers[i];
        if (framebuffer->data != 0)
            free(framebuffer->data);
    }

    if (videoDecoder->decoderName)
        free(videoDecoder->decoderName);

    free(videoDecoder->infoBuffer);

    free(videoDecoder);

    LOGD("VideoDecoder_release: released!");
}

void VideoDecoder_setLegacyFrameDropRendering(VideoDecoder* videoDecoder, bool enabled) {
    videoDecoder->legacyFrameDropRendering = enabled;
}

void VideoDecoder_release(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&videoDecoder->lock);

    // 停止
    if (!videoDecoder->stopping) {
        // release at stop
        videoDecoder->stopCallback = releaseVideoDecoder;
        VideoDecoder_stop(videoDecoder);
    } else {
        releaseVideoDecoder(videoDecoder);
    }

    pthread_mutex_unlock(&videoDecoder->lock);
}

void* queuing_thread(VideoDecoder* videoDecoder) {

    while(!videoDecoder->stopping) {

        sem_wait(&videoDecoder->queuing_sem);
        if (videoDecoder->stopping) break;

        pthread_mutex_lock(&videoDecoder->inputCacheLock); {
            for (int i = 0; i < InputBufferCacheSize; i++) {
                VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
                if (inputBuffer->status == INPUT_BUFFER_STATUS_QUEUING) {

                    // Push to codec
                    AMediaCodec_queueInputBuffer(videoDecoder->codec, inputBuffer->index, 0, inputBuffer->bufsize, inputBuffer->timestampUs,
                                                    inputBuffer->codecFlags);

                    LOGT("[test] Push to codec %d", inputBuffer->index);

                    inputBuffer->status = INPUT_BUFFER_STATUS_INVALID;
                    inputBuffer->index = -1;
                    inputBuffer->buffer = 0;
                    inputBuffer->bufsize = 0;
                    inputBuffer->codecFlags = 0;
                    inputBuffer->timestampUs = 0;
                }
            }
        } pthread_mutex_unlock(&videoDecoder->inputCacheLock);
    }

    return 0;
}

void* rendering_thread(VideoDecoder* videoDecoder)
{
    // Try to output a frame
    AMediaCodecBufferInfo info;

    long usTimeout = 1000000 / videoDecoder->refreshRate;
    long lastRenderingTimeUs = getTimeUsec();

    while(!videoDecoder->stopping) {

//        sem_wait(&videoDecoder->rendering_sem);
        // if (videoDecoder->stopping) break;

        int outIndex = dequeueOutputBuffer(videoDecoder, &info, usTimeout); // -1 to block test
        if (outIndex >= 0) {

            long presentationTimeUs = info.presentationTimeUs;
            int lastIndex = outIndex;

#ifdef LC_DEBUG
            static long prevRenderingTime = 0;
            long start_time = getTimeUsec();
#endif

            // Skip frame if need
//            VideoStats lastTwo = {0};
//            VideoStats_add(&lastTwo, &videoDecoder->lastWindowVideoStats);
//            VideoStats_add(&lastTwo, &videoDecoder->activeWindowVideoStats);
//            VideoStatsFps fps = VideoStats_getFps(&lastTwo);
//            if (fps.renderedFps < fps.receivedFps)
//            {
//                while ((outIndex = dequeueOutputBuffer(videoDecoder, &info, 0)) >= 0) {
//                    AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, false);
//                    lastIndex = outIndex;
//                    presentationTimeUs = info.presentationTimeUs;
//                }
//            }

            if (videoDecoder->legacyFrameDropRendering) {
                AMediaCodec_releaseOutputBufferAtTime(videoDecoder->codec, lastIndex, getTimeNanc());
            } else {
                AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, true);
            }

#ifdef LC_DEBUG
            LOGT("[test] - 渲染: [%d] %ld 间隔 %d ms 解码用时 %d ms", lastIndex, presentationTimeUs/1000, (start_time - prevRenderingTime)/1000, delta);
            prevRenderingTime = start_time;
#endif
//            usleep(1000000 / videoDecoder->refreshRate);
            
        } else {

            #define INFO_OUTPUT_BUFFERS_CHANGED -3
            #define INFO_OUTPUT_FORMAT_CHANGED -2
            #define INFO_TRY_AGAIN_LATER -1

            switch (outIndex) {
                case INFO_TRY_AGAIN_LATER:
                    LOGD("try again later");
                    break;
                case INFO_OUTPUT_FORMAT_CHANGED:
                // {
                    LOGD("Output format changed");
                    //outputFormat = videoDecoder.getOutputFormat();
                    AMediaFormat* videoFormat = AMediaCodec_getOutputFormat(videoDecoder->codec);
                    const char* string = AMediaFormat_toString(videoFormat);
                    LOGD("New output format: %s", string);
                    break;
                // }
                default:
                    break;
            }

            LOGD("rendering_thread: Rendering pass %d", outIndex);

//            usleep(1000);
        }

        long currentTimeUs = getTimeUsec();
        long renderingTimeUs = currentTimeUs - lastRenderingTimeUs; // 渲染时间

        usTimeout = 1000000 / videoDecoder->refreshRate;
        if (renderingTimeUs > usTimeout)
            usTimeout = usTimeout * 2 - renderingTimeUs;
        
        LOGT("[test] usTimeout %d %d", usTimeout, renderingTimeUs);
        lastRenderingTimeUs = currentTimeUs;
    }
    
    if (videoDecoder->stopCallback) {
        videoDecoder->stopCallback(videoDecoder);
    }

    LOGT("rendering_thread: Thread quited!");

    return 0;
}

void VideoDecoder_start(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&videoDecoder->lock);

    assert(!videoDecoder->stopping);

#if VD_USE_CACHE
    struct AMediaCodecOnAsyncNotifyCallback aCB = {
            OnInputAvailableCB,
            OnOutputAvailableCB,
            OnFormatChangedCB,
            OnErrorCB
    };
    AMediaCodec_setAsyncNotifyCallback(videoDecoder->codec, aCB, videoDecoder);
#endif

    // Init
    videoDecoder->lastFrameNumber = 0;
    videoDecoder->lastTimestampUs = 0;

    videoDecoder->numSpsIn = 0;
    videoDecoder->numVpsIn = 0;
    videoDecoder->numSpsIn = 0;
    videoDecoder->submittedCsd = false;

    VideoStats initStats = {0};
    videoDecoder->activeWindowVideoStats = initStats;
    videoDecoder->lastWindowVideoStats = initStats;
    videoDecoder->globalVideoStats = initStats;

    // Start thread
    pthread_t pid;
    pthread_attr_t attr;
    pthread_attr_init(&attr);

    pthread_create(&pid, &attr, rendering_thread, videoDecoder);
#if !INPUTBUFFER_SUBMIT_IMMEDIATE
    pthread_create(&pid, &attr, queuing_thread, videoDecoder);
#endif

    // Set current
    currentVideoDecoder = videoDecoder;

    if (AMediaCodec_start(videoDecoder->codec) != AMEDIA_OK) {
        LOGD("AMediaCodec_start: Could not start encoder.");
    }
    else {
        LOGD("AMediaCodec_start: encoder successfully started");
    }

    pthread_mutex_unlock(&videoDecoder->lock);
}

void VideoDecoder_stop(VideoDecoder* videoDecoder) {

    videoDecoder->stopping = true;
    sem_post(&videoDecoder->queuing_sem);
    sem_post(&videoDecoder->rendering_sem);
}

typedef enum {
    DR_OK = 0,
    DR_NEED_IDR = -1
};

#define BUFFER_FLAG_CODEC_CONFIG 2

void doProfileSpecificSpsPatching(sps_t* sps, bool constrainedHighProfile) {
    // Some devices benefit from setting constraint flags 4 & 5 to make this Constrained
    // High Profile which allows the decoder to assume there will be no B-frames and
    // reduce delay and buffering accordingly. Some devices (Marvell, Exynos 4) don't
    // like it so we only set them on devices that are confirmed to benefit from it.
    if (sps->profile_idc == 100 && constrainedHighProfile) {
//        LimeLog.info("Setting constraint set flags for constrained high profile");
        sps->constraint_set4_flag = true;
        sps->constraint_set5_flag = true;
    }
    else {
        // Force the constraints unset otherwise (some may be set by default)
        sps->constraint_set4_flag = false;
        sps->constraint_set5_flag = false;
    }
}

bool replaySps(VideoDecoder* videoDecoder) {

    size_t inputBufPos = 0;

    int inputIndex = _dequeueInputBuffer(videoDecoder);
    if (inputIndex < 0) {
        return false;
    }

    size_t bufsize;
    void* inputBuffer = _getInputBuffer(videoDecoder, inputIndex, &bufsize);
    if (inputBuffer == 0) {
        // We're being torn down now
        return false;
    }

    // Write the Annex B header
    // inputBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01, 0x67});
    const int head = 5;
    static const char* header = {0x00, 0x00, 0x00, 0x01, 0x67};
    memcpy(inputBuffer, header, head);

    // Switch the H264 profile back to high
    videoDecoder->savedSps.profile_idc = 100;

    // Patch the SPS constraint flags
    doProfileSpecificSpsPatching(&videoDecoder->savedSps, videoDecoder->constrainedHighProfile);

    // The H264Utils.writeSPS function safely handles
    // Annex B NALUs (including NALUs with escape sequences)
    // Create tmp buffer
    void* tmp_buffer = malloc(128);
    bs_t* tmp_sps_bs = bs_new((uint8_t*)tmp_buffer, 128);

    // Write to tmp buffer
    write_seq_parameter_set_rbsp(&videoDecoder->savedSps, tmp_sps_bs);

    // Copy tmp -> sps buffer
    memcpy(inputBuffer+head, tmp_buffer, 128-head);
    inputBufPos = head + 128;

    free(tmp_buffer);
    bs_free(tmp_sps_bs);

    // No need for the SPS anymore
    // savedSps = null;

    // Queue the new SPS
    // inputBuffer.timestampUs = System.nanoTime() / 1000;
    // inputBuffer.codecFlags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

    return _queueInputBuffer2(videoDecoder, inputIndex, inputBufPos,
            getTimeUsec(),
            BUFFER_FLAG_CODEC_CONFIG);
    // return queueInputBuffer(inputIndex,
    //        0, inputBuffer.position(),
    //        System.nanoTime() / 1000,
    //        MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
    // return queueInputBuffer(inputBuffer);
    // return true;
}

void patchSPS(VideoDecoder* videoDecoder, const uint8_t* data, size_t decodeUnitLength, void* sps_buffer) {
    // const char* data = (*env)->GetDirectBufferAddress(env, buffer);
    // jlong size = (*env)->GetDirectBufferCapacity(env, buffer);
    assert(data[4] == 0x67);

    const int head = 5;

    bs_t* bs = bs_new((uint8_t*)data + head, decodeUnitLength - head);

    sps_t sps;
    read_seq_parameter_set_rbsp(&sps, bs);

    // Some decoders rely on H264 level to decide how many buffers are needed
    // Since we only need one frame buffered, we'll set the level as low as we can
    // for known resolution combinations. Reference frame invalidation may need
    // these, so leave them be for those decoders.
    if (!videoDecoder->refFrameInvalidationActive) {
        if (videoDecoder->initialWidth <= 720 && videoDecoder->initialHeight <= 480 && videoDecoder->refreshRate <= 60) {
            // Max 5 buffered frames at 720x480x60
            LOGD("Patching level_idc to 31");
            sps.level_idc = 31;
        }
        else if (videoDecoder->initialWidth <= 1280 && videoDecoder->initialHeight <= 720 && videoDecoder->refreshRate <= 60) {
            // Max 5 buffered frames at 1280x720x60
            LOGD("Patching level_idc to 32");
            sps.level_idc = 32;
        }
        else if (videoDecoder->initialWidth <= 1920 && videoDecoder->initialHeight <= 1080 && videoDecoder->refreshRate <= 60) {
            // Max 4 buffered frames at 1920x1080x64
            LOGD("Patching level_idc to 42");
            sps.level_idc = 42;
        }
        else {
            // Leave the profile alone (currently 5.0)
        }
    }

    // TI OMAP4 requires a reference frame count of 1 to decode successfully. Exynos 4
    // also requires this fixup.
    //
    // I'm doing this fixup for all devices because I haven't seen any devices that
    // this causes issues for. At worst, it seems to do nothing and at best it fixes
    // issues with video lag, hangs, and crashes.
    //
    // It does break reference frame invalidation, so we will not do that for decoders
    // where we've enabled reference frame invalidation.
    if (!videoDecoder->refFrameInvalidationActive) {
        LOGD("Patching num_ref_frames in SPS");
        sps.num_ref_frames = 1;
    }

    // GFE 2.5.11 changed the SPS to add additional extensions
    // Some devices don't like these so we remove them here on old devices.
    if (Build_VERSION_SDK_INT < Build_VERSION_CODES_O) {
        sps.vui.video_signal_type_present_flag = false;
        sps.vui.colour_description_present_flag = false;
        sps.vui.chroma_loc_info_present_flag = false;
    }

    // Some older devices used to choke on a bitstream restrictions, so we won't provide them
    // unless explicitly whitelisted. For newer devices, leave the bitstream restrictions present.
    if (videoDecoder->needsSpsBitstreamFixup || videoDecoder->isExynos4 || Build_VERSION_SDK_INT >= Build_VERSION_CODES_O) {
        // The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
        // or max_dec_frame_buffering which increases decoding latency on Tegra.

        // GFE 2.5.11 started sending bitstream restrictions
        if (sps.vui.bitstream_restriction_flag == 0) {
            LOGD("Adding bitstream restrictions");
//        sps.vuiParams.bitstreamRestriction = new VUIParameters.BitstreamRestriction();
            sps.vui.motion_vectors_over_pic_boundaries_flag = true;
            sps.vui.log2_max_mv_length_horizontal = 16;
            sps.vui.log2_max_mv_length_vertical = 16;
            sps.vui.num_reorder_frames = 0;
        } else {
            LOGD("Patching bitstream restrictions");
        }

        // Some devices throw errors if maxDecFrameBuffering < numRefFrames
        sps.vui.max_dec_frame_buffering = sps.num_ref_frames;

        // These values are the defaults for the fields, but they are more aggressive
        // than what GFE sends in 2.5.11, but it doesn't seem to cause picture problems.
        sps.vui.max_bytes_per_pic_denom = 2;
        sps.vui.max_bits_per_mb_denom = 1;

        // log2_max_mv_length_horizontal and log2_max_mv_length_vertical are set to more
        // conservative values by GFE 2.5.11. We'll let those values stand.

    } else {
        // Devices that didn't/couldn't get bitstream restrictions before GFE 2.5.11
        // will continue to not receive them now
        sps.vui.bitstream_restriction_flag = 0;
    }

    // Patch the SPS constraint flags
    doProfileSpecificSpsPatching(&sps, videoDecoder->constrainedHighProfile);

    // If we need to hack this SPS to say we're baseline, do so now
    if (videoDecoder->needsBaselineSpsHack) {
        LOGD("Hacking SPS to baseline");
        sps.profile_idc = 66;
        videoDecoder->savedSps = sps;
    }

    // The H264Utils.writeSPS function safely handles
    // Annex B NALUs (including NALUs with escape sequences)
    // Create tmp buffer
    void* tmp_buffer = malloc(head + decodeUnitLength);
    bs_t* sps_bs = bs_new((uint8_t*)tmp_buffer, head + decodeUnitLength);

    // Write to tmp buffer
    write_seq_parameter_set_rbsp(&sps, sps_bs);

    // Copy tmp -> sps buffer
    memcpy(sps_buffer, data, head);
    memcpy(sps_buffer+head, tmp_buffer, decodeUnitLength-head);

    free(tmp_buffer);
    bs_free(sps_bs);
    bs_free(bs);
}

int bufferIndexInCache(VideoDecoder* videoDecoder, void* buffer) {
    int index = -1;
    pthread_mutex_lock(&videoDecoder->inputCacheLock); {
        for (int i = 0; i < InputBufferCacheSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->buffer == buffer) {
                assert(inputBuffer->status == INPUT_BUFFER_STATUS_TEMP);
                index = i;
                break;
            }
        }

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);
    return index;
}

VideoInputBuffer* findBufferInCache(VideoDecoder* videoDecoder, void* buffer) {
    VideoInputBuffer* findInputBuffer = 0;
    pthread_mutex_lock(&videoDecoder->inputCacheLock); {
        for (int i = 0; i < InputBufferCacheSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->buffer == buffer) {
                findInputBuffer = inputBuffer;
                break;
            }
        }

    } pthread_mutex_unlock(&videoDecoder->inputCacheLock);
    return findInputBuffer;
}

int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs) {

    #define VPS_BUFFER  videoDecoder->buffers[VPS].data
    #define VPS_BUFSIZE videoDecoder->buffers[VPS].size

    #define SPS_BUFFER videoDecoder->buffers[SPS].data
    #define SPS_BUFSIZE videoDecoder->buffers[SPS].size

    #define PPS_BUFFER videoDecoder->buffers[PPS].data
    #define PPS_BUFSIZE videoDecoder->buffers[PPS].size

    #define RETURN(x) {pthread_mutex_unlock(&videoDecoder->lock);return x;}

    if (videoDecoder->stopping) {
        // Don't bother if we're stopping
        return DR_OK;
    }

    pthread_mutex_lock(&videoDecoder->lock);

    // Get long time, not the same as receiveTimeMs
    uint64_t timestampUs = getTimeUsec();
    uint64_t currentTimeMillis = timestampUs / 1000;

    if (videoDecoder->lastFrameNumber == 0) {
        videoDecoder->activeWindowVideoStats.measurementStartTimestamp = currentTimeMillis;
    } else if (frameNumber != videoDecoder->lastFrameNumber && frameNumber != videoDecoder->lastFrameNumber + 1) {
        // We can receive the same "frame" multiple times if it's an IDR frame.
        // In that case, each frame start NALU is submitted independently.
        videoDecoder->activeWindowVideoStats.framesLost += frameNumber - videoDecoder->lastFrameNumber - 1;
        videoDecoder->activeWindowVideoStats.totalFrames += frameNumber - videoDecoder->lastFrameNumber - 1;
        videoDecoder->activeWindowVideoStats.frameLossEvents++;
    }

    videoDecoder->lastFrameNumber = frameNumber;

    // Flip stats windows roughly every second
    if (currentTimeMillis >= videoDecoder->activeWindowVideoStats.measurementStartTimestamp + 1000) {

        VideoStats_add(&videoDecoder->globalVideoStats, &videoDecoder->activeWindowVideoStats);
        VideoStats_copy(&videoDecoder->lastWindowVideoStats, &videoDecoder->activeWindowVideoStats);
        VideoStats_clear(&videoDecoder->activeWindowVideoStats);
        videoDecoder->activeWindowVideoStats.measurementStartTimestamp = currentTimeMillis;
    }

    videoDecoder->activeWindowVideoStats.totalFramesReceived++;
    videoDecoder->activeWindowVideoStats.totalFrames++;

//    LOGT("fuck %d %ld %ld %ld %ld", frameNumber, videoDecoder->activeWindowVideoStats.totalFramesReceived, videoDecoder->activeWindowVideoStats.totalFramesRendered, currentTimeMillis, videoDecoder->activeWindowVideoStats.measurementStartTimestamp);

    LOGD("VideoDecoder_submitDecodeUnit: submit %p", decodeUnitData);

    int inputBufferIndex;
    void* inputBuffer;
    size_t inputBufPos = 0;
    size_t inputBufsize;

    int codecFlags = 0;

    if (!FRAME_RENDER_TIME_ONLY) {
        // Count time from first packet received to decode start
        //videoDecoder->activeWindowVideoStats.totalTimeMs += (timestampUs / 1000) - receiveTimeMs;
        // receiveTimeMs is a clock time
        videoDecoder->activeWindowVideoStats.totalTimeMs += getClockUsec()/1000 - receiveTimeMs;
    }

    if (timestampUs <= videoDecoder->lastTimestampUs) {
        // We can't submit multiple buffers with the same timestamp
        // so bump it up by one before queuing
        timestampUs = videoDecoder->lastTimestampUs + 1;
    }

    long callDif = timestampUs - videoDecoder->lastTimestampUs;

    videoDecoder->lastTimestampUs = timestampUs;

    // H264 SPS
    if (((char*)decodeUnitData)[4] == 0x67) {
        videoDecoder->numSpsIn ++;
        
        if (SPS_BUFFER) free(SPS_BUFFER);

        SPS_BUFFER = malloc(decodeUnitLength);
        SPS_BUFSIZE = decodeUnitLength;

        // java版本这里会+1长度，写入一个高位字节，存为 0x80。我这里并未实现该功能，似乎也没问题
        patchSPS(videoDecoder, decodeUnitData, decodeUnitLength, SPS_BUFFER);

#ifdef LC_DEBUG
        printBufferHex(decodeUnitData, SPS_BUFSIZE);
        printBufferHex(SPS_BUFFER, SPS_BUFSIZE);
#endif
        
        RETURN(DR_OK);
    } else if (decodeUnitType == BUFFER_TYPE_VPS) {
        videoDecoder->numVpsIn++;

        // Batch this to submit together with SPS and PPS per AOSP docs
        if (VPS_BUFFER) free(VPS_BUFFER);

        VPS_BUFFER = malloc(decodeUnitLength);
        memcpy(VPS_BUFFER, decodeUnitData, decodeUnitLength);
        VPS_BUFSIZE = decodeUnitLength;
        RETURN(DR_OK);
    } else if (decodeUnitType == BUFFER_TYPE_SPS) {
        videoDecoder->numSpsIn++;

        // Batch this to submit together with VPS and PPS per AOSP docs        
        if (SPS_BUFFER) free(SPS_BUFFER);

        SPS_BUFFER = malloc(decodeUnitLength);
        memcpy(SPS_BUFFER, decodeUnitData, decodeUnitLength);
        SPS_BUFSIZE = decodeUnitLength;
        RETURN(DR_OK);
    } else if (decodeUnitType == BUFFER_TYPE_PPS) {
        videoDecoder->numPpsIn++;

        // If this is the first CSD blob or we aren't supporting
        // adaptive playback, we will submit the CSD blob in a
        // separate input buffer.
        if (!videoDecoder->submittedCsd || !videoDecoder->adaptivePlayback) {
            inputBufferIndex = _dequeueInputBuffer(videoDecoder);
            if (inputBufferIndex < 0) {
                // We're being torn down now
                RETURN(DR_NEED_IDR);
            }

            inputBuffer = _getInputBuffer(videoDecoder, inputBufferIndex, &inputBufsize);
            if (inputBuffer == 0) {
                // We're being torn down now
                RETURN(DR_NEED_IDR);
            }

            // When we get the PPS, submit the VPS and SPS together with
            // the PPS, as required by AOSP docs on use of MediaCodec.
            if (VPS_BUFFER != 0) {
                memcpy(inputBuffer+inputBufPos, VPS_BUFFER, VPS_BUFSIZE);
                inputBufPos += VPS_BUFSIZE;
            }
            if (SPS_BUFFER != 0) {
                memcpy(inputBuffer+inputBufPos, SPS_BUFFER, SPS_BUFSIZE);
                inputBufPos += SPS_BUFSIZE;
            }

            // This is the CSD blob
            codecFlags |= BUFFER_FLAG_CODEC_CONFIG;
        }
        else {
            // Batch this to submit together with the next I-frame
            if (PPS_BUFFER) free(PPS_BUFFER);

            PPS_BUFFER = malloc(decodeUnitLength);
            memcpy(PPS_BUFFER, decodeUnitData, decodeUnitLength);

            // Next call will be I-frame data
            videoDecoder->submitCsdNextCall = true;

            RETURN(DR_OK);
        }

    } else {

        int index = bufferIndexInCache(videoDecoder, decodeUnitData);
        if (index >= 0) {
            inputBufferIndex = index;
            inputBuffer = decodeUnitData;
        } else
        {
            inputBufferIndex = _dequeueInputBuffer(videoDecoder);
            if (inputBufferIndex < 0) {
                // We're being torn down now
                RETURN(DR_NEED_IDR);
            }
            inputBuffer = _getInputBuffer(videoDecoder, inputBufferIndex, &inputBufsize);
            if (inputBuffer == 0) {
                // We're being torn down now
                RETURN(DR_NEED_IDR);
            }
        }


        if (videoDecoder->submitCsdNextCall) {
            assert(inputBufPos == 0);

            if (VPS_BUFFER != 0) {
                memcpy(inputBuffer+inputBufPos, VPS_BUFFER, VPS_BUFSIZE);
                inputBufPos += VPS_BUFSIZE;
            }
            if (SPS_BUFFER != 0) {
                memcpy(inputBuffer+inputBufPos, SPS_BUFFER, SPS_BUFSIZE);
                inputBufPos += SPS_BUFSIZE;
            }
            if (PPS_BUFFER != 0) {
                memcpy(inputBuffer+inputBufPos, PPS_BUFFER, PPS_BUFSIZE);
                inputBufPos += PPS_BUFSIZE;
            }

            videoDecoder->submitCsdNextCall = false;
        }
    }

    if (decodeUnitLength > inputBufsize - inputBufPos) {
        // IllegalArgumentException exception = new IllegalArgumentException(
        //         "Decode unit length "+decodeUnitLength+" too large for input buffer "+inputBuffer.limit());
        // if (!reportedCrash) {
        //     reportedCrash = true;
        //     crashListener.notifyCrash(exception);
        // }
        // throw new RendererException(this, exception);
        assert(!"error");
    }

    // Copy data from our buffer list into the input buffer
    if (inputBuffer+inputBufPos != decodeUnitData) {
        memcpy(inputBuffer+inputBufPos, decodeUnitData, decodeUnitLength);
    } else {
        LOGT("[test] skip copy");
    }
    LOGT("[test] 提交 %p + %d <= %p [%d]", inputBuffer, inputBufPos, decodeUnitData, inputBufferIndex);

    inputBufPos += decodeUnitLength;

//    int delay = 1000000/videoDecoder->refreshRate - callDif;
//    if (delay > 2000) {
//        usleep(delay);
//        LOGT("[test] usleep用时 %d ms", delay/1000);
//    }

    if (!_queueInputBuffer2(videoDecoder, inputBufferIndex, inputBufPos,
            timestampUs, codecFlags)) {
        RETURN(DR_NEED_IDR);
    }

#ifdef LC_DEBUG
    long endTimeMillis = getTimeMsec();
    LOGT("[test] + 提交数据 %ld 间隔 %d ms 提交用时 %d ms", timestampUs/1000, callDif/1000, endTimeMillis-currentTimeMillis);
#endif

    if ((codecFlags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
        videoDecoder->submittedCsd = true;

        if (videoDecoder->needsBaselineSpsHack) {
            videoDecoder->needsBaselineSpsHack = false;

            if (!replaySps(videoDecoder)) {
                RETURN(DR_NEED_IDR);
            }

            LOGD("SPS replay complete");
        }
    }


    RETURN(0);
}

void VideoDecoder_getTempBuffer(void** buffer, size_t* bufsize) {

    VideoDecoder* videoDecoder = currentVideoDecoder;

    pthread_mutex_lock(&videoDecoder->lock); {

        // Return a free input buffer as temp buffer
        pthread_mutex_lock(&videoDecoder->inputCacheLock); {
            for (int i = 0; i < InputBufferCacheSize; i++) {
                VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
                if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) {
                    *buffer = inputBuffer->buffer;
                    *bufsize = inputBuffer->bufsize;
                    inputBuffer->status = INPUT_BUFFER_STATUS_TEMP;

                    LOGT("[test] getTempBuffer [%d]%d", i, inputBuffer->index);

                    break;
                }
            }
        } pthread_mutex_unlock(&videoDecoder->inputCacheLock);

    } pthread_mutex_unlock(&videoDecoder->lock);
}

void VideoDecoder_releaseTempBuffer(void* buffer) {

    VideoDecoder* videoDecoder = currentVideoDecoder;

    pthread_mutex_lock(&videoDecoder->lock); {

        // get a empty input buffer from cache
        pthread_mutex_lock(&videoDecoder->inputCacheLock); {

            int count = 0;

            for (int i = 0; i < InputBufferCacheSize; i++) {
                VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
                if (buffer == inputBuffer->buffer && inputBuffer->status == INPUT_BUFFER_STATUS_TEMP) {
                    inputBuffer->status = INPUT_BUFFER_STATUS_FREE;
                    break;
                }
            }

        } pthread_mutex_unlock(&videoDecoder->inputCacheLock);

    } pthread_mutex_unlock(&videoDecoder->lock);
}


const char* VideoDecoder_formatInfo(VideoDecoder* videoDecoder, const char* format) {
    
    char* tmp[50];
    sprintf(tmp, "%dx%d", videoDecoder->initialWidth, videoDecoder->initialHeight);
    
    VideoStats lastTwo = {0};
    VideoStats_add(&lastTwo, &videoDecoder->lastWindowVideoStats);
    VideoStats_add(&lastTwo, &videoDecoder->activeWindowVideoStats);
    VideoStatsFps fps = VideoStats_getFps(&lastTwo);
    const char* decoder = videoDecoder->decoderName;

    float decodeTimeMs = (float)lastTwo.decoderTimeMs / lastTwo.totalFramesReceived;
    sprintf(videoDecoder->infoBuffer, format, 
                        tmp,
                        decoder,
                        fps.totalFps,
                        fps.receivedFps,
                        fps.renderedFps,
                        (float)lastTwo.framesLost / lastTwo.totalFrames * 100,
                        ((float)lastTwo.totalTimeMs / lastTwo.totalFramesReceived) - decodeTimeMs,
                        decodeTimeMs);
    return videoDecoder->infoBuffer;
}

int VideoDecoder_staticSubmitDecodeUnit(void* decodeUnitData, int decodeUnitLength, int decodeUnitType, int frameNumber, long receiveTimeMs) {

    // currentVideoDecoder
    // assert(currentVideoDecoder);
    if (!currentVideoDecoder)
        return DR_NEED_IDR;

    return VideoDecoder_submitDecodeUnit(currentVideoDecoder, decodeUnitData, decodeUnitLength, decodeUnitType, frameNumber, receiveTimeMs);
}