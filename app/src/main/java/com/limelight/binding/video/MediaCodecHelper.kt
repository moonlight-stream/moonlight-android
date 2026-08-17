package com.limelight.binding.video

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.limelight.LimeLog
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.regex.Pattern

object MediaCodecHelper {

    // ==================== Decoder Prefix Lists ====================

    private val preferredDecoders = mutableListOf<String>()
    private val blacklistedDecoderPrefixes = mutableListOf<String>()
    private val spsFixupBitstreamFixupDecoderPrefixes = mutableListOf<String>()
    private val blacklistedAdaptivePlaybackPrefixes = mutableListOf<String>()
    private val baselineProfileHackPrefixes = mutableListOf<String>()
    private val directSubmitPrefixes = mutableListOf<String>()
    private val constrainedHighProfilePrefixes = mutableListOf<String>()
    private val whitelistedHevcDecoders = mutableListOf<String>()
    private val refFrameInvalidationAvcPrefixes = mutableListOf<String>()
    private val refFrameInvalidationHevcPrefixes = mutableListOf<String>()
    private val useFourSlicesPrefixes = mutableListOf<String>()

    // ==================== Vendor Decoder Prefixes ====================

    private val qualcommDecoderPrefixes = listOf("omx.qcom", "c2.qti")
    private val mtkDecoderPrefixes = listOf("omx.mtk", "c2.mtk")
    private val kirinDecoderPrefixes = listOf("omx.hisi", "c2.hisi")
    private val exynosDecoderPrefixes = listOf("omx.exynos", "c2.exynos")
    private val amlogicDecoderPrefixes = listOf("omx.amlogic", "c2.amlogic")
    private val tegraDecoderPrefixes = listOf("omx.nvidia", "c2.nvidia")

    // ==================== Known Vendor Low Latency Options ====================
    // Representative vendor low-latency keys for each SoC vendor.
    // Used by decoderSupportsKnownVendorLowLatencyOption() to probe whether
    // a decoder supports low-latency extensions — which in turn drives
    // HEVC/AV1 RFI (Reference Frame Invalidation) decisions.
    //
    // When adding vendor params in the applyXxxVendorParams() methods,
    // also add the most representative key here.

    private val knownVendorLowLatencyOptions = listOf(
        // Qualcomm
        "vendor.qti-ext-dec-low-latency.enable",
        // HiSilicon / Kirin
        "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req",
        // Samsung Exynos
        "vendor.rtc-ext-dec-low-latency.enable",
        // Amlogic
        "vendor.low-latency.enable",
        // MediaTek Dimensity / MT SoCs
        "vendor.mtk.vdec.low-latency.mode",
        "vendor.mtk.vdec.decode-immediately",
    )

    // ==================== Runtime State ====================

    @JvmField
    val SHOULD_BYPASS_SOFTWARE_BLOCK: Boolean =
        Build.HARDWARE == "ranchu" || Build.HARDWARE == "cheets" || Build.BRAND == "Android-x86"

    private var isLowEndSnapdragon = false
    private var isAdreno620 = false
    private var isSnapdragonGSeries = false
    private var isAmlogicRfiSafe = false
    private var initialized = false

    init {
        // Direct submit decoders — low input buffer latency
        directSubmitPrefixes.addAll(listOf(
            "omx.qcom", "omx.sec", "omx.exynos", "omx.intel",
            "omx.brcm", "omx.TI", "omx.arc", "omx.nvidia",
            "c2." // All Codec2 decoders
        ))

        // RFI prefixes (Qualcomm/NVIDIA added at runtime)
        refFrameInvalidationHevcPrefixes.addAll(listOf("omx.exynos", "c2.exynos"))

        // Software decoder blacklist
        if (!SHOULD_BYPASS_SOFTWARE_BLOCK) {
            blacklistedDecoderPrefixes.addAll(listOf("omx.google", "AVCDecoder"))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                blacklistedDecoderPrefixes.add("OMX.ffmpeg")
            }
        }
        blacklistedDecoderPrefixes.addAll(listOf(
            "OMX.qcom.video.decoder.hevcswvdec",
            "OMX.SEC.hevc.sw.dec"
        ))

        // Bitstream fixup
        spsFixupBitstreamFixupDecoderPrefixes.addAll(listOf("omx.nvidia", "omx.qcom", "omx.brcm"))
        baselineProfileHackPrefixes.add("omx.intel")
        blacklistedAdaptivePlaybackPrefixes.addAll(listOf("omx.intel", "omx.mtk"))
        constrainedHighProfilePrefixes.add("omx.intel")

