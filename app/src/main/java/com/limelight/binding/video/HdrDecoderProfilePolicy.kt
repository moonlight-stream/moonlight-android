package com.limelight.binding.video

/** Pure policy for ordered HDR decoder profile configuration and compatibility fallback. */
internal object HdrDecoderProfilePolicy {
    fun buildCandidates(
        isTenBit: Boolean,
        isPqHdr: Boolean,
        hdr10PlusEligible: Boolean,
        hdr10Advertised: Boolean,
        hdr10PlusProfile: Int,
        hdr10Profile: Int,
    ): List<Int?> {
        if (!isTenBit || !isPqHdr) {
            return listOf(null)
        }

        val candidates = mutableListOf<Int?>()
        if (hdr10PlusEligible) {
            candidates += hdr10PlusProfile
        }
        if (hdr10Advertised) {
            candidates += hdr10Profile
        }
        // Preserve the historical profile-less MediaCodec configuration as the final fallback.
        candidates += null
        return candidates.distinct()
    }
}
