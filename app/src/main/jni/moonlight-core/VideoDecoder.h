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
    uint64_t renderingFrames;
    uint64_t renderedFrames;
    uint64_t lastTimestampUs;

    sps_t savedSps;

    FrameBuffer buffers[3];
    uint32_t numSpsIn, numPpsIn, numVpsIn;
    bool submittedCsd, submitCsdNextCall;
    bool adaptivePlayback, needsBaselineSpsHack, constrainedHighProfile, refFrameInvalidationActive, needsSpsBitstreamFixup, isExynos4;

    VideoStats activeWindowVideoStats;
    VideoStats lastWindowVideoStats;
    VideoStats globalVideoStats;

    bool stopping;
    void (*stopCallback)(void*);

    // 缓冲区
    VideoInputBuffer* inputBufferCache;
    char* infoBuffer;

    sem_t queue_sem;
    pthread_mutex_t lock; // api lock
} VideoDecoder;

// Control
VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* decoderName, const char* mimeType, int width, int height, int refreshRate, int prefsFps, bool lowLatency, bool adaptivePlayback);
void VideoDecoder_release(VideoDecoder* videoDecoder);
void VideoDecoder_start(VideoDecoder* videoDecoder);
void VideoDecoder_stop(VideoDecoder* videoDecoder);

// Submit data
int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

// Check busy
bool VideoDecoder_isBusing(VideoDecoder* videoDecoder);
const char* VideoDecoder_formatInfo(VideoDecoder* videoDecoder, const char* format);


// This is called once for each frame-start NALU. This means it will be called several times
// for an IDR frame which contains several parameter sets and the I-frame data.
int VideoDecoder_staticSubmitDecodeUnit(void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

#endif //MOONLIGHT_ANDROID_DECODER_H
