package com.limelight.binding.video

import android.annotation.SuppressLint
import android.media.MediaFormat

/** Filters per-frame HDR10+ metadata updates from structural output-format changes. */
internal class StableOutputFormatTracker {
    @SuppressLint("InlinedApi")
    private companion object {
        private val INTEGER_KEYS = arrayOf(
            MediaFormat.KEY_WIDTH,
            MediaFormat.KEY_HEIGHT,
            "crop-left",
            "crop-right",
            "crop-top",
            "crop-bottom",
            "color-format",
            "stride",
            "slice-height",
            MediaFormat.KEY_COLOR_STANDARD,
            MediaFormat.KEY_COLOR_TRANSFER,
            MediaFormat.KEY_COLOR_RANGE,
        )
    }

    private var signature: Int? = null

    fun reset() {
        signature = null
    }

    fun record(format: MediaFormat): Boolean {
        var newSignature = format.getString(MediaFormat.KEY_MIME)?.hashCode() ?: 0
        for (key in INTEGER_KEYS) {
            val value = try {
                if (format.containsKey(key)) format.getInteger(key) else 0
            } catch (_: RuntimeException) {
                // Vendor keys occasionally use unexpected types; they do not affect HDR dataspace.
                0
            }
            newSignature = 31 * newSignature + value
        }

        if (signature == newSignature) return false
        signature = newSignature
        return true
    }
}
