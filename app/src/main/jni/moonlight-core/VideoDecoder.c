//
// Created by Viktor Pih on 2020/8/6.
//

#include "VideoDecoder.h"
#include <stdlib.h>
#include <sys/time.h>

#include <media/NdkMediaExtractor.h>

#include <sys/system_properties.h>

#include <pthread.h>

#include <android/log.h>

#define LOG_TAG    "VideoDecoder"
#ifdef LC_DEBUG
#define LOGD(...)  //{__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); /*printCache();*/}
#else
#define LOGD(...) 
#endif

#define LOGT(...)  {__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); /*printCache();*/}

typedef enum {
    INPUT_BUFFER_STATUS_INVALID,
    INPUT_BUFFER_STATUS_FREE,
    INPUT_BUFFER_STATUS_WORKING,
    INPUT_BUFFER_STATUS_QUEUING,
    INPUT_BUFFER_STATUS_RENDERING,
};

VideoDecoder* _videoDecoder = 0;
void printCache() {

    if (_videoDecoder) {
        VideoInputBuffer* inputBuffer = &_videoDecoder->inputBufferCache[0];
        //LOGD("cache %d", inputBuffer->status);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "cache %d", inputBuffer->status);
    }
}

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
bool getEmptyInputBuffer(VideoDecoder* videoDecoder, VideoInputBuffer* inputBuffer) {

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

    return true;
}

const int InputBufferMaxSize = 8;
const int InputBufferCacheSize = 8;
pthread_mutex_t inputCache_lock = PTHREAD_MUTEX_INITIALIZER; // lock NDK API access

void makeInputBuffer(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&inputCache_lock); {

        int cacheCount = 0;
        for (int i = 0; i < InputBufferMaxSize; i++) {

            assert(cacheCount <= InputBufferCacheSize);
            if (cacheCount == InputBufferCacheSize) {
                break;
            }

            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_INVALID) {
                if (getEmptyInputBuffer(videoDecoder, inputBuffer)) {
                    inputBuffer->status = INPUT_BUFFER_STATUS_FREE;
                    LOGD("makeInputBuffer create inputBuffer index [%d]%d", i, inputBuffer->index);
                } else {
                    LOGD("makeInputBuffer fail!");
                }
            }
            // count unqueuing
            if (inputBuffer->status != INPUT_BUFFER_STATUS_QUEUING) {
                cacheCount ++;
            }
        }

    } pthread_mutex_unlock(&inputCache_lock);
}
 
void _queueInputBuffer(VideoDecoder* videoDecoder, int index) {

    VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[index];
    assert(inputBuffer->status == INPUT_BUFFER_STATUS_QUEUING);

    // push to codec
    AMediaCodec_queueInputBuffer(videoDecoder->codec, inputBuffer->index, 0, inputBuffer->bufsize, inputBuffer->timestampUs,
                                 inputBuffer->codecFlags);

    LOGD("_queueInputBuffer free index [%d]%d bufsize %d timestampUs %ld codecFlags %d", index, inputBuffer->index, inputBuffer->bufsize, inputBuffer->timestampUs/1000, inputBuffer->codecFlags);
}

void queueInputBuffer(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&inputCache_lock); {

        for (int i = 0; i < InputBufferMaxSize; i++) {
            
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_QUEUING) {
                // Queue one input for each time
                // LOGD("queueInputBuffer: %d", i);
                _queueInputBuffer(videoDecoder, i);
                
                // mark to rendering
                inputBuffer->status = INPUT_BUFFER_STATUS_RENDERING;

                // mark to invalid
                // inputBuffer->status = INPUT_BUFFER_STATUS_INVALID;
                break; // or continue
            }
        }        

    } pthread_mutex_unlock(&inputCache_lock);
}

// helper

// private static boolean isDecoderInList(List<String> decoderList, String decoderName) {
//     if (!initialized) {
//         throw new IllegalStateException("MediaCodecHelper must be initialized before use");
//     }

