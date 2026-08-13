package com.limelight.binding.video

/** Pure policy for Qualcomm decoder options whose interaction affects HDR10+ metadata. */
internal object QualcommLowLatencyPolicy {
    private const val PICTURE_ORDER_TRY_LIMIT = 4

    fun shouldEnablePictureOrder(
        tryNumber: Int,
        hdr10PlusModeSelected: Boolean,
    ): Boolean = tryNumber in 0 until PICTURE_ORDER_TRY_LIMIT && !hdr10PlusModeSelected
}
