package com.limelight.nvstream.av.video

abstract class VideoDecoderRenderer {
    abstract fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int

    abstract fun start()

    abstract fun stop()

    // This is called once for each frame-start NALU. This means it will be called several times
    // for an IDR frame which contains several parameter sets and the I-frame data.
    abstract fun submitDecodeUnit(
        decodeUnitData: ByteArray, decodeUnitLength: Int, decodeUnitType: Int,
        frameNumber: Int, frameType: Int, frameHostProcessingLatency: Char,
        receiveTimeUs: Long, enqueueTimeUs: Long, hostPresentationTimeUs: Long
    ): Int

    abstract fun cleanup()

    abstract fun getCapabilities(): Int

    abstract fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?)

    // Called when the host resolution changes (e.g., screen rotation)
    open fun onResolutionChanged(width: Int, height: Int) {
        // Default implementation does nothing
        // Subclasses can override to handle resolution changes
    }
}
