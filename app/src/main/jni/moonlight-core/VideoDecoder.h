//
// Created by Viktor Pih on 2020/8/6.
//

#ifndef MOONLIGHT_ANDROID_DECODER_H
#define MOONLIGHT_ANDROID_DECODER_H

#include <jni.h>
#include <media/NdkMediaCodec.h>
#include <android/native_window_jni.h>
#include <semaphore.h>

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

    uint64_t renderingFrames;
    uint64_t renderedFrames;
    uint64_t lastTimestampUs;

    FrameBuffer buffers[3];
    uint32_t numSpsIn, numPpsIn, numVpsIn;
    bool submittedCsd, submitCsdNextCall, needsBaselineSpsHack;

    bool adaptivePlayback;

    bool stopping;
    void (*stopCallback)(void*);

    // 缓冲区
    VideoInputBuffer* inputBufferCache;

    pthread_mutex_t lock; // api lock
} VideoDecoder;

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* name, const char* mimeType, int width, int height, int fps, bool lowLatency, bool adaptivePlayback, bool needsBaselineSpsHack);
void VideoDecoder_release(VideoDecoder* videoDecoder);

void VideoDecoder_start(VideoDecoder* videoDecoder);
void VideoDecoder_stop(VideoDecoder* videoDecoder);

// Callback
int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

bool VideoDecoder_isBusing(VideoDecoder* videoDecoder);
// bool VideoDecoder_getEmptyInputBuffer(VideoDecoder* videoDecoder, VideoInputBuffer* inputBuffer);

// native
int VideoDecoder_dequeueInputBuffer2(VideoDecoder* videoDecoder);
void* VideoDecoder_getInputBuffer2(VideoDecoder* videoDecoder, int index, size_t* bufsize);
bool VideoDecoder_queueInputBuffer2(VideoDecoder* videoDecoder, int index, size_t bufsize, uint64_t timestampUs, uint32_t codecFlags);

#endif //MOONLIGHT_ANDROID_DECODER_H
