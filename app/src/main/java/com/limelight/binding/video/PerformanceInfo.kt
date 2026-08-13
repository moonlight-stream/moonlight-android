package com.limelight.binding.video

import android.content.Context

class PerformanceInfo {
    var context: Context? = null
    var decoder: String? = null
    var initialWidth: Int = 0
    var initialHeight: Int = 0
    var totalFps: Float = 0f
    var receivedFps: Float = 0f
    var renderedFps: Float = 0f
    var framegenFps: Float = 0f
    var framegenInterpolatedFps: Float = 0f
    var framegenBypassFps: Float = 0f
    var framegenQueueDepth: Int = 0
    var framegenPresenterDrops: Long = 0
    var framegenMode: Int = 0
    var framegenInputFps: Float = 0f
    var framegenLsfgWaitMs: Int = 0
    var framegenBlitMs: Int = 0
    var lostFrameRate: Float = 0f
    var rttInfo: Long = 0
    var framesWithHostProcessingLatency: Int = 0
    var minHostProcessingLatency: Float = 0f
    var maxHostProcessingLatency: Float = 0f
    var aveHostProcessingLatency: Float = 0f
    var decodeTimeMs: Float = 0f
    var totalTimeMs: Float = 0f
    var bandWidth: String? = null
    var hdrFormat: StreamHdrFormat = StreamHdrFormat.SDR
    /** Compatibility view for consumers that only distinguish SDR from HDR. */
    var isHdrActive: Boolean
        get() = hdrFormat.isHdr
        set(value) {
            hdrFormat = when {
                !value -> StreamHdrFormat.SDR
                hdrFormat.isHdr -> hdrFormat
                else -> StreamHdrFormat.HDR10
            }
        }
    var renderingLatencyMs: Float = 0f // 渲染时间
    var onePercentLowFps: Float = 0f // 1% low FPS (P99帧间隔倒数)
}
