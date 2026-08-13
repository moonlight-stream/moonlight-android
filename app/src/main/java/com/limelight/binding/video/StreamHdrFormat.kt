package com.limelight.binding.video

/** Dynamic-range format carried by the decoded input stream, not display-pipeline acceptance. */
enum class StreamHdrFormat(val displayName: String) {
    SDR("SDR"),
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    HLG("HLG"),
    ;

    val isHdr: Boolean
        get() = this != SDR

    /** Clarifies the observable boundary used by detailed diagnostics. */
    val diagnosticName: String
        get() = if (this == HDR10_PLUS) {
            "HDR10+ (metadata observed)"
        } else {
            displayName
        }
}

/** Pure policy that keeps capability/configuration distinct from observed stream state. */
internal object StreamHdrFormatPolicy {
    fun resolve(
        hdrEnabled: Boolean,
        hdrStateKnown: Boolean,
        isTenBitStream: Boolean,
        isPqHdr: Boolean,
        isHlg: Boolean,
        hdr10PlusConfigured: Boolean,
        hdr10PlusMetadataObserved: Boolean,
    ): StreamHdrFormat {
        val observedHdr10Plus = isTenBitStream &&
            isPqHdr &&
            hdr10PlusConfigured &&
            hdr10PlusMetadataObserved
        // Decoder-output metadata is sufficient evidence that the stream carries HDR10+ when the
        // initial host callback was missed. It cannot prove that a vendor display pipeline accepts
        // the metadata. An explicit host disable always wins over stale observations.
        val effectiveHdrEnabled = hdrEnabled || (!hdrStateKnown && observedHdr10Plus)

        if (!effectiveHdrEnabled || !isTenBitStream) {
            return StreamHdrFormat.SDR
        }
        if (isHlg) {
            return StreamHdrFormat.HLG
        }
        if (!isPqHdr) {
            return StreamHdrFormat.SDR
        }
        return if (observedHdr10Plus) {
            StreamHdrFormat.HDR10_PLUS
        } else {
            StreamHdrFormat.HDR10
        }
    }
}

/** Defines which host HDR transitions begin a new dynamic-metadata observation epoch. */
internal object HdrObservationEpochPolicy {
    fun shouldReset(previousEnabled: Boolean?, enabled: Boolean): Boolean =
        previousEnabled != enabled && !(previousEnabled == null && enabled)
}
