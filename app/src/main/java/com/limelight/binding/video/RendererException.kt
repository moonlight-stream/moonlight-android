package com.limelight.binding.video

import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.os.Build
import com.limelight.BuildConfig

/**
 * Diagnostic state snapshot captured when a renderer exception occurs.
 * Avoids holding a reference to the full renderer object.
 */
internal class RendererDiagnostics(
    val numVpsIn: Int,
    val numSpsIn: Int,
    val numPpsIn: Int,
    val numFramesIn: Int,
    val numFramesOut: Int,
    val videoFormat: Int,
    val initialWidth: Int,
    val initialHeight: Int,
    val refreshRate: Int,
    val bitrate: Int,
    val framePacing: Int,
    val consecutiveCrashCount: Int,
    val adaptivePlayback: Boolean,
    val refFrameInvalidationActive: Boolean,
    val fusedIdrFrame: Boolean,
    val hdr10PlusEligible: Boolean,
    val hdr10PlusConfigured: Boolean,
    val hdr10PlusOutputFramesQueried: Long,
    val hdr10PlusMetadataFrames: Long,
    val hdr10PlusMetadataChanges: Long,
    val hdr10PlusLastMetadataSize: Int,
    val hdr10PlusLastPresentationTimeUs: Long,
    val hdr10PlusMetadataQueryFailures: Int,
    val glRenderer: String?,
    val avcDecoder: MediaCodecInfo?,
    val hevcDecoder: MediaCodecInfo?,
    val av1Decoder: MediaCodecInfo?,
    val configuredFormat: android.media.MediaFormat?,
    val inputFormat: android.media.MediaFormat?,
    val outputFormat: android.media.MediaFormat?,
    val totalFramesReceived: Long,
    val totalFramesRendered: Long,
    val framesLost: Long,
    val frameLossEvents: Long,
    val averageEndToEndLatency: Int,
    val averageDecoderLatency: Int,
)

internal class DecoderHungException(private val hangTimeMs: Int) : RuntimeException() {
    override fun toString(): String =
        "Hang time: $hangTimeMs ms${RendererException.DELIMITER}${super.toString()}"
}

