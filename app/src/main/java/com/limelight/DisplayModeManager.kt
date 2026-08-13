package com.limelight

import android.os.Build
import android.view.Display
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.UiHelper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 显示模式管理器
 * 负责从可用的显示模式中选择最佳的刷新率和分辨率模式。
 * 纯计算逻辑，不持有 Activity 引用。
 */
object DisplayModeManager {

    /** Immutable display mode choice bound to the display whose mode ids it references. */
    data class DisplayModeSelection(
        val displayId: Int,
        val selectedModeId: Int,
        val refreshRate: Float,
        val preferredModeId: Int,
        val useSetFrameRate: Boolean,
        val aspectRatioMatch: Boolean
    )

    fun isRefreshRateEqualMatch(refreshRate: Float, targetFps: Int): Boolean {
        return refreshRate >= targetFps && refreshRate <= targetFps + 3
    }

    fun isRefreshRateGoodMatch(refreshRate: Float, targetFps: Int): Boolean {
        return refreshRate >= targetFps && refreshRate.roundToInt() % targetFps <= 3
    }

    fun mayReduceRefreshRate(prefConfig: PreferenceConfiguration): Boolean {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate)
    }

    fun shouldIgnoreInsetsForResolution(
        display: Display,
        width: Int,
        height: Int,
        isNativeResolution: Boolean = PreferenceConfiguration.isNativeResolution(width, height)
    ): Boolean {
        if (!isNativeResolution) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (candidate in display.supportedModes) {
                if ((width == candidate.physicalWidth && height == candidate.physicalHeight) ||
                    (height == candidate.physicalWidth && width == candidate.physicalHeight)
                ) {
                    return true
                }
            }
        }

        return false
    }

    fun selectBestDisplayMode(
        display: Display,
        prefConfig: PreferenceConfiguration,
        acceptableHdrTypes: IntArray = IntArray(0),
    ): DisplayModeSelection {
        val displayRefreshRate: Float
        var selectedModeId = -1
        var preferredModeId = -1
        var useSetFrameRate = false
        var aspectRatioMatch = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val supportedModes = display.supportedModes
            val eligibleModes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                acceptableHdrTypes.isNotEmpty()
            ) {
                supportedModes.filter { mode ->
                    mode.supportedHdrTypes.any { supportedType ->
                        acceptableHdrTypes.any { it == supportedType }
                    }
                }.ifEmpty {
                    LimeLog.warning("No display mode supports the requested HDR type; using normal mode selection")
                    supportedModes.asList()
                }
            } else {
                supportedModes.asList()
            }

            // Start from the mode Android is actually using. A candidate may be
            // rejected by the resolution/refresh-rate constraints below; in that
            // case we must keep the current mode instead of retaining an arbitrary
            // first HDR-eligible mode that was never validated by those constraints.
            var bestMode = display.mode
            val isNativeResolutionStream = prefConfig.usesNativeDisplayMode
            var refreshRateIsGood = isRefreshRateGoodMatch(bestMode.refreshRate, prefConfig.fps)
            var refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.refreshRate, prefConfig.fps)

            LimeLog.info("Current display mode: ${bestMode.physicalWidth}x${bestMode.physicalHeight}x${bestMode.refreshRate}")

            for (candidate in eligibleModes) {
                val refreshRateReduced = candidate.refreshRate < bestMode.refreshRate
                val resolutionReduced = candidate.physicalWidth < bestMode.physicalWidth ||
                        candidate.physicalHeight < bestMode.physicalHeight
                val resolutionFitsStream = candidate.physicalWidth >= prefConfig.width &&
                        candidate.physicalHeight >= prefConfig.height

                LimeLog.info("Examining display mode: ${candidate.physicalWidth}x${candidate.physicalHeight}x${candidate.refreshRate}")

                if (candidate.physicalWidth > 4096 && prefConfig.width <= 4096) {
                    continue
                }

                if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                    if (display.mode.physicalWidth != candidate.physicalWidth ||
                        display.mode.physicalHeight != candidate.physicalHeight
                    ) {
                        continue
                    }
                }

                if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                    continue
                }

                if (mayReduceRefreshRate(prefConfig) && refreshRateIsEqual && !isRefreshRateEqualMatch(candidate.refreshRate, prefConfig.fps)) {
                    continue
                } else if (refreshRateIsGood) {
                    if (!isRefreshRateGoodMatch(candidate.refreshRate, prefConfig.fps)) {
                        continue
                    }

                    if (mayReduceRefreshRate(prefConfig)) {
                        if (candidate.refreshRate > bestMode.refreshRate) {
                            continue
                        }
                    } else {
                        if (refreshRateReduced) {
                            continue
                        }
                    }
                } else if (!isRefreshRateGoodMatch(candidate.refreshRate, prefConfig.fps)) {
                    if (refreshRateReduced) {
                        continue
                    }
                }

                bestMode = candidate
                refreshRateIsGood = isRefreshRateGoodMatch(candidate.refreshRate, prefConfig.fps)
                refreshRateIsEqual = isRefreshRateEqualMatch(candidate.refreshRate, prefConfig.fps)
            }

            LimeLog.info("Best display mode: ${bestMode.physicalWidth}x${bestMode.physicalHeight}x${bestMode.refreshRate}")

            if (display.mode.modeId != bestMode.modeId) {
                // setFrameRate() requests only a refresh rate, so Android may choose a different
                // same-resolution mode whose HDR types don't match the mode we validated above.
                // Pin the exact mode whenever mode-specific HDR capabilities influenced selection.
                val requiresExactHdrMode =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        acceptableHdrTypes.isNotEmpty()
                if (requiresExactHdrMode ||
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || UiHelper.isColorOS() ||
                    display.mode.physicalWidth != bestMode.physicalWidth ||
                    display.mode.physicalHeight != bestMode.physicalHeight
                ) {
                    preferredModeId = bestMode.modeId
                } else {
                    LimeLog.info("Using setFrameRate() instead of preferredDisplayModeId due to matching resolution")
                    useSetFrameRate = true
                }
            } else {
                LimeLog.info("Current display mode is already the best display mode")
            }

            displayRefreshRate = bestMode.refreshRate
            selectedModeId = bestMode.modeId
        } else {
            @Suppress("DEPRECATION")
            var bestRefreshRate = display.refreshRate
            @Suppress("DEPRECATION")
            for (candidate in display.supportedRefreshRates) {
                LimeLog.info("Examining refresh rate: $candidate")

                if (candidate > bestRefreshRate) {
                    if (prefConfig.fps <= 60) {
                        if (candidate >= 63) {
                            continue
                        }
                    }
                    bestRefreshRate = candidate
                }
            }

            LimeLog.info("Selected refresh rate: $bestRefreshRate")
            displayRefreshRate = bestRefreshRate
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val screenSize = android.graphics.Point(0, 0)
            @Suppress("DEPRECATION")
            display.getSize(screenSize)

            val screenAspectRatio = screenSize.y.toDouble() / screenSize.x
            val streamAspectRatio = prefConfig.height.toDouble() / prefConfig.width
            if (abs(screenAspectRatio - streamAspectRatio) < 0.001) {
                LimeLog.info("Stream has compatible aspect ratio with output display")
                aspectRatioMatch = true
            }
        }

        return DisplayModeSelection(
            displayId = display.displayId,
            selectedModeId = selectedModeId,
            refreshRate = displayRefreshRate,
            preferredModeId = preferredModeId,
            useSetFrameRate = useSetFrameRate,
            aspectRatioMatch = aspectRatioMatch
        )
    }
}
