package com.limelight.binding.video

import android.annotation.SuppressLint
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import com.limelight.LimeLog

/** Queries decoder HDR profiles and builds the ordered MediaCodec profile fallback list. */
internal class HdrDecoderProfileSelector(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val fullRange: Boolean,
) {
    companion object {
        const val MIME_HEVC = "video/hevc"
        const val MIME_AV1 = "video/av01"

        @SuppressLint("InlinedApi")
        private const val HEVC_PROFILE_MAIN10_HDR10 =
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        @SuppressLint("InlinedApi")
        private const val HEVC_PROFILE_MAIN10_HDR10_PLUS =
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
        @SuppressLint("InlinedApi")
        private const val AV1_PROFILE_MAIN10 =
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
        @SuppressLint("InlinedApi")
        private const val AV1_PROFILE_MAIN10_HDR10 =
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
        @SuppressLint("InlinedApi")
        private const val AV1_PROFILE_MAIN10_HDR10_PLUS =
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus

        private val HEVC_MAIN10_PROFILES = intArrayOf(
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
            HEVC_PROFILE_MAIN10_HDR10,
            HEVC_PROFILE_MAIN10_HDR10_PLUS,
        )
        private val HEVC_HDR10_PROFILES = intArrayOf(
            HEVC_PROFILE_MAIN10_HDR10,
            HEVC_PROFILE_MAIN10_HDR10_PLUS,
        )
        private val AV1_MAIN10_PROFILES = intArrayOf(
            AV1_PROFILE_MAIN10,
            AV1_PROFILE_MAIN10_HDR10,
            AV1_PROFILE_MAIN10_HDR10_PLUS,
        )
        private val AV1_HDR10_PROFILES = intArrayOf(
            AV1_PROFILE_MAIN10_HDR10,
            AV1_PROFILE_MAIN10_HDR10_PLUS,
        )
    }

    fun supportsHevcMain10(decoderInfo: MediaCodecInfo?): Boolean = supportsAnyProfile(
        decoderInfo,
        MIME_HEVC,
        HEVC_MAIN10_PROFILES,
        "HEVC Main10",
    )

    fun supportsHevcHdr10(decoderInfo: MediaCodecInfo?): Boolean = supportsAnyProfile(
        decoderInfo,
        MIME_HEVC,
        HEVC_HDR10_PROFILES,
        "HEVC Main10 HDR10/HDR10+",
    )

    fun supportsHevcHdr10Plus(decoderInfo: MediaCodecInfo?): Boolean = hasProfile(
        decoderInfo,
        MIME_HEVC,
        HEVC_PROFILE_MAIN10_HDR10_PLUS,
    )

    fun supportsAv1Main10(decoderInfo: MediaCodecInfo?): Boolean = supportsAnyProfile(
        decoderInfo,
        MIME_AV1,
        AV1_MAIN10_PROFILES,
        "AV1 Main10",
    )

    fun supportsAv1Hdr10(decoderInfo: MediaCodecInfo?): Boolean = supportsAnyProfile(
        decoderInfo,
        MIME_AV1,
        AV1_HDR10_PROFILES,
        "AV1 Main10 HDR10/HDR10+",
    )

    fun supportsAv1Hdr10Plus(decoderInfo: MediaCodecInfo?): Boolean = hasProfile(
        decoderInfo,
        MIME_AV1,
        AV1_PROFILE_MAIN10_HDR10_PLUS,
    )

    fun supportsHdr10PlusFormat(
        decoderInfo: MediaCodecInfo?,
        mimeType: String,
    ): Boolean {
        val hdr10PlusProfile = profilesFor(mimeType)?.hdr10Plus ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !hasProfile(decoderInfo, mimeType, hdr10PlusProfile)
        ) {
            return false
        }

        return try {
            val probeFormat = MediaFormat.createVideoFormat(mimeType, width, height)
            probeFormat.setInteger(MediaFormat.KEY_PROFILE, hdr10PlusProfile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                probeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            }
            probeFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            probeFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084)
            probeFormat.setInteger(
                MediaFormat.KEY_COLOR_RANGE,
                if (fullRange) MediaFormat.COLOR_RANGE_FULL else MediaFormat.COLOR_RANGE_LIMITED,
            )

            val supported = decoderInfo!!
                .getCapabilitiesForType(mimeType)
                .isFormatSupported(probeFormat)
            if (!supported) {
                LimeLog.warning(
                    "${decoderInfo.name} advertises HDR10+ profile but rejects " +
                        "${width}x${height}@${frameRate}"
                )
            }
            supported
        } catch (e: RuntimeException) {
            LimeLog.warning("HDR10+ format probe failed for ${decoderInfo?.name}: ${e.message}")
            false
        }
    }

    /** Creates a probe using the dimensions/rate negotiated for the active stream. */
    fun forStreamParameters(
        width: Int,
        height: Int,
        frameRate: Int,
        fullRange: Boolean,
    ): HdrDecoderProfileSelector = HdrDecoderProfileSelector(
        width = width,
        height = height,
        frameRate = frameRate,
        fullRange = fullRange,
    )

    fun buildCandidates(
        mimeType: String,
        decoderInfo: MediaCodecInfo,
        isTenBit: Boolean,
        isPqHdr: Boolean,
        hdr10PlusEligible: Boolean,
    ): List<Int?> {
        val profiles = profilesFor(mimeType) ?: return listOf(null)
        return HdrDecoderProfilePolicy.buildCandidates(
            isTenBit = isTenBit,
            isPqHdr = isPqHdr,
            hdr10PlusEligible = hdr10PlusEligible,
            hdr10Advertised = hasProfile(decoderInfo, mimeType, profiles.hdr10),
            hdr10PlusProfile = profiles.hdr10Plus,
            hdr10Profile = profiles.hdr10,
        )
    }

    fun isHdr10PlusProfile(mimeType: String, profile: Int?): Boolean =
        profile != null && profile == profilesFor(mimeType)?.hdr10Plus

    fun profileName(mimeType: String, profile: Int?): String {
        if (profile == null) return "automatic"
        val profiles = profilesFor(mimeType)
        return when (profile) {
            profiles?.hdr10Plus -> "HDR10+"
            profiles?.hdr10 -> "HDR10"
            else -> profile.toString()
        }
    }

    private fun supportsAnyProfile(
        decoderInfo: MediaCodecInfo?,
        mimeType: String,
        acceptedProfiles: IntArray,
        description: String,
    ): Boolean {
        if (decoderInfo == null) return false
        return try {
            val supported = decoderInfo.getCapabilitiesForType(mimeType).profileLevels.any {
                it.profile in acceptedProfiles
            }
            if (supported) {
                LimeLog.info("${decoderInfo.name} supports $description")
            }
            supported
        } catch (e: RuntimeException) {
            LimeLog.warning("Failed to query ${decoderInfo.name} profiles: ${e.message}")
            false
        }
    }

    private fun hasProfile(
        decoderInfo: MediaCodecInfo?,
        mimeType: String,
        profile: Int,
    ): Boolean {
        if (decoderInfo == null) return false
        return try {
            decoderInfo.getCapabilitiesForType(mimeType).profileLevels.any { it.profile == profile }
        } catch (e: RuntimeException) {
            LimeLog.warning("Failed to query ${decoderInfo.name} profile $profile: ${e.message}")
            false
        }
    }

    private fun profilesFor(mimeType: String): HdrProfiles? = when (mimeType) {
        MIME_HEVC -> HdrProfiles(HEVC_PROFILE_MAIN10_HDR10, HEVC_PROFILE_MAIN10_HDR10_PLUS)
        MIME_AV1 -> HdrProfiles(AV1_PROFILE_MAIN10_HDR10, AV1_PROFILE_MAIN10_HDR10_PLUS)
        else -> null
    }

    private data class HdrProfiles(
        val hdr10: Int,
        val hdr10Plus: Int,
    )
}