//     for (String badPrefix : decoderList) {
//         if (decoderName.length() >= badPrefix.length()) {
//             String prefix = decoderName.substring(0, badPrefix.length());
//             if (prefix.equalsIgnoreCase(badPrefix)) {
//                 return true;
//             }
//         }
//     }
    
//     return false;
// }

bool decoderSupportsMaxOperatingRate(const char* decoderName) {
    // Operate at maximum rate to lower latency as much as possible on
    // some Qualcomm platforms. We could also set KEY_PRIORITY to 0 (realtime)
    // but that will actually result in the decoder crashing if it can't satisfy
    // our (ludicrous) operating rate requirement. This seems to cause reliable
    // crashes on the Xiaomi Mi 10 lite 5G and Redmi K30i 5G on Android 10, so
    // we'll disable it on Snapdragon 765G and all non-Qualcomm devices to be safe.
    //
    // NB: Even on Android 10, this optimization still provides significant
    // performance gains on Pixel 2.
    int sdk_ver = sdk_version();
    return sdk_ver >= Build_VERSION_CODES_M;
    // return Build.VERSION.SDK_INT >= Build_VERSION_CODES_M &&
    //         isDecoderInList(qualcommDecoderPrefixes, decoderName) &&
    //         !isAdreno620;
}

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* name, const char* mimeType, int width, int height, int fps, bool lowLatency) {

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
        AMediaFormat_setInt32(videoFormat, "latency", 0);
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
       if (decoderSupportsMaxOperatingRate(name)) {
           AMediaFormat_setInt32(videoFormat, "operating-rate", 32767);
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
    LOGD("videoFormat %s", string);

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

    VideoDecoder* videoDecoder = (VideoDecoder*)malloc(sizeof(VideoDecoder));
    videoDecoder->window = window;
    videoDecoder->codec = codec;
    videoDecoder->stop = false;
    videoDecoder->stopCallback = 0;
    pthread_mutex_init(&videoDecoder->lock, 0);
    sem_init(&videoDecoder->rendering_sem, 0, 0);

    videoDecoder->inputBufferCache = malloc(sizeof(VideoInputBuffer)*InputBufferMaxSize);
    for (int i = 0; i < InputBufferMaxSize; i++) {
        VideoInputBuffer inputBuffer = {
            .index = 0,
            .buffer = 0,
            .bufsize = 0,
            .timestampUs = 0,
            .codecFlags = 0,
            .status = INPUT_BUFFER_STATUS_INVALID
        };
        
        videoDecoder->inputBufferCache[i] = inputBuffer;
    }

    _videoDecoder = videoDecoder;

    return videoDecoder;
}

void releaseVideoDecoder(VideoDecoder* videoDecoder) {
    AMediaCodec_delete(videoDecoder->codec);
    ANativeWindow_release(videoDecoder->window);

    pthread_mutex_destroy(&videoDecoder->lock);
    sem_destroy(&videoDecoder->rendering_sem);

    free(videoDecoder->inputBufferCache);
    free(videoDecoder);

    LOGD("VideoDecoder_release: released!");
}

void VideoDecoder_release(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&videoDecoder->lock);

    // 停止
    if (!videoDecoder->stop) {
        // release at stop
        videoDecoder->stopCallback = releaseVideoDecoder;
        VideoDecoder_stop(videoDecoder);
    } else {
        releaseVideoDecoder(videoDecoder);
    }

    pthread_mutex_unlock(&videoDecoder->lock);
}

pthread_t pid;

long getTimeUsec()
{
    struct timeval t;
    gettimeofday(&t, 0);
    return (long)((long)t.tv_sec * 1000 * 1000 + t.tv_usec);
}

static bool isRendered = false;

