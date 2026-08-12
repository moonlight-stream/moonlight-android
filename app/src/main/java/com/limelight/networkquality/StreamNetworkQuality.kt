package com.limelight.networkquality

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.preferences.PreferenceConfiguration
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StreamNetworkQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

fun ComputerDetails.supportsNetworkQualityProbe(): Boolean =
    !nvidiaServer && sunshineVersion?.endsWith(FOUNDATION_SUNSHINE_VERSION_SUFFIX) == true

data class StreamNetworkRecommendation(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val usesNativeResolution: Boolean
)

data class StreamDeviceDisplay(
    val nativeWidth: Int,
    val nativeHeight: Int,
    val maxFps: Int
) {
    val landscapeWidth: Int = max(nativeWidth, nativeHeight)
    val landscapeHeight: Int = min(nativeWidth, nativeHeight)
    val normalizedMaxFps: Int = when {
        maxFps >= 115 -> 120
        maxFps >= 85 -> 90
        maxFps >= 58 -> 60
        maxFps >= 45 -> 50
        else -> maxFps.coerceAtLeast(30)
    }
}

data class StreamNetworkTestResult(
    val bandwidthMbps: Double,
    val responseLatencyMs: Double,
    val responseJitterMs: Double,
    val testedAtEpochMs: Long = System.currentTimeMillis()
) {
    val quality: StreamNetworkQuality
        get() = when {
            bandwidthMbps >= 80.0 && responseLatencyMs < 20.0 -> StreamNetworkQuality.EXCELLENT
            bandwidthMbps >= 35.0 && responseLatencyMs < 50.0 -> StreamNetworkQuality.GOOD
            bandwidthMbps >= 15.0 && responseLatencyMs < 100.0 -> StreamNetworkQuality.FAIR
            else -> StreamNetworkQuality.POOR
        }

    fun recommendationFor(display: StreamDeviceDisplay): StreamNetworkRecommendation? {
        val safeKbps = floor(bandwidthMbps * 1000.0 * SAFE_BANDWIDTH_FRACTION)
            .toInt()
            .coerceAtMost(MAX_RECOMMENDED_KBPS)
            .let { it / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS }
        if (safeKbps < MIN_RECOMMENDED_KBPS) return null
        val highFps = display.normalizedMaxFps
        val standardFps = min(highFps, 60)

        // Prefer the panel's native aspect ratio. Resolution reductions are proportional,
        // so ultrawide and phone displays never fall back to unrelated 16:9 presets.
        val candidates = listOf(
            Candidate(1.0, highFps),
            Candidate(1.0, standardFps),
            Candidate(0.75, highFps),
            Candidate(0.75, standardFps),
            Candidate(0.5, highFps),
            Candidate(0.5, standardFps),
            Candidate(0.5, min(standardFps, 30))
        ).distinct()

        val evaluated = candidates.map { candidate ->
            val width = scaledEven(display.landscapeWidth, candidate.scale)
            val height = scaledEven(display.landscapeHeight, candidate.scale)
            EvaluatedCandidate(
                width = width,
                height = height,
                fps = candidate.fps,
                idealBitrateKbps = estimateBitrateKbps(width, height, candidate.fps),
                usesNativeResolution = candidate.scale == 1.0
            )
        }
        val selected = evaluated.firstOrNull { it.idealBitrateKbps <= safeKbps }
            ?: evaluated.last()
        val usefulBitrateCeiling = ceil(
            selected.idealBitrateKbps * MAX_DEFAULT_BITRATE_MULTIPLIER / 1000.0
        )
            .toInt() * 1000

        return StreamNetworkRecommendation(
            width = selected.width,
            height = selected.height,
            fps = selected.fps,
            bitrateKbps = min(safeKbps, usefulBitrateCeiling),
            usesNativeResolution = selected.usesNativeResolution
        )
    }

    fun isFresh(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        nowEpochMs - testedAtEpochMs in 0..RESULT_MAX_AGE_MS

    fun shouldWarnForBitrate(
        configuredBitrateKbps: Int,
        recommendation: StreamNetworkRecommendation
    ): Boolean = isFresh() &&
        configuredBitrateKbps > recommendation.bitrateKbps * WARNING_THRESHOLD_MULTIPLIER

    companion object {
        const val SAFE_BANDWIDTH_FRACTION = 0.65
        private const val WARNING_THRESHOLD_MULTIPLIER = 1.15
        private const val MIN_RECOMMENDED_KBPS = 500
        private const val MAX_RECOMMENDED_KBPS = 150_000
        private const val BITRATE_STEP_KBPS = 500
        private const val MAX_DEFAULT_BITRATE_MULTIPLIER = 2.0
        private const val RESULT_MAX_AGE_MS = 30L * 60L * 1000L

        private data class Candidate(val scale: Double, val fps: Int)

        private data class EvaluatedCandidate(
            val width: Int,
            val height: Int,
            val fps: Int,
            val idealBitrateKbps: Int,
            val usesNativeResolution: Boolean
        )

        private fun scaledEven(value: Int, scale: Double): Int =
            (value * scale).roundToInt().coerceAtLeast(2) and 1.inv()

        private fun estimateBitrateKbps(width: Int, height: Int, fps: Int): Int =
            PreferenceConfiguration.getDefaultBitrate("${width}x${height}", fps.toString())
    }
}

private const val FOUNDATION_SUNSHINE_VERSION_SUFFIX = "杂鱼"

object StreamNetworkQualityStore {
    private const val KEY_PREFIX = "stream_network_quality_"

    fun load(context: Context, computerUuid: String?, endpointIdentity: String?): StreamNetworkTestResult? {
        if (computerUuid.isNullOrBlank()) return null
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(storageKey(computerUuid, endpointIdentity), null)
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
            StreamNetworkTestResult(
                bandwidthMbps = json.getDouble("bandwidthMbps"),
                responseLatencyMs = json.optDouble("responseLatencyMs", 0.0),
                responseJitterMs = json.optDouble("responseJitterMs", 0.0),
                testedAtEpochMs = json.getLong("testedAtEpochMs")
            )
        }.getOrNull()
    }

    fun save(
        context: Context,
        computerUuid: String?,
        endpointIdentity: String?,
        result: StreamNetworkTestResult
    ) {
        if (computerUuid.isNullOrBlank()) return
        val json = JSONObject().apply {
            put("bandwidthMbps", result.bandwidthMbps)
            put("responseLatencyMs", result.responseLatencyMs)
            put("responseJitterMs", result.responseJitterMs)
            put("testedAtEpochMs", result.testedAtEpochMs)
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(storageKey(computerUuid, endpointIdentity), json.toString())
        }
    }

    private fun storageKey(computerUuid: String, endpointIdentity: String?): String {
        val endpointHash = Integer.toHexString(endpointIdentity.orEmpty().hashCode())
        return KEY_PREFIX + computerUuid + "_" + endpointHash
    }
}