internal class RendererException(
    diag: RendererDiagnostics,
    originalException: Exception,
) : RuntimeException() {

    companion object {
        private const val serialVersionUID = 8985937536997012406L
        val DELIMITER: String = if (BuildConfig.DEBUG) "\n" else " | "
    }

    private val text = generateText(diag, originalException)

    override fun toString(): String = text

    private fun generateText(r: RendererDiagnostics, originalException: Exception): String {
        val d = DELIMITER
        val sb = StringBuilder()

        // Error phase classification
        sb.append(
            when {
                r.numVpsIn == 0 && r.numSpsIn == 0 && r.numPpsIn == 0 -> "PreSPSError"
                r.numSpsIn > 0 && r.numPpsIn == 0 -> "PrePPSError"
                r.numPpsIn > 0 && r.numFramesIn == 0 -> "PreIFrameError"
                r.numFramesIn > 0 && r.outputFormat == null -> "PreOutputConfigError"
                r.outputFormat != null && r.numFramesOut == 0 -> "PreOutputError"
                r.numFramesOut <= r.refreshRate * 30 -> "EarlyOutputError"
                else -> "ErrorWhileStreaming"
            }
        )

        sb.append("Format: ${String.format("%x", r.videoFormat)}$d")
        sb.append("AVC Decoder: ${r.avcDecoder?.name ?: "(none)"}$d")
        sb.append("HEVC Decoder: ${r.hevcDecoder?.name ?: "(none)"}$d")
        sb.append("AV1 Decoder: ${r.av1Decoder?.name ?: "(none)"}$d")

        appendCodecCapabilities(sb, r.avcDecoder, "video/avc", "AVC", r, d)
        appendCodecCapabilities(sb, r.hevcDecoder, "video/hevc", "HEVC", r, d)
        appendCodecCapabilities(sb, r.av1Decoder, "video/av01", "AV1", r, d)

        sb.append("Configured format: ${r.configuredFormat}$d")
        sb.append("Input format: ${r.inputFormat}$d")
        sb.append("Output format: ${r.outputFormat}$d")
        sb.append("Adaptive playback: ${r.adaptivePlayback}$d")
        sb.append("GL Renderer: ${r.glRenderer}$d")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sb.append("SOC: ${Build.SOC_MANUFACTURER} - ${Build.SOC_MODEL}$d")
            sb.append("Performance class: ${Build.VERSION.MEDIA_PERFORMANCE_CLASS}$d")
        }

        sb.append("Consecutive crashes: ${r.consecutiveCrashCount}$d")
        sb.append("RFI active: ${r.refFrameInvalidationActive}$d")
        sb.append("Using modern SPS patching: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O}$d")
        sb.append("Fused IDR frames: ${r.fusedIdrFrame}$d")
        sb.append(
            "HDR10+: eligible=${r.hdr10PlusEligible}, configured=${r.hdr10PlusConfigured}, " +
                "observed=${r.hdr10PlusConfigured && r.hdr10PlusMetadataFrames > 0}, " +
                "queried=${r.hdr10PlusOutputFramesQueried}, " +
                "metadata=${r.hdr10PlusMetadataFrames}, changes=${r.hdr10PlusMetadataChanges}, " +
                "lastSize=${r.hdr10PlusLastMetadataSize}, " +
                "lastPtsUs=${r.hdr10PlusLastPresentationTimeUs}, " +
                "queryFailures=${r.hdr10PlusMetadataQueryFailures}$d"
        )
        sb.append("Video dimensions: ${r.initialWidth}x${r.initialHeight}$d")
        sb.append("FPS target: ${r.refreshRate}$d")
        sb.append("Bitrate: ${r.bitrate} Kbps$d")
        sb.append("CSD stats: ${r.numVpsIn}, ${r.numSpsIn}, ${r.numPpsIn}$d")
        sb.append("Frames in-out: ${r.numFramesIn}, ${r.numFramesOut}$d")
        sb.append("Total frames received: ${r.totalFramesReceived}$d")
        sb.append("Total frames rendered: ${r.totalFramesRendered}$d")
        sb.append("Frame losses: ${r.framesLost} in ${r.frameLossEvents} loss events$d")
        sb.append("Average end-to-end client latency: ${r.averageEndToEndLatency}ms$d")
        sb.append("Average hardware decoder latency: ${r.averageDecoderLatency}ms$d")
        sb.append("Frame pacing mode: ${r.framePacing}$d")

        if (originalException is CodecException) {
            sb.append("Diagnostic Info: ${originalException.diagnosticInfo}$d")
            sb.append("Recoverable: ${originalException.isRecoverable}$d")
            sb.append("Transient: ${originalException.isTransient}$d")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sb.append("Codec Error Code: ${originalException.errorCode}$d")
            }
        }

        sb.append(originalException.toString())
        return sb.toString()
    }

    private fun appendCodecCapabilities(
        sb: StringBuilder,
        decoder: MediaCodecInfo?,
        mimeType: String,
        label: String,
        r: RendererDiagnostics,
        d: String,
    ) {
        if (decoder == null) return
        val videoCapabilities = decoder.getCapabilitiesForType(mimeType).videoCapabilities ?: return
        val widthRange = videoCapabilities.supportedWidths
        sb.append("$label supported width range: $widthRange$d")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val fpsRange = videoCapabilities.getAchievableFrameRatesFor(r.initialWidth, r.initialHeight)
                sb.append("$label achievable FPS range: $fpsRange$d")
            } catch (_: IllegalArgumentException) {
                sb.append("$label achievable FPS range: UNSUPPORTED!$d")
            }
        }
    }
}