void* rendering_thread(VideoDecoder* videoDecoder)
{
    isRendered = false;

    while(!videoDecoder->stop) {

        // Build input buffer cache
        makeInputBuffer(videoDecoder);

        // Waitting for a signal
        sem_wait(&videoDecoder->rendering_sem);
        if (videoDecoder->stop) break;

        // Queue input buffers
        queueInputBuffer(videoDecoder);

        // Try to output a frame
        AMediaCodecBufferInfo info;
        int outIndex = AMediaCodec_dequeueOutputBuffer(videoDecoder->codec, &info, 0); // -1 to block test
        if (outIndex >= 0) {

            isRendered = true;

            long presentationTimeUs = info.presentationTimeUs;
            int lastIndex = outIndex;

#ifdef LC_DEBUG
            static long prevRenderingTime[2] = {0};
            long start_time = getTimeUsec();
#endif

            // Get the last output buffer in the queue
            // while ((outIndex = AMediaCodec_dequeueOutputBuffer(videoDecoder->codec, &info, 0)) >= 0) {
            //     AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, false);
            
            //     lastIndex = outIndex;
            //     presentationTimeUs = info.presentationTimeUs;
            // }

            // mark all rendering frames
            pthread_mutex_lock(&inputCache_lock); {

                for (int i = 0; i < InputBufferMaxSize; i++) {
                
                    VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
                    if (inputBuffer->status == INPUT_BUFFER_STATUS_RENDERING) {
                        inputBuffer->status = INPUT_BUFFER_STATUS_INVALID;
                    }
                }

            } pthread_mutex_unlock(&inputCache_lock);

            

#ifdef LC_DEBUG            
            long currentDelayUs = (start_time - prevRenderingTime[0]);
            long prevDelayUs = (prevRenderingTime[0] - prevRenderingTime[1]);
            int fps = 60;
            float standerDelayUs = 1000000.0 / fps;
            
            int customDelayUs = 16666/2;
            
            int timeoutUs = (currentDelayUs + prevDelayUs) - (standerDelayUs * 2);
            // int timeoutUs = (currentDelayUs + customDelayUs) - (standerDelayUs * 2);
            int releaseDelayUs = customDelayUs-timeoutUs;
            if (releaseDelayUs < 0) releaseDelayUs = 0;
            if (releaseDelayUs > customDelayUs) releaseDelayUs = customDelayUs;

            LOGT("[test] - 渲染: %d ms %d ms %ld %ld deleay %d\n", (getTimeUsec() - start_time) / 1000, (start_time - prevRenderingTime[0])/1000, start_time/1000, presentationTimeUs/1000, releaseDelayUs);
            prevRenderingTime[1] = prevRenderingTime[0];
            prevRenderingTime[0] = start_time;
#endif
            // usleep(releaseDelayUs);
            AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, true);

        //    AMediaCodec_releaseOutputBufferAtTime(videoDecoder->codec, lastIndex, (releaseDelayUs + presentationTimeUs) * 1000);

            LOGD("[fuck] rendering_thread: Rendering ... [%d] flags %d offset %d size %d presentationTimeUs %ld", lastIndex, info.flags, info.offset, info.size, presentationTimeUs/1000);            

            // Queue input buffers
            queueInputBuffer(videoDecoder);

            // Check the incoming frames during rendering
            sem_post(&videoDecoder->rendering_sem);
            
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

            // Wait for the next frame of data
            while (sem_trywait(&videoDecoder->rendering_sem) == 0) {
            }

//            renderingSemaphore.drainPermits();
        }
        
        

//        sleep(1);
    }

    if (videoDecoder->stopCallback) {
        videoDecoder->stopCallback(videoDecoder);
    }

    LOGD("rendering_thread: Thread quited!");
}

void VideoDecoder_start(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&videoDecoder->lock);

    assert(!videoDecoder->stop);

    if (AMediaCodec_start(videoDecoder->codec) != AMEDIA_OK) {
        LOGD("AMediaCodec_start: Could not start encoder.");
    }
    else {
        LOGD("AMediaCodec_start: encoder successfully started");
    }

    // 启动线程
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_create(&pid, &attr, rendering_thread, videoDecoder);

    pthread_mutex_unlock(&videoDecoder->lock);
}

