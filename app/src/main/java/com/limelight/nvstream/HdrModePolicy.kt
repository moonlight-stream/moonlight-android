package com.limelight.nvstream

import com.limelight.nvstream.jni.MoonBridge

/** Keeps client-only HDR selections out of the 0/1/2 host and framegen protocols. */
internal object HdrModePolicy {
    fun isHdr10PlusMode(hdrMode: Int): Boolean =
        hdrMode == MoonBridge.HDR_MODE_HDR10_PLUS

    fun isPqMode(hdrMode: Int): Boolean =
        hdrMode == MoonBridge.HDR_MODE_HDR10 || isHdr10PlusMode(hdrMode)

    fun shouldRequestHdr10Plus(
        hdrEnabled: Boolean,
        hdrMode: Int,
        displaySupportsHdr10Plus: Boolean,
        framegenRequested: Boolean,
    ): Boolean = hdrEnabled &&
        isHdr10PlusMode(hdrMode) &&
        displaySupportsHdr10Plus &&
        !framegenRequested

    fun toProtocolMode(hdrMode: Int): Int = when {
        isPqMode(hdrMode) -> MoonBridge.HDR_MODE_HDR10
        hdrMode == MoonBridge.HDR_MODE_HLG -> MoonBridge.HDR_MODE_HLG
        else -> MoonBridge.HDR_MODE_SDR
    }
}
