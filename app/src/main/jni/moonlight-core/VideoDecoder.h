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
//    int fd;
    ANativeWindow* window;
//    AMediaExtractor* ex;
    AMediaCodec* codec;
//    ThreadInfo* threadInfo;
//    int64_t renderstart;
//    bool sawInputEOS;
//    bool sawOutputEOS;
//    bool isPlaying;
//    bool renderonce;

    bool stop;
    void (*stopCallback)(void*);

    // 缓冲区
    VideoInputBuffer* inputBufferCache;

    pthread_mutex_t lock; // api lock
    sem_t rendering_sem;
    sem_t queue_sem;
} VideoDecoder;

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* name, const char* mimeType, int width, int height, int fps, bool lowLatency);
void VideoDecoder_release(VideoDecoder* videoDecoder);

void VideoDecoder_start(VideoDecoder* videoDecoder);
void VideoDecoder_stop(VideoDecoder* videoDecoder);

// Callback
int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDecoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

int VideoDecoder_dequeueInputBuffer(VideoDecoder* videoDecoder);
VideoInputBuffer* VideoDecoder_getInputBuffer(VideoDecoder* videoDecoder, int index);
bool VideoDecoder_queueInputBuffer(VideoDecoder* videoDecoder, int index, uint64_t timestampUs, uint32_t codecFlags);

bool VideoDecoder_isBusing(VideoDecoder* videoDecoder);
// bool VideoDecoder_getEmptyInputBuffer(VideoDecoder* videoDecoder, VideoInputBuffer* inputBuffer);

// native
int VideoDecoder_dequeueInputBuffer2(VideoDecoder* videoDecoder);
void* VideoDecoder_getInputBuffer2(VideoDecoder* videoDecoder, int index, size_t* bufsize);
bool VideoDecoder_queueInputBuffer2(VideoDecoder* videoDecoder, int index, size_t bufsize, uint64_t timestampUs, uint32_t codecFlags);

#endif //MOONLIGHT_ANDROID_DECODER_H
