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

#define BUSY_COUNT 2
#define USE_CACHE 1
#define QUEUE_IMMEDIATE 0

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
};

// buffer index
typedef enum {
    SPS, PPS, VPS,
    __BUFFER_MAX
};

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

                videoDecoder->renderingFrames ++;
                
                // mark to invalid
                inputBuffer->status = INPUT_BUFFER_STATUS_INVALID;
                
                break; // or continue
            }
        }        

    } pthread_mutex_unlock(&inputCache_lock);
}

int _dequeueInputBuffer(VideoDecoder* videoDecoder) {

    int index = -1;

#if USE_CACHE
    // get one
    pthread_mutex_lock(&inputCache_lock); {

        for (int i = 0; i < InputBufferMaxSize; i++) {
        VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];
            if (inputBuffer->status == INPUT_BUFFER_STATUS_FREE) {
                inputBuffer->status = INPUT_BUFFER_STATUS_WORKING;
                index = i;
                break;
            }
        }
        
    } pthread_mutex_unlock(&inputCache_lock);
#else
    while (index < 0 && !videoDecoder->stopping) {
        index = AMediaCodec_dequeueInputBuffer(videoDecoder->codec, 10000);
    }
    
#endif

    return index;
}

void* _getInputBuffer(VideoDecoder* videoDecoder, int index, size_t* bufsize) {

    void* buf = 0;

#if USE_CACHE
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
#else
    buf = AMediaCodec_getInputBuffer(videoDecoder->codec, index, bufsize);
#endif

    return buf;
}

bool _queueInputBuffer2(VideoDecoder* videoDecoder, int index, size_t bufsize, uint64_t timestampUs, uint32_t codecFlags) {

#if USE_CACHE
    pthread_mutex_lock(&inputCache_lock);
    {
        // add to list
        VideoInputBuffer *inputBuffer = &videoDecoder->inputBufferCache[index];
        assert(inputBuffer->status == INPUT_BUFFER_STATUS_WORKING);

        inputBuffer->timestampUs = timestampUs;
        inputBuffer->codecFlags = codecFlags;
        inputBuffer->bufsize = bufsize;

        inputBuffer->status = INPUT_BUFFER_STATUS_QUEUING;

        LOGD("[fuck] post queueInputBuffer: [%d]%d timestampUs %ld codecFlags %d", index, inputBuffer->index, inputBuffer->timestampUs, inputBuffer->codecFlags);

    } pthread_mutex_unlock(&inputCache_lock);

#if QUEUE_IMMEDIATE
    // Queue input buffers
    queueInputBuffer(videoDecoder);
#else
    sem_post(&videoDecoder->queue_sem);
#endif

#else
    // push to codec
    AMediaCodec_queueInputBuffer(videoDecoder->codec, index, 0, bufsize, timestampUs,
                                 codecFlags);
#endif

    return true;
}

bool _isBusing(VideoDecoder* videoDecoder) {
    
    return videoDecoder->renderedFrames > 0 && videoDecoder->renderingFrames - videoDecoder->renderedFrames > BUSY_COUNT;
}

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
    return Build_VERSION_SDK_INT >= Build_VERSION_CODES_M;
    // return Build.VERSION.SDK_INT >= Build_VERSION_CODES_M &&
    //         isDecoderInList(qualcommDecoderPrefixes, decoderName) &&
    //         !isAdreno620;
}

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* decoderName, const char* mimeType, int width, int height, int refreshRate, int prefsFps, bool lowLatency, bool adaptivePlayback) {

    // Codecs have been known to throw all sorts of crazy runtime exceptions
    // due to implementation problems
    AMediaCodec* codec = AMediaCodec_createCodecByName(decoderName);

    AMediaFormat* videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, mimeType);

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

        if (decoderSupportsMaxOperatingRate(decoderName)) {
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
    LOGT("videoFormat %s", string);

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
    videoDecoder->decoderName = malloc(strlen(decoderName)+1);
    strcpy(videoDecoder->decoderName, decoderName);
    videoDecoder->initialWidth = width;
    videoDecoder->initialHeight = height;
    videoDecoder->refreshRate = refreshRate;

    videoDecoder->stopping = false;
    videoDecoder->stopCallback = 0;
    pthread_mutex_init(&videoDecoder->lock, 0);
    sem_init(&videoDecoder->queue_sem, 0, 1);

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

    // Initialize
    for (int i = 0; i < __BUFFER_MAX; i++) {
        FrameBuffer framebuffer;
        framebuffer.data = 0;
        framebuffer.size = 0;
        videoDecoder->buffers[i] = framebuffer;
    }

    videoDecoder->adaptivePlayback = adaptivePlayback;
    videoDecoder->needsBaselineSpsHack = false;
    videoDecoder->constrainedHighProfile = false;
    videoDecoder->refFrameInvalidationActive = false;

    videoDecoder->infoBuffer = malloc(1024);

    return videoDecoder;
}