        // HEVC whitelist
        if (Build.HARDWARE == "ranchu") {
            whitelistedHevcDecoders.add("omx.google")
        }
        whitelistedHevcDecoders.add("omx.exynos")
        if (!Build.DEVICE.equals("shieldtablet", ignoreCase = true) &&
            !Build.DEVICE.equals("mocha", ignoreCase = true) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            whitelistedHevcDecoders.add("omx.nvidia")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.DEVICE.startsWith("BRAVIA_")) {
            whitelistedHevcDecoders.add("omx.mtk")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !Build.DEVICE.equals("sabrina", ignoreCase = true)) {
            whitelistedHevcDecoders.add("omx.amlogic")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            whitelistedHevcDecoders.add("omx.realtek")
        }
        whitelistedHevcDecoders.add("c2.")

        // Four slices for software decoders
        useFourSlicesPrefixes.addAll(listOf("omx.google", "AVCDecoder", "omx.ffmpeg", "c2.android"))
    }

    // ==================== GPU Detection ====================

    private fun isPowerVR(glRenderer: String) = glRenderer.lowercase().contains("powervr")

    private fun isMali(glRenderer: String) = glRenderer.lowercase().contains("mali")

    /** Check for high-end Mali GPUs (G710+) used in Dimensity 8000+ SoCs */
    private fun isHighEndMali(glRenderer: String): Boolean {
        if (!isMali(glRenderer)) return false
        val matcher = Pattern.compile("mali[- ]?g(\\d+)", Pattern.CASE_INSENSITIVE).matcher(glRenderer)
        if (matcher.find()) {
            val modelNumber = matcher.group(1)?.toIntOrNull() ?: return false
            return modelNumber >= 710
        }
        return false
    }

    private fun getAdrenoVersionString(glRenderer: String): String? {
        val lower = glRenderer.lowercase().trim()
        if ("adreno" !in lower) return null
        val matcher = Pattern.compile("(.*)([0-9]{3})(.*)").matcher(lower)
        if (!matcher.matches()) return null
        val modelNumber = matcher.group(2)
        LimeLog.info("Found Adreno GPU: $modelNumber")
        return modelNumber
    }

    private fun getAdrenoRendererModelNumber(glRenderer: String): Int =
        getAdrenoVersionString(glRenderer)?.toIntOrNull() ?: -1

    private fun isLowEndSnapdragonRenderer(glRenderer: String): Boolean {
        val modelNumber = getAdrenoVersionString(glRenderer) ?: return false
        return modelNumber[1] == '0'
    }

    private fun isSnapdragonGSeries(glRenderer: String): Boolean {
        val lower = glRenderer.lowercase().trim()
        if ("adreno" !in lower) return false
        return Pattern.compile("(.*)(a[0-9]{2})(.*)").matcher(lower).matches()
    }

    private fun isGLES31SnapdragonRenderer(glRenderer: String) =
        getAdrenoRendererModelNumber(glRenderer) >= 400

    // ==================== Initialization ====================

    @JvmStatic
    fun initialize(context: Context, glRenderer: String) {
        if (initialized) return

        // Amazon Fire TV / tablets — confirmed working HEVC + RFI
        if (context.packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
            Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
        ) {
            whitelistedHevcDecoders.add("omx.mtk")
            refFrameInvalidationHevcPrefixes.addAll(listOf("omx.mtk", "c2.mtk"))
            whitelistedHevcDecoders.add("omx.amlogic")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Amlogic HEVC decoders are known to produce artifacts and decoder hangs
                // when HEVC RFI is enabled after packet loss on many Android TV devices.
                // Fire TV devices on Fire OS 7+ (Android 8+) are confirmed working.
                isAmlogicRfiSafe = true
                LimeLog.info("Enabling Amlogic HEVC RFI on Fire OS 7+ device")
            }
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configInfo = activityManager.deviceConfigurationInfo
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            LimeLog.info("OpenGL ES version: ${configInfo.reqGlEsVersion}")

            isLowEndSnapdragon = isLowEndSnapdragonRenderer(glRenderer)
            isSnapdragonGSeries = isSnapdragonGSeries(glRenderer)
            isAdreno620 = getAdrenoRendererModelNumber(glRenderer) == 620

            // Tegra K1+ can do RFI properly
            if (configInfo.reqGlEsVersion >= 0x30000) {
                LimeLog.info("Added omx.nvidia/c2.nvidia to reference frame invalidation support list")
                refFrameInvalidationAvcPrefixes.add("omx.nvidia")
                if (!Build.DEVICE.equals("dragon", ignoreCase = true) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    refFrameInvalidationHevcPrefixes.add("omx.nvidia")
                }
                refFrameInvalidationAvcPrefixes.add("c2.nvidia")
                refFrameInvalidationHevcPrefixes.add("c2.nvidia")

                LimeLog.info("Added omx.qcom/c2.qti to reference frame invalidation support list")
                refFrameInvalidationAvcPrefixes.addAll(listOf("omx.qcom", "c2.qti"))
                refFrameInvalidationHevcPrefixes.addAll(listOf("omx.qcom", "c2.qti"))
            }

            // Qualcomm HEVC whitelist based on Adreno GPU generation
            if (isGLES31SnapdragonRenderer(glRenderer)) {
                LimeLog.info("Added omx.qcom/c2.qti to HEVC decoders based on GLES 3.1+ support")
                whitelistedHevcDecoders.addAll(listOf("omx.qcom", "c2.qti"))
            } else {
                blacklistedDecoderPrefixes.add("OMX.qcom.video.decoder.hevc")
                useFourSlicesPrefixes.add("omx.qcom")
            }

            // Older MediaTek SoCs with PowerVR GPUs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPowerVR(glRenderer)) {
                LimeLog.info("Added omx.mtk to HEVC decoders based on PowerVR GPU")
                whitelistedHevcDecoders.add("omx.mtk")
                if ("GX6" in glRenderer) {
                    LimeLog.info("Added omx.mtk/c2.mtk to RFI list for HEVC")
                    refFrameInvalidationHevcPrefixes.addAll(listOf("omx.mtk", "c2.mtk"))
                }
            }

            // Newer MediaTek Dimensity SoCs with high-end Mali GPUs (G710+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                isHighEndMali(glRenderer) &&
                Build.HARDWARE.lowercase().startsWith("mt")
            ) {
                LimeLog.info("Added omx.mtk/c2.mtk to HEVC decoders and RFI list based on Mali GPU (Dimensity)")
                whitelistedHevcDecoders.addAll(listOf("omx.mtk", "c2.mtk"))
                refFrameInvalidationHevcPrefixes.addAll(listOf("omx.mtk", "c2.mtk"))
            }
        }

        initialized = true
    }

    // ==================== Decoder Matching ====================

    private fun isDecoderInList(decoderList: List<String>, decoderName: String): Boolean {
        check(initialized) { "MediaCodecHelper must be initialized before use" }
        return decoderList.any { prefix ->
            decoderName.length >= prefix.length &&
                decoderName.substring(0, prefix.length).equals(prefix, ignoreCase = true)
        }
    }

    // ==================== Low Latency Capability Probing ====================

    private fun decoderSupportsAndroidRLowLatency(decoderInfo: MediaCodecInfo, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (decoderInfo.getCapabilitiesForType(mimeType)
                        .isFeatureSupported(CodecCapabilities.FEATURE_LowLatency)
                ) {
                    LimeLog.info("Low latency decoding mode supported (FEATURE_LowLatency)")
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun decoderSupportsKnownVendorLowLatencyOption(decoderName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var testCodec: MediaCodec? = null
            try {
                testCodec = MediaCodec.createByCodecName(decoderName)
                val supportedParams = testCodec.supportedVendorParameters

                // Log all supported vendor parameters for diagnostics
                LimeLog.info("$decoderName supported vendor params (${supportedParams.size}):")
                for (param in supportedParams) {
                    LimeLog.info("  $param")
                }

                // Match against known low-latency options
                for (supportedOption in supportedParams) {
                    for (knownOption in knownVendorLowLatencyOptions) {
                        if (supportedOption.equals(knownOption, ignoreCase = true)) {
                            LimeLog.info("$decoderName supports known low latency option: $supportedOption")
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                testCodec?.release()
            }
        }
        return false
    }

    private fun decoderSupportsMaxOperatingRate(decoderName: String): Boolean {
        // Operate at maximum rate to lower latency on Qualcomm platforms.
        // Crashes on Snapdragon 765G (Adreno 620) and non-Qualcomm devices.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            (isDecoderInList(qualcommDecoderPrefixes, decoderName) || isSnapdragonGSeries) &&
            !isAdreno620
    }

    // ==================== Vendor-Specific Low Latency Configuration ====================
    //
    // Each vendor's decoder has unique low-latency extensions. The methods below apply
    // vendor-specific parameters to the MediaFormat. They share a common pattern:
    //   1. Set parameters that are known to work (no try-catch needed)
    //   2. Try speculative parameters in try-catch blocks (may not exist on all FW versions)
    //
    // When adding new vendor params here, also add the most representative key to
    // knownVendorLowLatencyOptions above.

    private fun applyQualcommVendorParams(
        videoFormat: MediaFormat,
        tryNumber: Int,
        hdr10PlusModeSelected: Boolean,
    ) {
        // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp
        // Picture order is compatible with HDR10+ once output fencing is disabled.
        val outputFenceEnabled = !hdr10PlusModeSelected
        if (QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                tryNumber,
                hdr10PlusModeSelected,
                outputFenceEnabled,
            )
        ) {
            videoFormat.setInteger("vendor.qti-ext-dec-picture-order.enable", 1)
        }
        if (tryNumber < 5) {
            videoFormat.setInteger("vendor.qti-ext-dec-low-latency.enable", 1)

            // CONFIRMED WORKING: Snapdragon Elite, SD8 gen 3, SD8 gen 2
            videoFormat.setInteger("vendor.qti-ext-output-sw-fence-enable.value", 1)
            if (outputFenceEnabled) {
                videoFormat.setInteger("vendor.qti-ext-output-fence.enable", 1)
                videoFormat.setInteger("vendor.qti-ext-output-fence.fence_type", 1)
            }

            videoFormat.setInteger("vendor.qti-ext-dec-info-misr.disable", 1)
            videoFormat.setInteger("vendor.qti-ext-dec-instant-decode.enable", 1)
            videoFormat.setInteger("vendor.qti-ext-dec-error-correction.conceal", 1)
        }
    }

    private fun applyMtkVendorParams(
        videoFormat: MediaFormat,
        tryNumber: Int,
        allowMaxOperatingRate: Boolean
    ) {
        // Tiered MTK low-latency profile (inspired by derflacco/Artemide):
        //   tier 0 = stable baseline (with optional max operating-rate override)
        //   tier 1+ = progressive fallback: shrink queues / drop preload
        // Higher tiers are only reached if a previous attempt failed to start.

        // Core performance params (unchanged across tiers)
        videoFormat.setInteger("vendor.mtk.vdec.cpu.boost.mode", 1)
        videoFormat.setInteger("vendor.mtk.vdec.cpu.boost.mode.value", 2)
        videoFormat.setInteger("vendor.mtk.ext.dolby.vision.cpu-boost", 1)
        videoFormat.setInteger("vendor.mtk.vdec.dvfs.mode", 1)
        videoFormat.setInteger("vendor.mtk.vdec.dvfs.level", 1)

        videoFormat.setInteger("vendor.mtk.vdec.bq.guard.interval.time.value", 2)
        videoFormat.setInteger("vendor.mtk.vdec.buffer.fetch.timeout.ms.value", 2)
        videoFormat.setInteger("vendor.mtk.vdec.vsync.adjust.enable", 0)

        // Queue depth and timeout tuning (tiered)
        // tier 0: depth=3 (your existing safe value)
        // tier 1: depth=2  -> -1 frame of internal queueing
        // tier 2+: depth=1 -> minimum, only as last resort
        val qDepth = when {
            tryNumber >= 2 -> 1
            tryNumber >= 1 -> 2
            else -> 3
        }
        val fetchTimeoutMs = if (tryNumber >= 1) 2 else 4
        runCatching {
            videoFormat.setInteger("vendor.mtk.vdec.buffer.fetch.timeout.ms", fetchTimeoutMs)
            videoFormat.setInteger("vendor.mtk.vdec.bq.guard.interval.time", fetchTimeoutMs)
            videoFormat.setInteger("vendor.mtk.vdec.input.max.queue.depth", qDepth)
            videoFormat.setInteger("vendor.mtk.vdec.output.max.queue.depth", qDepth)
        }

        // Low-latency pipeline
        // preload: tier 0 keeps 1 (smoother first frame); tier 1+ drops to 0
        runCatching {
            videoFormat.setInteger("vendor.mtk.vdec.low-latency.mode", 1)
            videoFormat.setInteger("vendor.mtk.vdec.ultra-low-latency", 0) // Off for stability
            videoFormat.setInteger("vendor.mtk.vdec.disable-idle", 1)
            videoFormat.setInteger(
                "vendor.mtk.vdec.preload.frame.count",
                if (tryNumber >= 1) 0 else 1
            )
        }

        // Decode behavior (unchanged across tiers)
        runCatching {
            videoFormat.setInteger("vendor.mtk.vdec.realtime.priority", 1)
            videoFormat.setInteger("vendor.mtk.vdec.no-reorder", 1)
            videoFormat.setInteger("vendor.mtk.vdec.decode-immediately", 1)
            videoFormat.setInteger("vendor.mtk.vdec.force-max-freq", 1)
        }

        // Optional Android low-latency hint: ask the decoder to run at max clock.
        // Some MTK devices (including newer Dimensity parts) benefit from this,
        // but others regress badly. For example, Hisense U7Q / c2.mtk.hevc.decoder
        // accepts the value but then misses SurfaceFlinger deadlines and accumulates
        // seconds of display latency. Keep it disabled by default and expose it
        // behind a user toggle.
        if (allowMaxOperatingRate) {
            runCatching {
                videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            }
        } else if (tryNumber == 0) {
            LimeLog.info("Skipping MTK max operating rate override (disabled in settings)")
        }
    }

    private fun applyKirinVendorParams(videoFormat: MediaFormat) {
        // https://developer.huawei.com/consumer/cn/forum/topic/0202325564295980115
        videoFormat.setInteger("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req", 1)
        videoFormat.setInteger("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-rdy", -1)

        runCatching {
            videoFormat.setInteger("vendor.hisi-ext-dec-low-latency.enable", 1)
            videoFormat.setInteger("vendor.hisi-ext-dec-output-order.enable", 1)
            videoFormat.setInteger("vendor.hisi-ext-dec-realtime.enable", 1)
        }
    }

    private fun applyExynosVendorParams(videoFormat: MediaFormat) {
        videoFormat.setInteger("vendor.rtc-ext-dec-low-latency.enable", 1)

        runCatching {
            videoFormat.setInteger("vendor.sec.dec.low-latency.enable", 1)
            videoFormat.setInteger("vendor.sec.dec.realtime.enable", 1)
            videoFormat.setInteger("vendor.sec.dec.output-order.enable", 1)
        }
    }

    private fun applyAmlogicVendorParams(videoFormat: MediaFormat) {
        // https://github.com/codewalkerster/android_vendor_amlogic_common_prebuilt_libstagefrighthw/commit/41fefc4e035c476d58491324a5fe7666bfc2989e
        videoFormat.setInteger("vendor.low-latency.enable", 1)

        runCatching {
            videoFormat.setInteger("vendor.amlogic.lowlatency.mode", 1)
            videoFormat.setInteger("vendor.amlogic.tunnel.mode", 0)
            videoFormat.setInteger("vendor.amlogic.frame-skip.enable", 0)
        }
    }

    private fun applyTegraVendorParams(videoFormat: MediaFormat) {
        runCatching {
            videoFormat.setInteger("vendor.nvidia.low-latency.enable", 1)
            videoFormat.setInteger("vendor.nvidia.realtime.enable", 1)
            videoFormat.setInteger("vendor.nvidia.game-streaming.enable", 1)
        }
    }

    // ==================== Main Low Latency Configuration ====================

    @JvmStatic
    fun setDecoderLowLatencyOptions(
        videoFormat: MediaFormat,
        decoderInfo: MediaCodecInfo,
        tryNumber: Int,
        allowMtkMaxOperatingRate: Boolean,
    ): Boolean = setDecoderLowLatencyOptions(
        videoFormat,
        decoderInfo,
        tryNumber,
        allowMtkMaxOperatingRate,
        hdr10PlusModeSelected = false,
    )

    @JvmStatic
    fun setDecoderLowLatencyOptions(
        videoFormat: MediaFormat,
        decoderInfo: MediaCodecInfo,
        tryNumber: Int,
        allowMtkMaxOperatingRate: Boolean,
        hdr10PlusModeSelected: Boolean,
    ): Boolean {
        // Options are tried in order of most to least risky. The decoder will use
        // the first MediaFormat that doesn't fail in configure().
        var setNewOption = false

        // --- Layer 1: Standard Android low-latency APIs ---

        if (tryNumber < 1) {
            // Official Android 11+ low latency option (KEY_LOW_LATENCY)
            videoFormat.setInteger("low-latency", 1)
            setNewOption = true
            // Don't return early even if FEATURE_LowLatency is supported —
            // vendor-specific extensions provide additional latency reduction.
        }

        if (tryNumber < 2 &&
            (!Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
                Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
        ) {
            // OMX.MTK.index.param.video.LowLatencyDecode — works on MTK and Amazon Amlogic devices.
            // Breaks Xiaomi MITV4-ANSM0 on Android 6, so excluded there.
            videoFormat.setInteger("vdec-lowlatency", 1)
            setNewOption = true
        }

        if (tryNumber < 3) {
            // Qualcomm: KEY_OPERATING_RATE=MAX for aggressive clock boosting
            // Others: KEY_PRIORITY=0 (realtime) as a safer alternative
            if (decoderSupportsMaxOperatingRate(decoderInfo.name)) {
                videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
                setNewOption = true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoFormat.setInteger(MediaFormat.KEY_PRIORITY, 0)
                setNewOption = true
            }
        }

        // --- Layer 2: Vendor-specific extensions (Android 8.0+) ---

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decoderName = decoderInfo.name

            when {
                isDecoderInList(qualcommDecoderPrefixes, decoderName) -> if (tryNumber < 5) {
                    applyQualcommVendorParams(
                        videoFormat,
                        tryNumber,
                        hdr10PlusModeSelected,
                    )
                    setNewOption = true
                }
                isDecoderInList(mtkDecoderPrefixes, decoderName) -> if (tryNumber < 4) {
                    applyMtkVendorParams(videoFormat, tryNumber, allowMtkMaxOperatingRate)
                    setNewOption = true
                }
                isDecoderInList(kirinDecoderPrefixes, decoderName) -> if (tryNumber < 4) {
                    applyKirinVendorParams(videoFormat)
                    setNewOption = true
                }
                isDecoderInList(exynosDecoderPrefixes, decoderName) -> if (tryNumber < 4) {
                    applyExynosVendorParams(videoFormat)
                    setNewOption = true
                }
                isDecoderInList(amlogicDecoderPrefixes, decoderName) -> if (tryNumber < 4) {
                    applyAmlogicVendorParams(videoFormat)
                    setNewOption = true
                }
                isDecoderInList(tegraDecoderPrefixes, decoderName) -> if (tryNumber < 4) {
                    applyTegraVendorParams(videoFormat)
                    setNewOption = true
                }
            }
        }

        return setNewOption
    }

    // ==================== Decoder Capability Queries ====================

    @JvmStatic
    fun decoderSupportsFusedIdrFrame(decoderInfo: MediaCodecInfo, mimeType: String): Boolean {
        try {
            if (decoderInfo.getCapabilitiesForType(mimeType)
                    .isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)
            ) {
                LimeLog.info("Decoder supports fused IDR frames (FEATURE_AdaptivePlayback)")
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun decoderSupportsAdaptivePlayback(decoderInfo: MediaCodecInfo, mimeType: String): Boolean {
        if (isDecoderInList(blacklistedAdaptivePlaybackPrefixes, decoderInfo.name)) {
            LimeLog.info("Decoder blacklisted for adaptive playback")
            return false
        }
        try {
            if (decoderInfo.getCapabilitiesForType(mimeType)
                    .isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)
            ) {
                LimeLog.info("Adaptive playback supported (FEATURE_AdaptivePlayback)")
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun decoderNeedsConstrainedHighProfile(decoderName: String) =
        isDecoderInList(constrainedHighProfilePrefixes, decoderName)

    @JvmStatic
    fun decoderCanDirectSubmit(decoderName: String) =
        isDecoderInList(directSubmitPrefixes, decoderName) && !isExynos4Device()

    @JvmStatic
    fun decoderNeedsSpsBitstreamRestrictions(decoderName: String) =
        isDecoderInList(spsFixupBitstreamFixupDecoderPrefixes, decoderName)

    @JvmStatic
    fun decoderNeedsBaselineSpsHack(decoderName: String) =
        isDecoderInList(baselineProfileHackPrefixes, decoderName)

    @JvmStatic
    fun getDecoderOptimalSlicesPerFrame(decoderName: String): Byte =
        if (isDecoderInList(useFourSlicesPrefixes, decoderName)) 4 else 1

    // ==================== Reference Frame Invalidation ====================

    @JvmStatic
    fun decoderSupportsRefFrameInvalidationAvc(decoderName: String, videoHeight: Int): Boolean {
        if (videoHeight > 720 && isLowEndSnapdragon) return false
        if (Build.DEVICE == "b3" || Build.DEVICE == "b5") return false
        return isDecoderInList(refFrameInvalidationAvcPrefixes, decoderName)
    }

    @JvmStatic
    fun decoderSupportsRefFrameInvalidationHevc(decoderInfo: MediaCodecInfo): Boolean {
        // If the decoder supports FEATURE_LowLatency or any vendor low latency option,
        // we use that as an indication that it can handle HEVC RFI without excessive buffering.
        if (decoderSupportsAndroidRLowLatency(decoderInfo, "video/hevc") ||
            decoderSupportsKnownVendorLowLatencyOption(decoderInfo.name)
        ) {
            return if (!isDecoderInList(amlogicDecoderPrefixes, decoderInfo.name)) {
                LimeLog.info("Enabling HEVC RFI based on low latency option support")
                true
            } else if (isAmlogicRfiSafe) {
                LimeLog.info("Enabling HEVC RFI on confirmed-safe Amlogic device")
                true
            } else {
                LimeLog.info("Not enabling HEVC RFI on unconfirmed Amlogic decoder: ${decoderInfo.name}")
                false
            }
        }
        return isDecoderInList(refFrameInvalidationHevcPrefixes, decoderInfo.name)
    }

    @JvmStatic
    fun decoderSupportsRefFrameInvalidationAv1(decoderInfo: MediaCodecInfo): Boolean {
        if (decoderSupportsAndroidRLowLatency(decoderInfo, "video/av01") ||
            decoderSupportsKnownVendorLowLatencyOption(decoderInfo.name)
        ) {
            LimeLog.info("Enabling AV1 RFI based on low latency option support")
            return true
        }
        return false
    }

    // ==================== HEVC / AV1 Whitelist ====================

    @JvmStatic
    fun decoderIsWhitelistedForHevc(decoderInfo: MediaCodecInfo): Boolean {
        // Reject software decoders
        if ("sw" in decoderInfo.name) {
            LimeLog.info("Disallowing HEVC on software decoder: ${decoderInfo.name}")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (!decoderInfo.isHardwareAccelerated || decoderInfo.isSoftwareOnly)
        ) {
            LimeLog.info("Disallowing HEVC on software decoder: ${decoderInfo.name}")
            return false
        }

        // Media performance class 12+ → assume any HW HEVC decoder is good enough
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LimeLog.info("Media performance class: ${Build.VERSION.MEDIA_PERFORMANCE_CLASS}")
            if (Build.VERSION.MEDIA_PERFORMANCE_CLASS >= Build.VERSION_CODES.S) {
                LimeLog.info("Allowing HEVC based on media performance class")
                return true
            }
        }

        // FEATURE_LowLatency → modern enough for streaming
        if (decoderSupportsAndroidRLowLatency(decoderInfo, "video/hevc")) {
            LimeLog.info("Allowing HEVC based on FEATURE_LowLatency support")
            return true
        }

        return isDecoderInList(whitelistedHevcDecoders, decoderInfo.name)
    }

    @JvmStatic
    fun isDecoderWhitelistedForAv1(decoderInfo: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if ("sw" in decoderInfo.name) {
            LimeLog.info("Disallowing AV1 on software decoder: ${decoderInfo.name}")
            return false
        }
        if (!decoderInfo.isHardwareAccelerated || decoderInfo.isSoftwareOnly) {
            LimeLog.info("Disallowing AV1 on software decoder: ${decoderInfo.name}")
            return false
        }
        return true
    }

    // ==================== Decoder Enumeration ====================

    @SuppressLint("NewApi")
    private fun getMediaCodecList(): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()

    @JvmStatic
    @Throws(Exception::class)
    fun dumpDecoders(): String = buildString {
        for (codecInfo in getMediaCodecList()) {
            if (codecInfo.isEncoder) continue
            append("Decoder: ").append(codecInfo.name).append("\n")
            for (type in codecInfo.supportedTypes) {
                append("\t").append(type).append("\n")
                val caps = codecInfo.getCapabilitiesForType(type)
                for (profile in caps.profileLevels) {
                    append("\t\t").append(profile.profile).append(" ").append(profile.level).append("\n")
                }
            }
        }
    }

    private fun findPreferredDecoder(): MediaCodecInfo? {
        check(initialized) { "MediaCodecHelper must be initialized before use" }
        for (preferredDecoder in preferredDecoders) {
            for (codecInfo in getMediaCodecList()) {
                if (codecInfo.isEncoder) continue
                if (preferredDecoder.equals(codecInfo.name, ignoreCase = true)) {
                    LimeLog.info("Preferred decoder choice is ${codecInfo.name}")
                    return codecInfo
                }
            }
        }
        return null
    }

    private fun isCodecBlacklisted(codecInfo: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!SHOULD_BYPASS_SOFTWARE_BLOCK && codecInfo.isSoftwareOnly) {
                LimeLog.info("Skipping software-only decoder: ${codecInfo.name}")
                return true
            }
        }
        if (isDecoderInList(blacklistedDecoderPrefixes, codecInfo.name)) {
            LimeLog.info("Skipping blacklisted decoder: ${codecInfo.name}")
            return true
        }
        return false
    }

    @JvmStatic
    fun findFirstDecoder(mimeType: String): MediaCodecInfo? {
        for (codecInfo in getMediaCodecList()) {
            if (codecInfo.isEncoder) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias) continue
            for (mime in codecInfo.supportedTypes) {
                if (mime.equals(mimeType, ignoreCase = true)) {
                    if (isCodecBlacklisted(codecInfo)) continue
                    LimeLog.info("First decoder choice is ${codecInfo.name}")
                    return codecInfo
                }
            }
        }
        return null
    }

    @JvmStatic
    fun findProbableSafeDecoder(mimeType: String, requiredProfile: Int): MediaCodecInfo? {
        findPreferredDecoder()?.let { return it }
        return try {
            findKnownSafeDecoder(mimeType, requiredProfile)
        } catch (e: Exception) {
            findFirstDecoder(mimeType)
        }
    }

    @Throws(Exception::class)
    private fun findKnownSafeDecoder(mimeType: String, requiredProfile: Int): MediaCodecInfo? {
        // Some devices have two decoder sets. The first (C2) may lack FEATURE_LowLatency,
        // but the second (OMX) supports it. We prefer decoders with FEATURE_LowLatency.
        for (round in 0..1) {
            for (codecInfo in getMediaCodecList()) {
                if (codecInfo.isEncoder) continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias) continue

                for (mime in codecInfo.supportedTypes) {
                    if (mime.equals(mimeType, ignoreCase = true)) {
                        LimeLog.info("Examining decoder capabilities of ${codecInfo.name} (round ${round + 1})")
                        if (isCodecBlacklisted(codecInfo)) continue

                        if (round == 0 && !decoderSupportsAndroidRLowLatency(codecInfo, mime)) {
                            LimeLog.info("Skipping decoder that lacks FEATURE_LowLatency for round 1")
                            continue
                        }

                        if (requiredProfile != -1) {
                            val caps = codecInfo.getCapabilitiesForType(mime)
                            val hasProfile = caps.profileLevels.any { it.profile == requiredProfile }
                            if (hasProfile) {
                                LimeLog.info("Decoder ${codecInfo.name} supports required profile")
                                return codecInfo
                            }
                            LimeLog.info("Decoder ${codecInfo.name} does NOT support required profile")
                        } else {
                            return codecInfo
                        }
                    }
                }
            }
        }
        return null
    }

    // ==================== Utility ====================

    @JvmStatic
    @Throws(Exception::class)
    fun readCpuinfo(): String = buildString {
        BufferedReader(FileReader(File("/proc/cpuinfo"))).use { br ->
            var ch: Int
            while (br.read().also { ch = it } != -1) {
                append(ch.toChar())
            }
        }
    }

    private fun stringContainsIgnoreCase(string: String, substring: String) =
        string.lowercase(Locale.ENGLISH).contains(substring.lowercase(Locale.ENGLISH))

    @JvmStatic
    fun isExynos4Device(): Boolean {
        try {
            val cpuInfo = readCpuinfo()
            if (stringContainsIgnoreCase(cpuInfo, "SMDK4")) {
                LimeLog.info("Found SMDK4 in /proc/cpuinfo")
                return true
            }
            if (stringContainsIgnoreCase(cpuInfo, "Exynos 4")) {
                LimeLog.info("Found Exynos 4 in /proc/cpuinfo")
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val files = File("/sys/devices/system").listFiles()
            if (files != null) {
                for (f in files) {
                    if (stringContainsIgnoreCase(f.name, "exynos4")) {
                        LimeLog.info("Found exynos4 in /sys/devices/system")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}
