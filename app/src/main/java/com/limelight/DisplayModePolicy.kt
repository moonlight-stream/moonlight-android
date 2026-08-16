package com.limelight

import kotlin.math.roundToInt

/** Pure display-mode selection policy, independent of Android framework objects. */
internal object DisplayModePolicy {
    data class Mode(
        val id: Int,
        val width: Int,
        val height: Int,
        val refreshRate: Float,
        val hdrTypes: List<Int> = emptyList(),
    )

    data class Request(
        val width: Int,
        val height: Int,
        val fps: Int,
        val usesNativeDisplayMode: Boolean,
        val mayReduceRefreshRate: Boolean,
        val acceptableHdrTypes: List<Int> = emptyList(),
    )

    data class Result(
        val mode: Mode,
        val hdrFilterApplied: Boolean,
    )

    fun selectBestMode(
        currentMode: Mode,
        supportedModes: List<Mode>,
        request: Request,
    ): Result {
        val hdrModes = if (request.acceptableHdrTypes.isNotEmpty()) {
            supportedModes.filter { it.supportsAny(request.acceptableHdrTypes) }
        } else {
            emptyList()
        }
        val hdrFilterApplied = hdrModes.isNotEmpty()
        val eligibleModes = if (hdrFilterApplied) hdrModes else supportedModes

        // An HDR request must be optimized entirely within the matching HDR set. Keep the
        // current mode as the baseline only when it belongs to that set; otherwise the first
        // candidate that passes the resolution constraints establishes the HDR baseline.
        var bestMode: Mode? = currentMode.takeIf { !hdrFilterApplied || it.supportsAny(request.acceptableHdrTypes) }
        var refreshRateIsGood = bestMode?.let { isRefreshRateGoodMatch(it.refreshRate, request.fps) } ?: false
        var refreshRateIsEqual = bestMode?.let { isRefreshRateEqualMatch(it.refreshRate, request.fps) } ?: false

        for (candidate in eligibleModes) {
            val comparisonMode = bestMode ?: currentMode
            val resolutionReduced = candidate.width < comparisonMode.width ||
                    candidate.height < comparisonMode.height
            val resolutionFitsStream = candidate.width >= request.width &&
                    candidate.height >= request.height

            if (candidate.width > 4096 && request.width <= 4096) {
                continue
            }

            if (request.width < 3840 && request.fps <= 60 && !request.usesNativeDisplayMode &&
                (currentMode.width != candidate.width || currentMode.height != candidate.height)
            ) {
                continue
            }

            if (resolutionReduced && !(request.fps > 60 && resolutionFitsStream)) {
                continue
            }

            // There is no valid HDR baseline yet. The first resolution-compatible HDR mode
            // must not be compared against an SDR mode's refresh rate.
            if (bestMode != null) {
                val refreshRateReduced = candidate.refreshRate < bestMode.refreshRate

                if (request.mayReduceRefreshRate && refreshRateIsEqual &&
                    !isRefreshRateEqualMatch(candidate.refreshRate, request.fps)
                ) {
                    continue
                } else if (refreshRateIsGood) {
                    if (!isRefreshRateGoodMatch(candidate.refreshRate, request.fps)) {
                        continue
                    }

                    if (request.mayReduceRefreshRate) {
                        if (candidate.refreshRate > bestMode.refreshRate) {
                            continue
                        }
                    } else if (refreshRateReduced) {
                        continue
                    }
                } else if (!isRefreshRateGoodMatch(candidate.refreshRate, request.fps) && refreshRateReduced) {
                    continue
                }
            }

            bestMode = candidate
            refreshRateIsGood = isRefreshRateGoodMatch(candidate.refreshRate, request.fps)
            refreshRateIsEqual = isRefreshRateEqualMatch(candidate.refreshRate, request.fps)
        }

        return Result(bestMode ?: currentMode, hdrFilterApplied)
    }

    fun isRefreshRateEqualMatch(refreshRate: Float, targetFps: Int): Boolean {
        return refreshRate >= targetFps && refreshRate <= targetFps + 3
    }

    fun isRefreshRateGoodMatch(refreshRate: Float, targetFps: Int): Boolean {
        return refreshRate >= targetFps && refreshRate.roundToInt() % targetFps <= 3
    }

    private fun Mode.supportsAny(acceptableHdrTypes: List<Int>): Boolean {
        return hdrTypes.any(acceptableHdrTypes::contains)
    }
}