void VideoDecoder_stop(VideoDecoder* videoDecoder) {

    videoDecoder->stop = true;
    sem_post(&videoDecoder->rendering_sem); // make stop signal
}

int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs) {

    pthread_mutex_lock(&videoDecoder->lock);

    LOGD("VideoDecoder_submitDecodeUnit: submit %p", decodeUnitData);

    int codecFlags = 0;

    // H264 SPS
//    if (((char*)decodeUnitData)[4] == 0x67) {
//
//    }

    pthread_mutex_unlock(&videoDecoder->lock);

    return 0;
}

int VideoDecoder_dequeueInputBuffer(VideoDecoder* videoDecoder) {

    int index = -1;
    // get a empty input buffer from cache
    pthread_mutex_lock(&inputCache_lock); {

        for (int i = 0; i < InputBufferMaxSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) {
                inputBuffer->status = INPUT_BUFFER_STATUS_WORKING;
                LOGD("VideoDecoder_getInputBuffer working [%d]", i);
                index = i;
                break;
            }
        }

    } pthread_mutex_unlock(&inputCache_lock);

    LOGD("VideoDecoder_dequeueInputBuffer index [%d]", index);

    return index;
}

VideoInputBuffer* VideoDecoder_getInputBuffer(VideoDecoder* videoDecoder, int index) {

    VideoInputBuffer* inputBuffer;
    pthread_mutex_lock(&videoDecoder->lock); {

        pthread_mutex_lock(&inputCache_lock); {

            inputBuffer = &videoDecoder->inputBufferCache[index];
            if (inputBuffer->status != INPUT_BUFFER_STATUS_WORKING) {
                LOGD("VideoDecoder_getInputBuffer error index %d", index);
                inputBuffer = 0;
            } else {
                LOGD("VideoDecoder_getInputBuffer index [%d]%d", index, inputBuffer->index);
            }
            
        } pthread_mutex_unlock(&inputCache_lock);

    } pthread_mutex_unlock(&videoDecoder->lock);
    
    return inputBuffer;
}

bool VideoDecoder_queueInputBuffer(VideoDecoder* videoDecoder, int index, uint64_t timestampUs, uint32_t codecFlags) {

    pthread_mutex_lock(&videoDecoder->lock); {

        pthread_mutex_lock(&inputCache_lock);
        {
            // add to list
            VideoInputBuffer *inputBuffer = &videoDecoder->inputBufferCache[index];
            assert(inputBuffer->status == INPUT_BUFFER_STATUS_WORKING);

            inputBuffer->timestampUs = timestampUs;
            inputBuffer->codecFlags = codecFlags;

            inputBuffer->status = INPUT_BUFFER_STATUS_QUEUING;

            // send rendering semaphore
            sem_post(&videoDecoder->rendering_sem);

            LOGD("[fuck] post queueInputBuffer: [%d]%d timestampUs %ld codecFlags %d", index, inputBuffer->index, inputBuffer->timestampUs, inputBuffer->codecFlags);

        } pthread_mutex_unlock(&inputCache_lock);

    } pthread_mutex_unlock(&videoDecoder->lock);

    return true;
}

#define SYNC_PUSH 1

bool VideoDecoder_isBusing(VideoDecoder* videoDecoder) {

#if SYNC_PUSH
    bool isBusing = false;
    if (!isRendered) {
        sem_post(&videoDecoder->rendering_sem);
        return false;
    }
    char tmp[512] = {0};
#else
    bool isBusing = true;
#endif
    pthread_mutex_lock(&videoDecoder->lock); {

        // get a empty input buffer from cache
        pthread_mutex_lock(&inputCache_lock); {

            for (int i = 0; i < InputBufferMaxSize; i++) {
                VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];

#if SYNC_PUSH
                sprintf(tmp, "%s %d", tmp, inputBuffer->status);
                if (inputBuffer->status > INPUT_BUFFER_STATUS_FREE) {
                    isBusing = true;
                    // LOGT("[test2] [%d] %d", i, inputBuffer->status);
#else
                if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) { // just any one free buffer
                    // LOGD("VideoDecoder_getInputBuffer working [%d]", i);
                    isBusing = false;
                    break;
#endif
                }
            }
            LOGT("[test] %s", tmp);

        } pthread_mutex_unlock(&inputCache_lock);

        // LOGD("VideoDecoder_dequeueInputBuffer index [%d]", index);

    } pthread_mutex_unlock(&videoDecoder->lock);

    if (isBusing) {
        sem_post(&videoDecoder->rendering_sem);
    }

    return isBusing;
}