void releaseVideoDecoder(VideoDecoder* videoDecoder) {
    AMediaCodec_delete(videoDecoder->codec);
    ANativeWindow_release(videoDecoder->window);

    pthread_mutex_destroy(&videoDecoder->lock);
    sem_destroy(&videoDecoder->queue_sem);

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

void* rendering_thread(VideoDecoder* videoDecoder)
{
    // Try to output a frame
    AMediaCodecBufferInfo info;

    while(!videoDecoder->stopping) {
        
        // Build input buffer cache
        makeInputBuffer(videoDecoder);

        int outIndex = AMediaCodec_dequeueOutputBuffer(videoDecoder->codec, &info, 50000); // -1 to block test
        if (outIndex >= 0) {

            long presentationTimeUs = info.presentationTimeUs;
            int lastIndex = outIndex;

#ifdef LC_DEBUG
            static long prevRenderingTime[2] = {0};
            long start_time = getTimeUsec();
#endif

            // Get the last output buffer in the queue
        //    while ((outIndex = AMediaCodec_dequeueOutputBuffer(videoDecoder->codec, &info, 0)) >= 0) {
        //        AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, false);

        //        lastIndex = outIndex;
        //        presentationTimeUs = info.presentationTimeUs;
        //    }

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
            if (releaseDelayUs > customDelayUs) releaseDelayUs = customDelayUs; // need delay

            long frameDelay = (start_time - presentationTimeUs)/1000;
            LOGT("[test] - 渲染 呈现用时 %d ms 调用 %d ms 延迟 %d ms %ld %ld deleay %d need\n", (getTimeUsec() - start_time)/1000, (start_time - prevRenderingTime[0])/1000, frameDelay, start_time, presentationTimeUs, releaseDelayUs);
            prevRenderingTime[1] = prevRenderingTime[0];
            prevRenderingTime[0] = start_time;
#endif

            AMediaCodec_releaseOutputBuffer(videoDecoder->codec, lastIndex, true);

            LOGD("[fuck] rendering_thread: Rendering ... [%d] flags %d offset %d size %d presentationTimeUs %ld", lastIndex, info.flags, info.offset, info.size, presentationTimeUs/1000);

            videoDecoder->renderedFrames ++;

            videoDecoder->activeWindowVideoStats.totalFramesRendered ++;
            // Add delta time to the totals (excluding probable outliers)
            long delta = (getTimeUsec() - presentationTimeUs) / 1000;
            if (delta >= 0 && delta < 1000) {
                videoDecoder->activeWindowVideoStats.decoderTimeMs += delta;
                if (!USE_FRAME_RENDER_TIME) {
                    videoDecoder->activeWindowVideoStats.totalTimeMs += delta;
                }
            }
            
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
        }
    }
    
    if (videoDecoder->stopCallback) {
        videoDecoder->stopCallback(videoDecoder);
    }

    LOGT("rendering_thread: Thread quited!");
}

void* queuing_thread(VideoDecoder* videoDecoder) {

    int count = 0;

    while(!videoDecoder->stopping) {

        // Queue input buffers
        queueInputBuffer(videoDecoder);

        sem_wait(&videoDecoder->queue_sem);
    }

    // // Build input buffer cache
    // makeInputBuffer(videoDecoder);

    // while(!videoDecoder->stopping) {

    //     sem_wait(&videoDecoder->queue_sem);

    //     // Queue input buffers
    //     queueInputBuffer(videoDecoder);

    //     // Build input buffer cache
    //     makeInputBuffer(videoDecoder);
    // }
}

void VideoDecoder_start(VideoDecoder* videoDecoder) {

    pthread_mutex_lock(&videoDecoder->lock);

    assert(!videoDecoder->stopping);

    if (AMediaCodec_start(videoDecoder->codec) != AMEDIA_OK) {
        LOGD("AMediaCodec_start: Could not start encoder.");
    }
    else {
        LOGD("AMediaCodec_start: encoder successfully started");
    }

    // Init
    videoDecoder->lastFrameNumber = 0;
    videoDecoder->renderingFrames = 0;
    videoDecoder->renderedFrames = 0;
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

#if !QUEUE_IMMEDIATE
    pthread_attr_init(&attr);
    pthread_create(&pid, &attr, queuing_thread, videoDecoder);
#endif

    // Set current
    currentVideoDecoder = videoDecoder;

    pthread_mutex_unlock(&videoDecoder->lock);
}

void VideoDecoder_stop(VideoDecoder* videoDecoder) {

    videoDecoder->stopping = true;
    sem_post(&videoDecoder->queue_sem);
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

bool replaySps(VideoDecoder* videoDecoder, long receiveTimeMs) {

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
            receiveTimeMs,
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

    // If we need to hack this SPS to say we're baseline, do so now
    if (videoDecoder->needsBaselineSpsHack) {
        LOGD("Hacking SPS to baseline");
        sps.profile_idc = 66;
        videoDecoder->savedSps = sps;
    }

    // Patch the SPS constraint flags
    doProfileSpecificSpsPatching(&sps, videoDecoder->constrainedHighProfile);

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

void printBufferHex(void* data, size_t size) {
    char tmp[1024];
    memset(tmp, 0, 1024);

    for (int i=0; i<size; i++) {
        sprintf(tmp, "%s %x", tmp, ((char*)data)[i]);
    }
    LOGT("buffer: %s", tmp);
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

    // Get UTCx time, not the same as receiveTimeMs (clock time)
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
//            System.out.println("1.5");
        inputBufferIndex = _dequeueInputBuffer(videoDecoder);
        if (inputBufferIndex < 0) {
            // We're being torn down now
            RETURN(DR_NEED_IDR);
        }
//            System.out.println("1.51 " + videoDecoder2);
        inputBuffer = _getInputBuffer(videoDecoder, inputBufferIndex, &inputBufsize);
        if (inputBuffer == 0) {
            // We're being torn down now
            RETURN(DR_NEED_IDR);
        }
//            System.out.println("1.2");
        if (videoDecoder->submitCsdNextCall) {
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
    memcpy(inputBuffer+inputBufPos, decodeUnitData, decodeUnitLength);
    inputBufPos += decodeUnitLength;

    if (!_queueInputBuffer2(videoDecoder, inputBufferIndex, inputBufPos,
            timestampUs, codecFlags)) {
        RETURN(DR_NEED_IDR);
    }

#ifdef LC_DEBUG
    long endTimeMillis = getTimeMsec();
    LOGT("[test] + 提交数据 %ld 间隔 %d ms 提交用时 %d ms", timestampUs, callDif/1000, endTimeMillis-currentTimeMillis);
#endif

    if ((codecFlags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
        videoDecoder->submittedCsd = true;

        if (videoDecoder->needsBaselineSpsHack) {
            videoDecoder->needsBaselineSpsHack = false;

            if (!replaySps(videoDecoder, timestampUs)) {
                RETURN(DR_NEED_IDR);
            }

            LOGD("SPS replay complete");
        }
    }

    RETURN(0);
}

#define SYNC_PUSH 1

bool VideoDecoder_isBusing(VideoDecoder* videoDecoder) {

#if SYNC_PUSH
    bool isBusing = false;
    char tmp[512] = {0};
#else
    bool isBusing = true;
#endif
    pthread_mutex_lock(&videoDecoder->lock); {

        // get a empty input buffer from cache
        pthread_mutex_lock(&inputCache_lock); {

            int count = 0;

            for (int i = 0; i < InputBufferMaxSize; i++) {
                VideoInputBuffer* inputBuffer = &videoDecoder->inputBufferCache[i];

#if SYNC_PUSH
                sprintf(tmp, "%s %d", tmp, inputBuffer->status);
                if (inputBuffer->status > INPUT_BUFFER_STATUS_FREE) {
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

    // isBusing = renderedFrames > 0 && renderingFrames - renderedFrames > 2;
    isBusing = _isBusing(videoDecoder);

    return isBusing;
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