//
// Created by Viktor Pih on 2020/8/6.
//

#ifndef MOONLIGHT_ANDROID_DECODER_H
#define MOONLIGHT_ANDROID_DECODER_H

#include <jni.h>
#include <media/NdkMediaCodec.h>
#include <android/native_window_jni.h>
#include <semaphore.h>
#include <h264bitstream/h264_stream.h>
#include "VideoStats.h"

typedef struct {
    int index;
    void* buffer;
    size_t bufsize;
    uint64_t timestampUs;
    int codecFlags;
    int status;
} VideoInputBuffer;

typedef struct {
    int index;
    AMediaCodecBufferInfo bufferInfo;
    int status;
} VideoOutputBuffer;

typedef struct {
    void* data;
    size_t size;
} FrameBuffer;

typedef struct {
    ANativeWindow* window;
    AMediaCodec* codec;
    const char* decoderName;

    int initialWidth, initialHeight;
    int refreshRate;

    uint64_t lastFrameNumber;
    uint64_t lastTimestampUs;

    sps_t savedSps;

    FrameBuffer buffers[3];
    uint32_t numSpsIn, numPpsIn, numVpsIn;
    bool submittedCsd, submitCsdNextCall;
    bool adaptivePlayback, needsBaselineSpsHack, constrainedHighProfile, refFrameInvalidationActive, needsSpsBitstreamFixup, isExynos4;

    bool legacyFrameDropRendering;

    VideoStats activeWindowVideoStats;
    VideoStats lastWindowVideoStats;
    VideoStats globalVideoStats;

    bool stopping;
    void (*stopCallback)(void*);

    // 缓冲区
    char* infoBuffer;
    VideoInputBuffer* inputBufferCache;
    VideoOutputBuffer* outputBufferCache;
    // sem_t queuing_sem;
    bool immediate;
    long immediate_count;

    pthread_mutex_t inputCacheLock;
    pthread_mutex_t outputCacheLock;
    pthread_mutex_t lock; // api lock
} VideoDecoder;

// Control
VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* decoderName, const char* mimeType, int width, int height, int refreshRate, int prefsFps, bool lowLatency, bool adaptivePlayback, bool maxOperatingRate);
void VideoDecoder_setLegacyFrameDropRendering(VideoDecoder* videoDecoder, bool enabled);
void VideoDecoder_release(VideoDecoder* videoDecoder);
void VideoDecoder_start(VideoDecoder* videoDecoder);
void VideoDecoder_stop(VideoDecoder* videoDecoder);

// Submit data
int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);
void VideoDecoder_getTempBuffer(void** buffer, size_t* bufsize);
void VideoDecoder_releaseTempBuffer(void* buffer);

// Check busy
const char* VideoDecoder_formatInfo(VideoDecoder* videoDecoder, const char* format);


// This is called once for each frame-start NALU. This means it will be called several times
// for an IDR frame which contains several parameter sets and the I-frame data.
int VideoDecoder_staticSubmitDecodeUnit(void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

#endif //MOONLIGHT_ANDROID_DECODER_H
