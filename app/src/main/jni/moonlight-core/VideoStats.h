//
// Created by Viktor Pih on 2020/8/10.
//

#ifndef MOONLIGHT_ANDROID_VIDEOSTATS_H
#define MOONLIGHT_ANDROID_VIDEOSTATS_H

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

long getTimeUsec();

void VideoStats_add(VideoStats* stats, const VideoStats* other);

VideoStatsFps VideoStats_getFps(const VideoStats* stats);

#endif //MOONLIGHT_ANDROID_VIDEOSTATS_H
