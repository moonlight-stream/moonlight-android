package com.limelight

import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.UiHelper
import kotlin.math.abs

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
        return DisplayModePolicy.isRefreshRateEqualMatch(refreshRate, targetFps)
    }

    fun isRefreshRateGoodMatch(refreshRate: Float, targetFps: Int): Boolean {
        return DisplayModePolicy.isRefreshRateGoodMatch(refreshRate, targetFps)
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
            val isNativeResolutionStream = prefConfig.usesNativeDisplayMode
            val effectiveHdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                acceptableHdrTypes.toList()
            } else {
                emptyList()
            }
            val currentMode = display.mode.toPolicyMode()
            val policyResult = DisplayModePolicy.selectBestMode(
                currentMode = currentMode,
                supportedModes = supportedModes.map { it.toPolicyMode() },
                request = DisplayModePolicy.Request(
                    width = prefConfig.width,
                    height = prefConfig.height,
                    fps = prefConfig.fps,
                    usesNativeDisplayMode = isNativeResolutionStream,
                    mayReduceRefreshRate = mayReduceRefreshRate(prefConfig),
                    acceptableHdrTypes = effectiveHdrTypes,
                ),
            )
            if (effectiveHdrTypes.isNotEmpty() && !policyResult.hdrFilterApplied) {
                LimeLog.warning("No display mode supports the requested HDR type; using normal mode selection")
            } else if (effectiveHdrTypes.isNotEmpty() &&
                policyResult.mode.hdrTypes.none(effectiveHdrTypes::contains)
            ) {
                LimeLog.warning("HDR-capable modes exist but none met the display mode constraints")
            }

            val bestMode = supportedModes.firstOrNull { it.modeId == policyResult.mode.id } ?: display.mode
            LimeLog.info("Current display mode: ${display.mode.physicalWidth}x${display.mode.physicalHeight}x${display.mode.refreshRate}")

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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun Display.Mode.toPolicyMode(): DisplayModePolicy.Mode {
        return DisplayModePolicy.Mode(
            id = modeId,
            width = physicalWidth,
            height = physicalHeight,
            refreshRate = refreshRate,
            hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                supportedHdrTypes.toList()
            } else {
                emptyList()
            },
        )
    }
}
