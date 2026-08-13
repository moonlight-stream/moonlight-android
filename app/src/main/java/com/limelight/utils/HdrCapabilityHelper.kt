@file:Suppress("DEPRECATION")
package com.limelight.utils

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import com.limelight.LimeLog

/**
 * 统一的 HDR 与屏幕能力检测工具类
 * 用于向服务端报告亮度信息以及在诊断页面展示
 */
object HdrCapabilityHelper {

    class BrightnessInfo {
        var maxLuminance: Float = DEFAULT_MAX
        var minLuminance: Float = DEFAULT_MIN
        var maxAvgLuminance: Float = DEFAULT_AVG
        var isFromHdrCaps: Boolean = false
        var isDefault: Boolean = true

        var hdrSdrRatio: Float = 1.0f
        var highestHdrSdrRatio: Float = 1.0f
        var isHdrSdrRatioAvailable: Boolean = false

        var computedPeakBrightness: Float = -1f
        var isComputedFromRatio: Boolean = false

        companion object {
            const val DEFAULT_MAX = 500f
            const val DEFAULT_MIN = 2f
            const val DEFAULT_AVG = 200f
        }
    }

    class HdrTypeSupport {
        var hasHlg: Boolean = false
        var hasHdr10: Boolean = false
        var hasHdr10Plus: Boolean = false
        var hasDolbyVision: Boolean = false
        var rawTypes: IntArray = IntArray(0)
    }

    class HdrCapabilityInfo {
        var brightness: BrightnessInfo = BrightnessInfo()
        var typeSupport: HdrTypeSupport = HdrTypeSupport()
        var isScreenHdr: Boolean = false
        var isWideColorGamut: Boolean = false
        var displayReportsHdr: Boolean = false
    }

    @SuppressLint("NewApi")
    fun getBrightnessInfo(context: Context?): BrightnessInfo {
        val info = BrightnessInfo()
        if (context == null) return info

        val display = getDefaultDisplay(context) ?: return info

        // 第一步：从 EDID (HdrCapabilities) 获取基础亮度值 (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hdrCaps = display.hdrCapabilities
            if (hdrCaps != null) {
                val maxLum = hdrCaps.desiredMaxLuminance
                val minLum = hdrCaps.desiredMinLuminance
                val maxAvgLum = hdrCaps.desiredMaxAverageLuminance

                LimeLog.info("HdrCapabilities raw: max=$maxLum, min=$minLum, avg=$maxAvgLum")

                if (maxLum.isFinite() && maxLum > 0.1f) {
                    info.maxLuminance = maxLum
                    info.isFromHdrCaps = true
                    info.isDefault = false
                }
                if (maxAvgLum.isFinite() && maxAvgLum > 0.1f) {
                    info.maxAvgLuminance = maxAvgLum
                    info.isDefault = false
                }
                if (minLum.isFinite() && minLum >= 0f) {
                    info.minLuminance = maxOf(0.001f, minLum)
                    info.isDefault = false
                }
            }
        }

