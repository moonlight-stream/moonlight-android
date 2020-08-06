//
// Created by Viktor Pih on 2020/8/6.
//

#ifndef MOONLIGHT_ANDROID_DECODER_H
#define MOONLIGHT_ANDROID_DECODER_H

#include <jni.h>
#include <media/NdkMediaCodec.h>
#include <android/native_window_jni.h>

typedef struct {
    bool stop;

} ThreadInfo;

typedef struct {
//    int fd;
    ANativeWindow* window;
//    AMediaExtractor* ex;
    AMediaCodec* codec;
    ThreadInfo* threadInfo;
//    int64_t renderstart;
//    bool sawInputEOS;
//    bool sawOutputEOS;
//    bool isPlaying;
//    bool renderonce;
} VideoDecoder;

VideoDecoder* VideoDecoder_create(JNIEnv *env, jobject surface, const char* name, const char* mimeType, int width, int height, int fps, int lowLatency);
void VideoDecoder_release(VideoDecoder* videoDeoder);

void VideoDecoder_start(VideoDecoder* videoDeoder);
void VideoDecoder_stop(VideoDecoder* videoDeoder);

// Callback
int VideoDecoder_submitDecodeUnit(VideoDecoder* videoDeoder, void* decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs);

#endif //MOONLIGHT_ANDROID_DECODER_H
