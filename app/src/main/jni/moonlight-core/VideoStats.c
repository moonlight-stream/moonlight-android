//
// Created by Viktor Pih on 2020/8/10.
//

#include "VideoStats.h"
#include <sys/time.h>
#include <assert.h>

long getTimeUsec()
{
    struct timeval t;
    gettimeofday(&t, 0);
    return (long)((long)t.tv_sec * 1000 * 1000 + t.tv_usec);
}

void VideoStats_add(VideoStats* stats, const VideoStats* other) {

    stats->decoderTimeMs += other->decoderTimeMs;
    stats->totalTimeMs += other->totalTimeMs;
    stats->totalFrames += other->totalFrames;
    stats->totalFramesReceived += other->totalFramesReceived;
    stats->totalFramesRendered += other->totalFramesRendered;
    stats->frameLossEvents += other->frameLossEvents;
    stats->framesLost += other->framesLost;

    if (stats->measurementStartTimestamp == 0) {
        stats->measurementStartTimestamp = other->measurementStartTimestamp;
    }

    assert(other->measurementStartTimestamp <= stats->measurementStartTimestamp);
}

void VideoStats_copy(VideoStats* stats, const VideoStats* other) {
    stats->decoderTimeMs = other->decoderTimeMs;
    stats->totalTimeMs = other->totalTimeMs;
    stats->totalFrames = other->totalFrames;
    stats->totalFramesReceived = other->totalFramesReceived;
    stats->totalFramesRendered = other->totalFramesRendered;
    stats->frameLossEvents = other->frameLossEvents;
    stats->framesLost = other->framesLost;
    stats->measurementStartTimestamp = other->measurementStartTimestamp;
}

void VideoStats_clear(VideoStats* stats) {
    stats->decoderTimeMs = 0;
    stats->totalTimeMs = 0;
    stats->totalFrames = 0;
    stats->totalFramesReceived = 0;
    stats->totalFramesRendered = 0;
    stats->frameLossEvents = 0;
    stats->framesLost = 0;
    stats->measurementStartTimestamp = 0;
}

VideoStatsFps VideoStats_getFps(const VideoStats* stats) {
    float elapsed = (getTimeUsec()/1000 - stats->measurementStartTimestamp) / (float) 1000;

    VideoStatsFps fps = {0};
    if (elapsed > 0) {
        fps.totalFps = stats->totalFrames / elapsed;
        fps.receivedFps = stats->totalFramesReceived / elapsed;
        fps.renderedFps = stats->totalFramesRendered / elapsed;
    }
    return fps;
}