        // 第二步：使用 HDR/SDR ratio 获取更精确的峰值亮度 (Android 14+ / API 34+)
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                info.isHdrSdrRatioAvailable = display.isHdrSdrRatioAvailable
                if (info.isHdrSdrRatioAvailable) {
                    info.hdrSdrRatio = display.hdrSdrRatio
                    LimeLog.info("HDR/SDR ratio: current=${info.hdrSdrRatio}, available=${info.isHdrSdrRatioAvailable}")

                    if (Build.VERSION.SDK_INT >= 36) {
                        try {
                            info.highestHdrSdrRatio = display.highestHdrSdrRatio
                            LimeLog.info("Highest HDR/SDR ratio: ${info.highestHdrSdrRatio}")
                        } catch (_: NoSuchMethodError) {
                            info.highestHdrSdrRatio = info.hdrSdrRatio
                        }
                    } else {
                        info.highestHdrSdrRatio = info.hdrSdrRatio
                    }

                    val bestRatio = maxOf(info.highestHdrSdrRatio, info.hdrSdrRatio)

                    if (bestRatio > 1.0f && !info.isFromHdrCaps) {
                        val assumedSdrNits = 300f
                        val computedPeak = assumedSdrNits * bestRatio
                        info.computedPeakBrightness = computedPeak
                        info.isComputedFromRatio = true

                        if (computedPeak > info.maxLuminance) {
                            info.maxLuminance = computedPeak
                            info.isDefault = false
                        }
                        LimeLog.info("Computed peak brightness (no EDID): $computedPeak nits (assumedSdr=$assumedSdrNits, ratio=$bestRatio)")
                    } else if (bestRatio > 1.0f && info.isFromHdrCaps) {
                        val impliedSdrNits = info.maxLuminance / bestRatio
                        info.computedPeakBrightness = info.maxLuminance
                        info.isComputedFromRatio = true

                        if (impliedSdrNits < 100f) {
                            val correctedPeak = 300f * bestRatio
                            if (correctedPeak > info.maxLuminance) {
                                info.computedPeakBrightness = correctedPeak
                                info.maxLuminance = correctedPeak
                                LimeLog.info("EDID likely underreporting: corrected peak=$correctedPeak nits (impliedSdr=$impliedSdrNits was too low)")
                            }
                        }
                        LimeLog.info("Peak brightness with EDID+ratio: peak=${info.computedPeakBrightness}, edidMax=${info.maxLuminance}, impliedSdr=$impliedSdrNits")
                    }
                }
            } catch (e: Exception) {
                LimeLog.warning("Failed to get HDR/SDR ratio: ${e.message}")
            }
        }

        return info
    }

    @SuppressLint("NewApi")
    fun getHdrTypeSupport(context: Context?): HdrTypeSupport {
        if (context == null) return HdrTypeSupport()
        return getHdrTypeSupport(getDefaultDisplay(context))
    }

    /**
     * Returns display-wide HDR capabilities without restricting them to the current display mode.
     * This is intended for settings shown before a specific streaming mode has been selected.
     */
    @SuppressLint("NewApi")
    fun getDisplayWideHdrTypeSupport(display: Display?): HdrTypeSupport {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return HdrTypeSupport()
        }

        return parseHdrTypes(display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0))
    }

    /**
     * Returns HDR types for the selected display mode on Android 14+, where HDR support may vary
     * by mode. Older releases expose only display-wide HDR capabilities.
     */
    @SuppressLint("NewApi")
    fun getHdrTypeSupport(display: Display?, selectedModeId: Int = -1): HdrTypeSupport {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return HdrTypeSupport()
        }

        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val selectedMode = if (selectedModeId >= 0) {
                display.supportedModes.firstOrNull { it.modeId == selectedModeId }
            } else {
                null
            }
            (selectedMode ?: display.mode).supportedHdrTypes
        } else {
            display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        }

        return parseHdrTypes(types)
    }

    @SuppressLint("InlinedApi")
    private fun parseHdrTypes(types: IntArray): HdrTypeSupport {
        val support = HdrTypeSupport()
        support.rawTypes = types

        for (type in types) {
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> support.hasDolbyVision = true
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> support.hasHdr10 = true
                Display.HdrCapabilities.HDR_TYPE_HLG -> support.hasHlg = true
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> support.hasHdr10Plus = true
            }
        }

        return support
    }

    @SuppressLint("NewApi")
    fun getFullCapabilityInfo(context: Context?): HdrCapabilityInfo {
        val capInfo = HdrCapabilityInfo()
        capInfo.brightness = getBrightnessInfo(context)
        capInfo.typeSupport = getHdrTypeSupport(context)

        if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    capInfo.isScreenHdr = context.resources.configuration.isScreenHdr
                } catch (_: Exception) {}
            }

            val display = getDefaultDisplay(context)
            if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    capInfo.isWideColorGamut = display.isWideColorGamut
                } catch (_: Exception) {}
            }

            if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    capInfo.displayReportsHdr = display.isHdr
                } catch (_: NoSuchMethodError) {}
            }
        }

        return capInfo
    }

    fun getBrightnessRangeAsInts(context: Context?): IntArray {
        val info = getBrightnessInfo(context)
        val min = maxOf(1, info.minLuminance.toInt())

        var effectiveMax = info.maxLuminance
        if (info.isComputedFromRatio && info.computedPeakBrightness > effectiveMax) {
            effectiveMax = info.computedPeakBrightness
        }

        val max = maxOf(min + 1, Math.ceil(effectiveMax.toDouble()).toInt())
        val avg = maxOf(min, Math.ceil(info.maxAvgLuminance.toDouble()).toInt())
        return intArrayOf(min, max, avg)
    }

    fun applyBrightnessOverride(range: IntArray, peakBrightnessNits: Int): IntArray {
        val min = range.getOrElse(0) { 1 }.coerceAtLeast(1)
        val peak = maxOf(min + 1, peakBrightnessNits.coerceIn(300, 4000))
        val avg = (peak * 0.2f).toInt().coerceIn(100, 1000)
        return intArrayOf(min, peak, maxOf(min, avg))
    }

    fun getSystemBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        } catch (_: Exception) {
            -1
        }
    }

    fun isAutoBrightnessEnabled(context: Context): Boolean {
        return try {
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("NewApi")
    private fun getDefaultDisplay(context: Context): Display? {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            if (dm != null) {
                return dm.getDisplay(Display.DEFAULT_DISPLAY)
            }
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay
        } catch (e: Exception) {
            LimeLog.warning("Failed to get default display: ${e.message}")
            null
        }
    }
}