int VideoDecoder_dequeueInputBuffer2(VideoDecoder* videoDecoder) {

    int index = -1;

    pthread_mutex_lock(&videoDecoder->lock); {

        // get one
        pthread_mutex_lock(&inputCache_lock); {

            for (int i = 0; i < InputBufferMaxSize; i++) {
            VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
                if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) {
                    inputBuffer->status = INPUT_BUFFER_STATUS_WORKING;
                    LOGD("VideoDecoder_getInputBuffer working [%d]", i);
                    index = i;
                    break;
                }
            }
            
        } pthread_mutex_unlock(&inputCache_lock);

        // if (index == -1) {
        //     while (index < 0 && !videoDecoder->stop) {
        //         index = AMediaCodec_dequeueInputBuffer(videoDecoder->codec, 10000);
        //     }
        // }

    } pthread_mutex_unlock(&videoDecoder->lock);

    LOGD("VideoDecoder_dequeueInputBuffer2 index %d", index);

    return index;
}

void* VideoDecoder_getInputBuffer2(VideoDecoder* videoDecoder, int index, size_t* bufsize) {

    void* buf = 0;

    pthread_mutex_lock(&videoDecoder->lock); {

        VideoInputBuffer* inputBuffer;
        pthread_mutex_lock(&inputCache_lock); {

            inputBuffer = &videoDecoder->inputBufferCache[index];
            if (inputBuffer->status != INPUT_BUFFER_STATUS_WORKING) {
                LOGD("VideoDecoder_getInputBuffer error index %d", index);
                inputBuffer = 0;
            } else {
                LOGD("VideoDecoder_getInputBuffer index [%d]%d", index, inputBuffer->index);
            }
            
        } pthread_mutex_unlock(&inputCache_lock);

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

    } pthread_mutex_unlock(&videoDecoder->lock);

    return buf;
}

bool VideoDecoder_queueInputBuffer2(VideoDecoder* videoDecoder, int index, size_t bufsize, uint64_t timestampUs, uint32_t codecFlags) {

    // bool res;
    pthread_mutex_lock(&videoDecoder->lock); {

        pthread_mutex_lock(&inputCache_lock);
        {
            // add to list
            VideoInputBuffer *inputBuffer = &videoDecoder->inputBufferCache[index];
            assert(inputBuffer->status == INPUT_BUFFER_STATUS_WORKING);

            inputBuffer->timestampUs = timestampUs;
            inputBuffer->codecFlags = codecFlags;
            inputBuffer->bufsize = bufsize;

            inputBuffer->status = INPUT_BUFFER_STATUS_QUEUING;

            // send rendering semaphore
            sem_post(&videoDecoder->rendering_sem);

            LOGD("[fuck] post queueInputBuffer: [%d]%d timestampUs %ld codecFlags %d", index, inputBuffer->index, inputBuffer->timestampUs, inputBuffer->codecFlags);

        } pthread_mutex_unlock(&inputCache_lock);

        // Queue one input for each time
        // queueInputBuffer(videoDecoder);
        sem_post(&videoDecoder->rendering_sem);

    } pthread_mutex_unlock(&videoDecoder->lock);

    // LOGD("VideoDecoder_queueInputBuffer2 result %d index %d bufsize %d timestampUs %ld codecFlags %d", res, index, bufsize, timestampUs, codecFlags);

    return true;
}