//
// Created by Viktor Pih on 2020/8/10.
//

#ifndef MOONLIGHT_ANDROID_VIDEOSTATS_H
#define MOONLIGHT_ANDROID_VIDEOSTATS_H

#include <stdint.h>

typedef struct {
    long decoderTimeMs;
    long totalTimeMs;
    int totalFrames;
    int totalFramesReceived;
    int totalFramesRendered;
    int frameLossEvents;
    int framesLost;
    long measurementStartTimestamp;
} VideoStats;

typedef struct {

    float totalFps;
    float receivedFps;
    float renderedFps;
} VideoStatsFps;

uint64_t getTimeMsec(void);
uint64_t getTimeUsec(void);
uint64_t getTimeNanc(void);
uint64_t getClockUsec(void);

void VideoStats_add(VideoStats* stats, const VideoStats* other);

VideoStatsFps VideoStats_getFps(const VideoStats* stats);

#endif //MOONLIGHT_ANDROID_VIDEOSTATS_H
