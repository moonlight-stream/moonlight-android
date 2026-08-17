package com.limelight.binding.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.nvstream.HdrModePolicy
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import org.jcodec.codecs.h264.io.model.SeqParameterSet
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MediaCodecDecoderRenderer(
    activity: Activity,
    private val prefs: PreferenceConfiguration,
    private val crashListener: CrashListener,
    private val consecutiveCrashCount: Int,
    meteredData: Boolean,
    requestedHdr: Boolean,
    initialHdr10PlusRequested: Boolean,
    private val glRenderer: String,
    private val perfListener: PerfOverlayListener
) : VideoDecoderRenderer() {

    companion object {
        private const val USE_FRAME_RENDER_TIME = true
        private const val FRAME_RENDER_TIME_ONLY = USE_FRAME_RENDER_TIME && false

        private const val CR_MAX_TRIES = 10
        private const val CR_RECOVERY_TYPE_NONE = 0
        private const val CR_RECOVERY_TYPE_FLUSH = 1
        private const val CR_RECOVERY_TYPE_RESTART = 2
        private const val CR_RECOVERY_TYPE_RESET = 3
        private const val MAX_DECODER_CONFIGURATION_TRIES = 8
        // Each thread that touches the MediaCodec object or any associated buffers must have a flag
        // here and must call doCodecRecoveryIfRequired() on a regular basis.
        private const val CR_FLAG_INPUT_THREAD = 0x1
        private const val CR_FLAG_RENDER_THREAD = 0x2
        const val CR_FLAG_CHOREOGRAPHER = 0x4
        private const val CR_FLAG_ALL = CR_FLAG_INPUT_THREAD or CR_FLAG_RENDER_THREAD or CR_FLAG_CHOREOGRAPHER

        private const val EXCEPTION_REPORT_DELAY_MS = 3000
        private const val FRAMEGEN_SURFACE_SWITCH_MAX_RETRIES = 20
        private const val FRAMEGEN_SURFACE_SWITCH_RETRY_DELAY_MS = 50L
        // Vendor codecs expose far fewer input buffers in practice. The generous fixed capacity
        // keeps the steady-state queue allocation-free without risking normal callback loss.
        private const val ASYNC_INPUT_BUFFER_QUEUE_CAPACITY = 256

    }

    // Used on versions < 5.0
    @Suppress("unused")
    private var legacyInputBuffers: Array<ByteBuffer>? = null

    private val avcDecoder: MediaCodecInfo?
    private val hevcDecoder: MediaCodecInfo?
    private val av1Decoder: MediaCodecInfo?

    private val vpsBuffers = ArrayList<ByteArray>()
    private val spsBuffers = ArrayList<ByteArray>()
    private val ppsBuffers = ArrayList<ByteArray>()
    private var submittedCsd = false
    private var currentHdrMetadata: ByteArray? = null
    private var hdrDataSpace = 0 // Configured DataSpace for HDR content, re-applied after format changes

    private var nextInputBufferIndex = -1
    private var nextInputBuffer: ByteBuffer? = null

    private val context: Context = activity
    private val activity: Activity = activity
    private var videoDecoder: MediaCodec? = null
    private var rendererThread: Thread? = null
    private var needsSpsBitstreamFixup = false
    private var isExynos4 = false
    private var adaptivePlayback = false
    private var directSubmit = false
    private var fusedIdrFrame = false
    private var constrainedHighProfile = false
    private var refFrameInvalidationAvc = false
    private var refFrameInvalidationHevc = false
    private var refFrameInvalidationAv1 = false
    private var requestedHdr10Plus = false
    private val hdr10PlusOutputObserver = Hdr10PlusOutputObserver()
    private val hdrProfileSelector = HdrDecoderProfileSelector(
        width = prefs.width,
        height = prefs.height,
        frameRate = prefs.fps,
        fullRange = prefs.fullRange,
    )
    private val hevcHdr10PlusFormatSupported by lazy {
        hdrProfileSelector.supportsHdr10PlusFormat(
            hevcDecoder,
            HdrDecoderProfileSelector.MIME_HEVC,
        )
    }
    private val av1Hdr10PlusFormatSupported by lazy {
        hdrProfileSelector.supportsHdr10PlusFormat(
            av1Decoder,
            HdrDecoderProfileSelector.MIME_AV1,
        )
    }
    private val hevcHdr10PlusEligible: Boolean
        get() = requestedHdr10Plus && hevcHdr10PlusFormatSupported
    private val av1Hdr10PlusEligible: Boolean
        get() = requestedHdr10Plus && av1Hdr10PlusFormatSupported
    private val optimalSlicesPerFrame: Byte
    private var refFrameInvalidationActive = false

    /**
     * 首帧解码完成后只触发一次的回调（线程不固定，调用方需自行切换 UI 线程）。
     * 用于让上层（progress overlay）在视频真正开始出画时再隐藏 loading，而不是凭
     * connectionStarted 这种连接级回调（首帧通常还没解码出来）。
     */
    @Volatile
    var firstFrameCallback: (() -> Unit)? = null
    @Volatile
    private var firstFrameDelivered = false
    private var initialWidth = 0
    private var initialHeight = 0
    private var videoFormat = 0
    private var renderTarget: SurfaceHolder? = null
    /**
     * 阶段 3 framegen 注入点：非 null 时，MediaCodec.configure() 用该 Surface 替代
     * renderTarget.surface，把解码帧导向 ImageReader 而不是直接上屏。null 表示走原路径。
     * 仅在 decoder 未配置（[configureAndStartDecoder] 调用前）期间设置才生效；
     * 运行中切换需要先 [pauseProcessing] / [resumeProcessing]。
     */
    @Volatile
    var framegenSurface: Surface? = null
    @Volatile
    private var framegenOutputSwitchPending = false
    private val framegenOutputSwitchReady = AtomicBoolean(false)
    private val framegenOutputSwitchRequested = AtomicBoolean(false)
    private val framegenOutputSwitchRetryCount = AtomicInteger(0)
    @Volatile
    private var stopping = false
    private var reportedCrash = false
    private var foreground = true
    @Volatile
    private var isProcessingPaused = false
    private var needsIdrOnResume = false

    private val codecRecoveryType = AtomicInteger(CR_RECOVERY_TYPE_NONE)
    private val codecRecoveryMonitor = Any()
    private var codecRecoveryThreadQuiescedFlags = 0
    private var codecRecoveryAttempts = 0

    private var inputFormat: MediaFormat? = null
    private var outputFormat: MediaFormat? = null
    private val stableOutputFormatTracker = StableOutputFormatTracker()
    private var configuredFormat: MediaFormat? = null

    private var needsBaselineSpsHack = false
    private var savedSps: SeqParameterSet? = null

    private var initialException: RendererException? = null
    private var initialExceptionTimestamp: Long = 0

    private val activeWindowVideoStats = VideoStats()
    private val lastWindowVideoStats = VideoStats()
    private val globalVideoStats = VideoStats()
    private val frameIntervalTracker = FrameIntervalTracker(600)

    private var lastTimestampUs: Long = 0
    private var lastFrameNumber = 0
    private var refreshRate = 0

    // Fixed primitive ring: local codec PTS -> enqueue time and host cadence PTS.
    // This metadata is bounded by codec in-flight buffers and accessed across input/output threads.
    private val frameTimestampTracker = FrameTimestampTracker()

    // 本地单调 timestampUs(送入 MediaCodec 的 PTS) -> host 节奏 PTS(presentationTimeUs, 微秒)
    // host PTS 源自主机捕获时刻(见 common-c RtpVideoQueue.c: packet->timestamp * 1000 / PTS_DIVISOR)，
    // 是三端同源的呈现节奏基准。之前在 JNI 边界被丢弃，现打通供 host-cadence pacing 使用。
    // ---- host-cadence 去抖呈现时钟(alpha-beta / PI 时钟恢复，移植自鸿蒙 native_render，已离线仿真验证) ----
    // 默认关闭：置 true 前，全部走原有 pacing，行为零变化。开启后在偏平滑档用 host PTS 复现主机出帧节奏，
    // 消除"本地 vsync 网格 vs 主机节奏"错拍造成的微判抖；代价是自适应缓冲延迟(净 LAN≈1ms，抖动网络更大)。
    // TODO(on-device): 接入设置项/按网络自适应开启，并在真机上标定 Kp/Ki/cushion。
    private val useHostCadencePacing = false
    // host PTS 仅由异步输出回调消费；直渲染档或 PRECISE_SYNC 两步呈现激活时维护旁路映射。
    private val needsHostPtsMapping: Boolean
        get() = asyncModeEnabled &&
            (useHostCadencePacing || framePacingController.isHostCadencePreciseSyncActive())
    // 直渲染档(MAX_SMOOTHNESS/CAP_FPS)无 vsync snap 兜底：一帧迟到即可见 hitch，故用较大 cushion
    // (3×MAD, floor 1ms)覆盖抖动分布。PRECISE_SYNC 路径(有 snap)将另建低 cushion 实例。
    private val hostCadenceClock =
        HostCadenceClock(cushionMul = 3.0, cushionFloorNs = 1_000_000L, enableDebugStats = BuildConfig.DEBUG)

    // Frame pacing and performance management (extracted)
    private val framePacingController: FramePacingController
    private val perfBoostManager: PerformanceBoostManager

    // H.264 SPS patching (extracted)
    private var spsPatcher: SpsPatcher? = null

    // Async codec mode (API 30+): eliminates polling overhead for input/output buffers
    private var asyncModeEnabled = false
    private var availableInputBuffers: ArrayBlockingQueue<Int>? = null
    private var codecCallbackThread: HandlerThread? = null
    private var codecCallbackHandler: Handler? = null

    // LinearBlock zero-copy input (API 30+)
    private var linearBlockEnabled = false
    private var codecNameArray: Array<String>? = null

    private var numSpsIn = 0
    private var numPpsIn = 0
    private var numVpsIn = 0
    private var numFramesIn = 0
    private var numFramesOut = 0

    private fun findAvcDecoder(): MediaCodecInfo? {
        var decoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
        if (decoder == null) {
            decoder = MediaCodecHelper.findFirstDecoder("video/avc")
        }
        return decoder
    }

    private fun decoderCanMeetPerformancePoint(caps: MediaCodecInfo.VideoCapabilities, prefs: PreferenceConfiguration): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val targetPerfPoint = MediaCodecInfo.VideoCapabilities.PerformancePoint(prefs.width, prefs.height, prefs.fps)
            val perfPoints = caps.supportedPerformancePoints
            if (perfPoints != null) {
                for (perfPoint in perfPoints) {
                    // If we find a performance point that covers our target, we're good to go
                    if (perfPoint.covers(targetPerfPoint)) {
                        return true
                    }
                }

                // We had performance point data but none met the specified streaming settings
                return false
            }

            // Fall-through to try the Android M API if there's no performance point data
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // We'll ask the decoder what it can do for us at this resolution and see if our
                // requested frame rate falls below or inside the range of achievable frame rates.
                val fpsRange = caps.getAchievableFrameRatesFor(prefs.width, prefs.height)
                if (fpsRange != null) {
                    return prefs.fps <= fpsRange.upper
                }

                // Fall-through to try the Android L API if there's no performance point data
            } catch (e: IllegalArgumentException) {
                // Video size not supported at any frame rate
                return false
            }
        }

        // As a last resort, we will use areSizeAndRateSupported() which is explicitly NOT a
        // performance metric, but it can work at least for the purpose of determining if
        // the codec is going to die when given a stream with the specified settings.
        return caps.areSizeAndRateSupported(prefs.width, prefs.height, prefs.fps.toDouble())
    }

    private fun decoderCanMeetPerformancePointWithHevcAndNotAvc(
        hevcDecoderInfo: MediaCodecInfo,
        avcDecoderInfo: MediaCodecInfo,
        prefs: PreferenceConfiguration
    ): Boolean {
        val avcCaps = avcDecoderInfo.getCapabilitiesForType("video/avc").videoCapabilities ?: return false
        val hevcCaps = hevcDecoderInfo.getCapabilitiesForType("video/hevc").videoCapabilities ?: return false
        return !decoderCanMeetPerformancePoint(avcCaps, prefs) && decoderCanMeetPerformancePoint(hevcCaps, prefs)
    }

    private fun decoderCanMeetPerformancePointWithAv1AndNotHevc(
        av1DecoderInfo: MediaCodecInfo,
        hevcDecoderInfo: MediaCodecInfo,
        prefs: PreferenceConfiguration
    ): Boolean {
        val av1Caps = av1DecoderInfo.getCapabilitiesForType("video/av01").videoCapabilities ?: return false
        val hevcCaps = hevcDecoderInfo.getCapabilitiesForType("video/hevc").videoCapabilities ?: return false
        return !decoderCanMeetPerformancePoint(hevcCaps, prefs) && decoderCanMeetPerformancePoint(av1Caps, prefs)
    }

    private fun decoderCanMeetPerformancePointWithAv1AndNotAvc(
        av1DecoderInfo: MediaCodecInfo,
        avcDecoderInfo: MediaCodecInfo,
        prefs: PreferenceConfiguration
    ): Boolean {
        val avcCaps = avcDecoderInfo.getCapabilitiesForType("video/avc").videoCapabilities ?: return false
        val av1Caps = av1DecoderInfo.getCapabilitiesForType("video/av01").videoCapabilities ?: return false
        return !decoderCanMeetPerformancePoint(avcCaps, prefs) && decoderCanMeetPerformancePoint(av1Caps, prefs)
    }

    private fun findHevcDecoder(prefs: PreferenceConfiguration, meteredNetwork: Boolean, requestedHdr: Boolean): MediaCodecInfo? {
        // Don't return anything if H.264 is forced
        if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_H264) {
            return null
        }

        // In auto mode, we should still prepare HEVC as a fallback even if AV1 is available
        // The server will negotiate the final codec choice based on what it supports
        if (prefs.videoFormat == PreferenceConfiguration.FormatOption.AUTO) {
            val av1DecoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/av01", -1)
            if (av1DecoderInfo != null && MediaCodecHelper.isDecoderWhitelistedForAv1(av1DecoderInfo)) {
                LimeLog.info("AV1 decoder available in auto mode, but still preparing HEVC as fallback")
                // Continue to prepare HEVC decoder instead of returning null
            }
        }

        // We don't try the first HEVC decoder. We'd rather fall back to hardware accelerated AVC instead
        //
        // We need HEVC Main profile, so we could pass that constant to findProbableSafeDecoder, however
        // some decoders (at least Qualcomm's Snapdragon 805) don't properly report support
        // for even required levels of HEVC.
        val hevcDecoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1)
        if (hevcDecoderInfo != null) {
            if (!MediaCodecHelper.decoderIsWhitelistedForHevc(hevcDecoderInfo)) {
                LimeLog.info("Found HEVC decoder, but it's not whitelisted - " + hevcDecoderInfo.name)

                // Force HEVC enabled if the user asked for it
                if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC) {
                    LimeLog.info("Forcing HEVC enabled despite non-whitelisted decoder")
                }
                // HDR implies HEVC forced on, since HEVCMain10HDR10 is required for HDR.
                else if (requestedHdr) {
                    LimeLog.info("Forcing HEVC enabled for HDR streaming")
                }
                // > 4K streaming also requires HEVC, so force it on there too.
                else if (prefs.width > 4096 || prefs.height > 4096) {
                    LimeLog.info("Forcing HEVC enabled for over 4K streaming")
                }
                // Use HEVC if the H.264 decoder is unable to meet the performance point
                else if (avcDecoder != null && decoderCanMeetPerformancePointWithHevcAndNotAvc(hevcDecoderInfo, avcDecoder, prefs)) {
                    LimeLog.info("Using non-whitelisted HEVC decoder to meet performance point")
                } else {
                    return null
                }
            }
        }

        return hevcDecoderInfo
    }

    private fun findAv1Decoder(prefs: PreferenceConfiguration): MediaCodecInfo? {
        // Use AV1 if explicitly requested or in auto mode
        if (prefs.videoFormat != PreferenceConfiguration.FormatOption.FORCE_AV1 &&
            prefs.videoFormat != PreferenceConfiguration.FormatOption.AUTO
        ) {
            return null
        }

        val decoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/av01", -1)
        if (decoderInfo != null) {
            if (!MediaCodecHelper.isDecoderWhitelistedForAv1(decoderInfo)) {
                LimeLog.info("Found AV1 decoder, but it's not whitelisted - " + decoderInfo.name)

                // Force AV1 enabled if the user asked for it
                if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1) {
                    LimeLog.info("Forcing AV1 enabled despite non-whitelisted decoder")
                }
                // Use AV1 if the HEVC decoder is unable to meet the performance point
                else if (hevcDecoder != null && decoderCanMeetPerformancePointWithAv1AndNotHevc(decoderInfo, hevcDecoder, prefs)) {
                    LimeLog.info("Using non-whitelisted AV1 decoder to meet performance point")
                }
                // Use AV1 if the H.264 decoder is unable to meet the performance point and we have no HEVC decoder
                else if (hevcDecoder == null && avcDecoder != null && decoderCanMeetPerformancePointWithAv1AndNotAvc(decoderInfo, avcDecoder!!, prefs)) {
                    LimeLog.info("Using non-whitelisted AV1 decoder to meet performance point")
                } else {
                    return null
                }
            }
        }

        return decoderInfo
    }

    fun setRenderTarget(renderTarget: SurfaceHolder) {
        this.renderTarget = renderTarget
    }

    fun setFramegenCaptureSwitchReady(ready: Boolean) {
        framegenOutputSwitchReady.set(ready)
        if (ready) {
            requestFramegenOutputSurfaceSwitch("framegen prewarm ready")
        }
    }

    private fun requestFramegenOutputSurfaceSwitch(reason: String) {
        if (!framegenOutputSwitchPending || stopping) {
            return
        }
        if (!framegenOutputSwitchReady.get()) {
            return
        }
        if (!framegenOutputSwitchRequested.compareAndSet(false, true)) {
            return
        }

        val switchAction = Runnable {
            val targetSurface = framegenSurface
            val decoder = videoDecoder
            if (!framegenOutputSwitchPending || stopping || targetSurface == null || decoder == null) {
                framegenOutputSwitchPending = false
                framegenOutputSwitchRetryCount.set(0)
                return@Runnable
            }

            val startMs = SystemClock.uptimeMillis()
            try {
                applyHdrDataSpace(targetSurface, "framegen capture")
                decoder.setOutputSurface(targetSurface)
                framegenOutputSwitchPending = false
                framegenOutputSwitchRetryCount.set(0)
                LimeLog.info(
                    "Framegen delayed capture switch to ImageReader after $reason " +
                        "elapsed=${SystemClock.uptimeMillis() - startMs}ms"
                )
            } catch (t: Throwable) {
                val retry = framegenOutputSwitchRetryCount.incrementAndGet()
                if (!stopping && retry <= FRAMEGEN_SURFACE_SWITCH_MAX_RETRIES) {
                    framegenOutputSwitchRequested.set(false)
                    LimeLog.warning(
                        "Framegen delayed capture switch retry $retry/" +
                            "$FRAMEGEN_SURFACE_SWITCH_MAX_RETRIES after $reason: " +
                            "${t.javaClass.simpleName}: ${t.message}"
                    )
                    val retryAction = Runnable {
                        requestFramegenOutputSurfaceSwitch("$reason retry")
                    }
                    codecCallbackHandler?.postDelayed(
                        retryAction,
                        FRAMEGEN_SURFACE_SWITCH_RETRY_DELAY_MS
                    ) ?: retryAction.run()
                    return@Runnable
                }
                framegenOutputSwitchPending = false
                framegenOutputSwitchRetryCount.set(0)
                LimeLog.warning(
                    "Framegen delayed capture switch failed after $reason: " +
                        "${t.javaClass.simpleName}: ${t.message}; staying on direct SurfaceView"
                )
            }
        }

        codecCallbackHandler?.post(switchAction) ?: switchAction.run()
    }

    private fun applyHdrDataSpace(surface: Surface, reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || hdrDataSpace == 0) {
            return
        }

        val result = MoonBridge.nativeSetSurfaceDataSpace(surface, hdrDataSpace)
        LimeLog.info(
            "Surface DataSpace ($reason): 0x${Integer.toHexString(hdrDataSpace)} result=$result"
        )
    }

    private fun reapplyHdrDataSpaceIfChanged(surface: Surface, reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || hdrDataSpace == 0) {
            return
        }

        val currentDataSpace = MoonBridge.nativeGetSurfaceDataSpace(surface)
        if (currentDataSpace != hdrDataSpace) {
            applyHdrDataSpace(surface, reason)
        }
    }

    init {
        //dumpDecoders()

        perfBoostManager = PerformanceBoostManager(activity)
        framePacingController = FramePacingController(object : FramePacingController.Callbacks {
            override fun onFrameRendered() {
                activeWindowVideoStats.totalFramesRendered++
                frameIntervalTracker.recordFrame()
            }

            override fun onDecoderException(e: IllegalStateException): Boolean {
                return handleDecoderException(e)
            }

            override fun onCodecRecoveryCheck(flag: Int): Boolean {
                return doCodecRecoveryIfRequired(flag)
            }
        }, prefs, activity)

        avcDecoder = findAvcDecoder()
        if (avcDecoder != null) {
            LimeLog.info("Selected AVC decoder: " + avcDecoder.name)
        } else {
            LimeLog.warning("No AVC decoder found")
        }

        hevcDecoder = findHevcDecoder(prefs, meteredData, requestedHdr)
        if (hevcDecoder != null) {
            LimeLog.info("Selected HEVC decoder: " + hevcDecoder.name)
        } else {
            LimeLog.info("No HEVC decoder found")
        }

        av1Decoder = findAv1Decoder(prefs)
        if (av1Decoder != null) {
            LimeLog.info("Selected AV1 decoder: " + av1Decoder.name)
        } else {
            LimeLog.info("No AV1 decoder found")
        }

        setHdr10PlusRequested(initialHdr10PlusRequested)

        // Set attributes that are queried in getCapabilities(). This must be done here
        // because getCapabilities() may be called before setup() in current versions of the common
        // library. The limitation of this is that we don't know whether we're using HEVC or AVC.
        var avcOptimalSlicesPerFrame: Byte = 0
        var hevcOptimalSlicesPerFrame: Byte = 0
        if (avcDecoder != null) {
            directSubmit = MediaCodecHelper.decoderCanDirectSubmit(avcDecoder.name)
            refFrameInvalidationAvc = MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(avcDecoder.name, prefs.height)
            avcOptimalSlicesPerFrame = MediaCodecHelper.getDecoderOptimalSlicesPerFrame(avcDecoder.name)

            if (directSubmit) {
                LimeLog.info("Decoder " + avcDecoder.name + " will use direct submit")
            }
            if (refFrameInvalidationAvc) {
                LimeLog.info("Decoder " + avcDecoder.name + " will use reference frame invalidation for AVC")
            }
            LimeLog.info("Decoder " + avcDecoder.name + " wants " + avcOptimalSlicesPerFrame + " slices per frame")
        }

        if (hevcDecoder != null) {
            refFrameInvalidationHevc = MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(hevcDecoder)
            hevcOptimalSlicesPerFrame = MediaCodecHelper.getDecoderOptimalSlicesPerFrame(hevcDecoder.name)

            if (refFrameInvalidationHevc) {
                LimeLog.info("Decoder " + hevcDecoder.name + " will use reference frame invalidation for HEVC")
            }

            LimeLog.info("Decoder " + hevcDecoder.name + " wants " + hevcOptimalSlicesPerFrame + " slices per frame")
        }

        if (av1Decoder != null) {
            refFrameInvalidationAv1 = MediaCodecHelper.decoderSupportsRefFrameInvalidationAv1(av1Decoder)

            if (refFrameInvalidationAv1) {
                LimeLog.info("Decoder " + av1Decoder.name + " will use reference frame invalidation for AV1")
            }
        }

        // Use the larger of the two slices per frame preferences
        optimalSlicesPerFrame = maxOf(avcOptimalSlicesPerFrame, hevcOptimalSlicesPerFrame)
        LimeLog.info("Requesting $optimalSlicesPerFrame slices per frame")

        if (consecutiveCrashCount % 2 == 1) {
            refFrameInvalidationAvc = false
            refFrameInvalidationHevc = false
            LimeLog.warning("Disabling RFI due to previous crash")
        }
    }

    fun isHevcSupported(): Boolean = hevcDecoder != null

    fun isAvcSupported(): Boolean = avcDecoder != null

    fun isHevcMain10Supported(): Boolean = hdrProfileSelector.supportsHevcMain10(hevcDecoder)

    fun isHevcMain10Hdr10Supported(): Boolean = hdrProfileSelector.supportsHevcHdr10(hevcDecoder)

    fun isHevcMain10Hdr10PlusSupported(): Boolean =
        hdrProfileSelector.supportsHevcHdr10Plus(hevcDecoder)

    internal fun isHevcHdr10PlusEligible(): Boolean = hevcHdr10PlusEligible

    /** Reconciles the HDR10+ request with the final stream negotiation before capability query. */
    internal fun setHdr10PlusRequested(enabled: Boolean) {
        if (requestedHdr10Plus == enabled) return

        val wasRequested = requestedHdr10Plus
        requestedHdr10Plus = enabled
        if (enabled) {
            LimeLog.info(
                "HDR10+ eligibility: HEVC=$hevcHdr10PlusEligible AV1=$av1Hdr10PlusEligible " +
                    "(API=${Build.VERSION.SDK_INT})"
            )
        } else if (wasRequested) {
            LimeLog.info("HDR10+ request cleared after final HDR negotiation fallback")
        }
    }

    fun isAv1Supported(): Boolean = av1Decoder != null

    fun isAv1Main10Supported(): Boolean = hdrProfileSelector.supportsAv1Main10(av1Decoder)

    fun isAv1Main10Hdr10Supported(): Boolean = hdrProfileSelector.supportsAv1Hdr10(av1Decoder)

    fun isAv1Main10Hdr10PlusSupported(): Boolean =
        hdrProfileSelector.supportsAv1Hdr10Plus(av1Decoder)

    internal fun isAv1Hdr10PlusEligible(): Boolean = av1Hdr10PlusEligible

    private fun handleOutputFormatChanged(format: MediaFormat, source: String) {
        outputFormat = format

        if (!stableOutputFormatTracker.record(format)) return

        LimeLog.info("Output format changed ($source): $format")
        renderTarget?.surface?.let {
            reapplyHdrDataSpaceIfChanged(it, "$source format change")
        }
    }

    fun getPreferredColorSpace(): Int {
        // Default to Rec 709 which is probably better supported on modern devices.
        //
        // We are sticking to Rec 601 on older devices unless the device has an HEVC decoder
        // to avoid possible regressions (and they are < 5% of installed devices). If we have
        // an HEVC decoder, we will use Rec 709 (even for H.264) since we can't choose a
        // colorspace by codec (and it's probably safe to say a SoC with HEVC decoding is
        // plenty modern enough to handle H.264 VUI colorspace info).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || hevcDecoder != null || av1Decoder != null) {
            MoonBridge.COLORSPACE_REC_709
        } else {
            MoonBridge.COLORSPACE_REC_601
        }
    }

    fun getPreferredColorRange(): Int {
        return if (prefs.fullRange) {
            MoonBridge.COLOR_RANGE_FULL
        } else {
            MoonBridge.COLOR_RANGE_LIMITED
        }
    }

    fun notifyVideoForeground() {
        foreground = true
    }

    fun notifyVideoBackground() {
        foreground = false
    }

    fun getActiveVideoFormat(): Int = videoFormat

    fun pauseProcessing() {
        if (isProcessingPaused) {
            return
        }

        LimeLog.info("Pausing video processing and releasing decoder")
        isProcessingPaused = true

        // 停止渲染线程和相关的 handle
        prepareForStop()

        // 释放 MediaCodec 资源
        cleanup()

        // 标记下次恢复时需要 IDR 帧
        needsIdrOnResume = true
    }

    fun resumeProcessing() {
        if (!isProcessingPaused) {
            return
        }

        LimeLog.info("Resuming video processing")

        // 重置停止标志，允许渲染线程运行
        stopping = false

        // 清理输出缓冲区队列，移除 prepareForStop 放入的 -1 信号
        framePacingController.clearBuffers()

        // 重置输入缓冲区索引，避免使用已释放解码器的旧索引
        nextInputBufferIndex = -1
        nextInputBuffer = null

        // 重新创建异步回调线程（如果需要）
        if (asyncModeEnabled && codecCallbackThread == null) {
            availableInputBuffers = ArrayBlockingQueue(ASYNC_INPUT_BUFFER_QUEUE_CAPACITY)
            codecCallbackThread = HandlerThread("Video - Codec", Process.THREAD_PRIORITY_DISPLAY)
            codecCallbackThread!!.start()
            codecCallbackHandler = Handler(codecCallbackThread!!.looper)
            LimeLog.info("MediaCodec async mode re-enabled after resume")
        }

        // 重新初始化解码器
        // 注意：initialWidth, initialHeight 等变量依然保留着
        initializeDecoder(false)

        // 重新启动渲染线程等
        start()

        isProcessingPaused = false
    }

    private fun createBaseMediaFormat(mimeType: String): MediaFormat {
        val videoFormat = MediaFormat.createVideoFormat(mimeType, initialWidth, initialHeight)

        // Avoid setting KEY_FRAME_RATE on Lollipop and earlier to reduce compatibility risk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, refreshRate)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            videoFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 1)
            LimeLog.info("Decoder MediaFormat: KEY_ALLOW_FRAME_DROP enabled")
        }

        // Populate keys for adaptive playback
        if (adaptivePlayback) {
            videoFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, initialWidth)
            videoFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, initialHeight)
        }

        // Android 7.0 adds color options to the MediaFormat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // HLG content from Sunshine uses FULL range. Using LIMITED causes dark images
            // because the HLG OETF/EOTF is applied to the wrong value range on most decoders.
            val useFullRange: Boolean
            if ((getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0 &&
                prefs.hdrMode == MoonBridge.HDR_MODE_HLG
            ) {
                useFullRange = true
            } else {
                useFullRange = (getPreferredColorRange() == MoonBridge.COLOR_RANGE_FULL)
            }
            videoFormat.setInteger(
                MediaFormat.KEY_COLOR_RANGE,
                if (useFullRange) MediaFormat.COLOR_RANGE_FULL else MediaFormat.COLOR_RANGE_LIMITED
            )

            if ((getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
                // HDR 10-bit: set BT.2020 color standard and transfer function.
                // Many decoders fail to auto-detect from VUI/SEI, causing dark/crushed colors.
                videoFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                if (prefs.hdrMode == MoonBridge.HDR_MODE_HLG) {
                    videoFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                    // Request pass-through to prevent internal tone-mapping on some decoders
                    videoFormat.setInteger("color-transfer-request", MediaFormat.COLOR_TRANSFER_HLG)
                } else {
                    videoFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084)
                    videoFormat.setInteger("color-transfer-request", MediaFormat.COLOR_TRANSFER_ST2084)
                }
            } else {
                // SDR mode: set color format keys since they won't change
                videoFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                when (getPreferredColorSpace()) {
                    MoonBridge.COLORSPACE_REC_601 ->
                        videoFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                    MoonBridge.COLORSPACE_REC_709 ->
                        videoFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    MoonBridge.COLORSPACE_REC_2020 ->
                        videoFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                }
            }
        }

        return videoFormat
    }

    private fun getDecoderProfileCandidates(
        mimeType: String,
        selectedDecoderInfo: MediaCodecInfo,
    ): List<Int?> {
        val activeHdr10PlusEligible = if (mimeType == HdrDecoderProfileSelector.MIME_HEVC ||
            mimeType == HdrDecoderProfileSelector.MIME_AV1
        ) {
            val activeFullRange = prefs.hdrMode == MoonBridge.HDR_MODE_HLG ||
                getPreferredColorRange() == MoonBridge.COLOR_RANGE_FULL
            val activeSelector = hdrProfileSelector.forStreamParameters(
                width = initialWidth,
                height = initialHeight,
                frameRate = if (refreshRate > 0) refreshRate else prefs.fps,
                fullRange = activeFullRange,
            )
            requestedHdr10Plus &&
                HdrModePolicy.isHdr10PlusMode(prefs.hdrMode) &&
                (getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0 &&
                activeSelector.supportsHdr10PlusFormat(selectedDecoderInfo, mimeType)
        } else {
            false
        }

        return hdrProfileSelector.buildCandidates(
            mimeType = mimeType,
            decoderInfo = selectedDecoderInfo,
            isTenBit = (getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0,
            isPqHdr = HdrModePolicy.isPqMode(prefs.hdrMode),
            hdr10PlusEligible = activeHdr10PlusEligible,
        )
    }

    private fun configureAndStartDecoder(format: MediaFormat) {
        hdr10PlusOutputObserver.restartCodecConfiguration()
        stableOutputFormatTracker.reset()

        // Set HDR metadata if present
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (currentHdrMetadata != null) {
                val hdrStaticInfo = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
                val hdrMetadata = ByteBuffer.wrap(currentHdrMetadata!!).order(ByteOrder.LITTLE_ENDIAN)

                // Create a HDMI Dynamic Range and Mastering InfoFrame as defined by CTA-861.3
                hdrStaticInfo.put(0.toByte()) // Metadata type
                hdrStaticInfo.putShort(hdrMetadata.short) // RX
                hdrStaticInfo.putShort(hdrMetadata.short) // RY
                hdrStaticInfo.putShort(hdrMetadata.short) // GX
                hdrStaticInfo.putShort(hdrMetadata.short) // GY
                hdrStaticInfo.putShort(hdrMetadata.short) // BX
                hdrStaticInfo.putShort(hdrMetadata.short) // BY
                hdrStaticInfo.putShort(hdrMetadata.short) // White X
                hdrStaticInfo.putShort(hdrMetadata.short) // White Y
                hdrStaticInfo.putShort(hdrMetadata.short) // Max mastering luminance
                hdrStaticInfo.putShort(hdrMetadata.short) // Min mastering luminance
                hdrStaticInfo.putShort(hdrMetadata.short) // Max content luminance
                hdrStaticInfo.putShort(hdrMetadata.short) // Max frame average luminance

                hdrStaticInfo.rewind()
                format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, hdrStaticInfo)
            } else if ((getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
                // HLG streams from Sunshine typically have no SMPTE 2086 static metadata.
                // Without metadata, the display pipeline doesn't know the content's target
                // luminance range, causing conservative tone mapping that makes HDR look dark.
                // Provide default BT.2020 metadata with typical HDR display parameters
                // (matching HarmonyOS behavior for consistent brightness across platforms).
                val hdrStaticInfo = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
                hdrStaticInfo.put(0.toByte())                 // Metadata type (HDMI Static Metadata Type 1)
                // BT.2020 color primaries (in 0.00002 units per CTA-861.3)
                hdrStaticInfo.putShort(35400.toShort())       // RX: 0.708
                hdrStaticInfo.putShort(14600.toShort())       // RY: 0.292
                hdrStaticInfo.putShort(8500.toShort())        // GX: 0.170
                hdrStaticInfo.putShort(39850.toShort())       // GY: 0.797
                hdrStaticInfo.putShort(6550.toShort())        // BX: 0.131
                hdrStaticInfo.putShort(2300.toShort())        // BY: 0.046
                hdrStaticInfo.putShort(15635.toShort())       // White X: 0.3127 (D65)
                hdrStaticInfo.putShort(16450.toShort())       // White Y: 0.3290 (D65)
                hdrStaticInfo.putShort(1000.toShort())        // Max mastering luminance (cd/m²)
                hdrStaticInfo.putShort(10.toShort())          // Min mastering luminance (0.0001 cd/m² units → 0.001 nits)
                hdrStaticInfo.putShort(1000.toShort())        // Max content light level (cd/m²)
                hdrStaticInfo.putShort(400.toShort())         // Max frame average light level (cd/m²)

                hdrStaticInfo.rewind()
                format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, hdrStaticInfo)
                LimeLog.info("Using default BT.2020 HDR static metadata (no server metadata available)")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                format.removeKey(MediaFormat.KEY_HDR_STATIC_INFO)
            }
        }

        LimeLog.info("Configuring with format: $format")

        // Framegen starts on the direct SurfaceView, then switches to ImageReader
        // after the first visible frame. Native framegen presents back to this same
        // SurfaceView and mirrors the HDR dataspace when HDR passthrough is active.
        val pendingFramegenSurface = framegenSurface
        framegenOutputSwitchRequested.set(false)
        framegenOutputSwitchRetryCount.set(0)
        framegenOutputSwitchPending =
            pendingFramegenSurface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val outSurface = if (framegenOutputSwitchPending) {
            LimeLog.info(
                "Framegen delayed capture active: decoder starts on SurfaceView " +
                    "and will switch to ImageReader after first direct frame"
            )
            renderTarget!!.surface
        } else {
            if (pendingFramegenSurface != null) {
                LimeLog.info("Framegen capture surface active (decoder output redirected to ImageReader)")
            }
            pendingFramegenSurface ?: renderTarget!!.surface
        }
        videoDecoder!!.configure(format, outSurface, null, 0)

        // Set DataSpace on the output Surface for HDR content.
        // Equivalent to HarmonyOS OH_NativeWindow_SetColorSpace().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0
        ) {
            // HLG always uses FULL range (its OETF/EOTF requires it)
            val isFullRange = (prefs.hdrMode == MoonBridge.HDR_MODE_HLG) ||
                (getPreferredColorRange() == MoonBridge.COLOR_RANGE_FULL)
            if (prefs.hdrMode == MoonBridge.HDR_MODE_HLG) {
                hdrDataSpace = if (isFullRange)
                    MoonBridge.DATASPACE_BT2020_HLG_FULL
                else
                    MoonBridge.DATASPACE_BT2020_HLG_LIMITED
            } else {
                hdrDataSpace = if (isFullRange)
                    MoonBridge.DATASPACE_BT2020_PQ_FULL
                else
                    MoonBridge.DATASPACE_BT2020_PQ_LIMITED
            }
            applyHdrDataSpace(renderTarget!!.surface, "decoder output")
        }

        configuredFormat = format

        // After reconfiguration, we must resubmit CSD buffers
        submittedCsd = false
        vpsBuffers.clear()
        spsBuffers.clear()
        ppsBuffers.clear()
        clearTimestampTracking()

        // This will contain the actual accepted input format attributes
        inputFormat = videoDecoder!!.inputFormat
        LimeLog.info("Input format: $inputFormat")

        videoDecoder!!.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)

        // Start the decoder
        videoDecoder!!.start()
        hdr10PlusOutputObserver.onCodecStarted()
        if (framegenOutputSwitchPending && framegenOutputSwitchReady.get()) {
            requestFramegenOutputSurfaceSwitch("decoder started after framegen prewarm")
        }
    }

    private fun tryConfigureDecoder(
        selectedDecoderInfo: MediaCodecInfo,
        format: MediaFormat,
        throwOnCodecError: Boolean
    ): Boolean {
        var configured = false
        try {
            videoDecoder = MediaCodec.createByCodecName(selectedDecoderInfo.name)

            // Async callback must be set before configure()
            setupAsyncCallback()

            configureAndStartDecoder(format)
            LimeLog.info("Using codec " + selectedDecoderInfo.name + " for hardware decoding " + format.getString(MediaFormat.KEY_MIME))
            configured = true
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw e
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw e
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw RuntimeException(e)
            }
        } finally {
            if (!configured && videoDecoder != null) {
                videoDecoder!!.release()
                videoDecoder = null
            }
        }
        return configured
    }

    fun initializeDecoder(throwOnCodecError: Boolean): Int {
        val mimeType: String
        val selectedDecoderInfo: MediaCodecInfo

        if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
            mimeType = "video/avc"
            selectedDecoderInfo = avcDecoder ?: run {
                LimeLog.severe("No available AVC decoder!")
                return -1
            }

            if (initialWidth > 4096 || initialHeight > 4096) {
                LimeLog.severe("> 4K streaming only supported on HEVC")
                return -1
            }

            // These fixups only apply to H264 decoders
            needsSpsBitstreamFixup = MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(selectedDecoderInfo.name)
            needsBaselineSpsHack = MediaCodecHelper.decoderNeedsBaselineSpsHack(selectedDecoderInfo.name)
            constrainedHighProfile = MediaCodecHelper.decoderNeedsConstrainedHighProfile(selectedDecoderInfo.name)
            isExynos4 = MediaCodecHelper.isExynos4Device()
            if (needsSpsBitstreamFixup) {
                LimeLog.info("Decoder " + selectedDecoderInfo.name + " needs SPS bitstream restrictions fixup")
            }
            if (needsBaselineSpsHack) {
                LimeLog.info("Decoder " + selectedDecoderInfo.name + " needs baseline SPS hack")
            }
            if (constrainedHighProfile) {
                LimeLog.info("Decoder " + selectedDecoderInfo.name + " needs constrained high profile")
            }
            if (isExynos4) {
                LimeLog.info("Decoder " + selectedDecoderInfo.name + " is on Exynos 4")
            }

            refFrameInvalidationActive = refFrameInvalidationAvc

            spsPatcher = SpsPatcher(
                constrainedHighProfile, needsSpsBitstreamFixup,
                isExynos4, hevcDecoder != null, av1Decoder != null
            )
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
            mimeType = "video/hevc"
            selectedDecoderInfo = hevcDecoder ?: run {
                LimeLog.severe("No available HEVC decoder!")
                return -2
            }

            refFrameInvalidationActive = refFrameInvalidationHevc
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
            mimeType = "video/av01"
            selectedDecoderInfo = av1Decoder ?: run {
                LimeLog.severe("No available AV1 decoder!")
                return -2
            }

            refFrameInvalidationActive = refFrameInvalidationAv1
        } else {
            // Unknown format
            LimeLog.severe("Unknown format")
            return -3
        }

        adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(selectedDecoderInfo, mimeType)
        fusedIdrFrame = MediaCodecHelper.decoderSupportsFusedIdrFrame(selectedDecoderInfo, mimeType)

        val profileCandidates = getDecoderProfileCandidates(mimeType, selectedDecoderInfo)
        var decoderConfigured = false
        hdr10PlusOutputObserver.beginCodecConfiguration(false)

        profileLoop@ for ((profileIndex, profile) in profileCandidates.withIndex()) {
            var tryNumber = 0
            while (true) {
                LimeLog.info(
                    "Decoder configuration try: profile=${hdrProfileSelector.profileName(mimeType, profile)} " +
                        "options=$tryNumber"
                )

                val mediaFormat = createBaseMediaFormat(mimeType)
                if (profile != null) {
                    mediaFormat.setInteger(MediaFormat.KEY_PROFILE, profile)
                }
                val configuringHdr10Plus = hdrProfileSelector.isHdr10PlusProfile(mimeType, profile)
                hdr10PlusOutputObserver.beginCodecConfiguration(configuringHdr10Plus)

                // Try low latency options while omitting Qualcomm output fences for HDR10+.
                val newFormat = MediaCodecHelper.setDecoderLowLatencyOptions(
                    mediaFormat,
                    selectedDecoderInfo,
                    tryNumber,
                    prefs.forceMtkMaxOperatingRate,
                    hdr10PlusModeSelected = HdrModePolicy.isHdr10PlusMode(prefs.hdrMode),
                )

                val isLastProfile = profileIndex == profileCandidates.lastIndex
                if (tryConfigureDecoder(
                        selectedDecoderInfo,
                        mediaFormat,
                        !newFormat && isLastProfile && throwOnCodecError,
                    )
                ) {
                    decoderConfigured = true
                    break@profileLoop
                }

                hdr10PlusOutputObserver.beginCodecConfiguration(false)

                if (!newFormat || tryNumber >= MAX_DECODER_CONFIGURATION_TRIES - 1) {
                    break
                }
                tryNumber++
            }

            if (profileIndex < profileCandidates.lastIndex) {
                LimeLog.warning(
                    "Decoder rejected ${hdrProfileSelector.profileName(mimeType, profile)} profile; trying " +
                        hdrProfileSelector.profileName(mimeType, profileCandidates[profileIndex + 1])
                )
            }
        }

        if (!decoderConfigured) {
            return -5
        }

        if (hdr10PlusOutputObserver.snapshot().configured) {
            LimeLog.info("HDR10+ decoder profile configured for $mimeType")
        } else if (requestedHdr10Plus && mimeType == "video/hevc" && hevcHdr10PlusEligible) {
            LimeLog.warning("HDR10+ profile fallback active; HEVC SEI remains preserved for this connection")
        }

        // Check LinearBlock copy-free compatibility (API 30+)
        // Disabled: Many vendor HEVC/HDR decoders have LinearBlock bugs causing crashes
        linearBlockEnabled = false

        if (USE_FRAME_RENDER_TIME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            videoDecoder!!.setOnFrameRenderedListener({ _, presentationTimeUs, renderTimeNanos ->
                // 首帧实际显示到 surface 的时刻（最准的"画面已上屏"时机）
                if (!firstFrameDelivered) {
                    firstFrameDelivered = true
                    try { firstFrameCallback?.invoke() } catch (_: Throwable) {}
                    requestFramegenOutputSurfaceSwitch("first rendered frame")
                }

                // presentationTimeUs: 我们告诉系统这一帧应该在什么时间点显示
                // renderTimeNanos: 系统报告的这一帧实际显示在屏幕上的时间点
                val presentationTimeMs = presentationTimeUs / 1000
                val renderTimeMs = renderTimeNanos / 1000000L

                // 计算从"应该显示"到"实际显示"的延迟
                val delta = renderTimeMs - presentationTimeMs

                // 过滤掉异常值
                if (delta >= 0 && delta < 1000) {
                    activeWindowVideoStats.renderingTimeMs += delta
                    activeWindowVideoStats.totalTimeMs += delta
                }
            }, null)
        }

        return 0
    }

    override fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int {
        this.initialWidth = width
        this.initialHeight = height
        this.videoFormat = format
        this.refreshRate = redrawRate

        // Async codec mode (API 30+)
        this.asyncModeEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (asyncModeEnabled) {
            availableInputBuffers = ArrayBlockingQueue(ASYNC_INPUT_BUFFER_QUEUE_CAPACITY)
            codecCallbackThread = HandlerThread("Video - Codec", Process.THREAD_PRIORITY_DISPLAY)
            codecCallbackThread!!.start()
            codecCallbackHandler = Handler(codecCallbackThread!!.looper)
            LimeLog.info("MediaCodec async mode enabled")
        }

        return initializeDecoder(false)
    }

    // All threads that interact with the MediaCodec instance must call this function regularly!
    private fun doCodecRecoveryIfRequired(quiescenceFlag: Int): Boolean {
        // NB: We cannot check 'stopping' here because we could end up bailing in a partially
        // quiesced state that will cause the quiesced threads to never wake up.
        if (codecRecoveryType.get() == CR_RECOVERY_TYPE_NONE) {
            // Common case
            return false
        }

        // We need some sort of recovery, so quiesce all threads before starting that
        synchronized(codecRecoveryMonitor) {
            if (!framePacingController.hasActiveTimingThread()) {
                // If we have no frame pacing thread, mark it as quiesced right now.
                codecRecoveryThreadQuiescedFlags = codecRecoveryThreadQuiescedFlags or CR_FLAG_CHOREOGRAPHER
            }

            if (asyncModeEnabled) {
                // In async mode there is no renderer thread; auto-quiesce its flag
                codecRecoveryThreadQuiescedFlags = codecRecoveryThreadQuiescedFlags or CR_FLAG_RENDER_THREAD
            }

            codecRecoveryThreadQuiescedFlags = codecRecoveryThreadQuiescedFlags or quiescenceFlag

            // This is the final thread to quiesce, so let's perform the codec recovery now.
            if (codecRecoveryThreadQuiescedFlags == CR_FLAG_ALL) {
                // Input and output buffers are invalidated by stop() and reset().
                nextInputBuffer = null
                nextInputBufferIndex = -1
                framePacingController.clearBuffers()
                if (asyncModeEnabled && availableInputBuffers != null) {
                    availableInputBuffers!!.clear()
                }

                // If we just need a flush, do so now with all threads quiesced.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_FLUSH) {
                    LimeLog.warning("Flushing decoder")
                    try {
                        videoDecoder!!.flush()
                        if (asyncModeEnabled) {
                            videoDecoder!!.start() // Resume async callbacks after flush
                        }
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()

                        // Something went wrong during the restart, let's use a bigger hammer
                        // and try a reset instead.
                        codecRecoveryType.set(CR_RECOVERY_TYPE_RESTART)
                    }
                }

                // We don't count flushes as codec recovery attempts
                if (codecRecoveryType.get() != CR_RECOVERY_TYPE_NONE) {
                    codecRecoveryAttempts++
                    LimeLog.info("Codec recovery attempt: $codecRecoveryAttempts")
                }

                // For "recoverable" exceptions, we can just stop, reconfigure, and restart.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESTART) {
                    LimeLog.warning("Trying to restart decoder after CodecException")
                    try {
                        videoDecoder!!.stop()
                        setupAsyncCallback() // Re-set callback after stop() for reliable async mode
                        configureAndStartDecoder(configuredFormat!!)
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()

                        // Something went wrong during the restart, let's use a bigger hammer
                        // and try a reset instead.
                        codecRecoveryType.set(CR_RECOVERY_TYPE_RESET)
                    }
                }

                // For "non-recoverable" exceptions on L+, we can call reset() to recover
                // without having to recreate the entire decoder again.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESET) {
                    LimeLog.warning("Trying to reset decoder after CodecException")
                    try {
                        videoDecoder!!.reset()
                        setupAsyncCallback() // reset() clears callback, must re-set before configure
                        configureAndStartDecoder(configuredFormat!!)
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()

                        // Something went wrong during the reset, we'll have to resort to
                        // releasing and recreating the decoder now.
                    }
                }

                // If we _still_ haven't managed to recover, go for the nuclear option and just
                // throw away the old decoder and reinitialize a new one from scratch.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESET) {
                    LimeLog.warning("Trying to recreate decoder after CodecException")
                    videoDecoder!!.release()

                    try {
                        val err = initializeDecoder(true)
                        if (err != 0) {
                            throw IllegalStateException("Decoder reset failed: $err")
                        }
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        // If we failed to recover after all of these attempts, just crash
                        if (!reportedCrash) {
                            reportedCrash = true
                            crashListener.notifyCrash(e)
                        }
                        throw RendererException(createDiagnostics(), e)
                    }
                }

                // Update frame pacing controller with potentially new decoder reference
                framePacingController.updateDecoder(videoDecoder!!)

                // Wake all quiesced threads and allow them to begin work again
                codecRecoveryThreadQuiescedFlags = 0
                (codecRecoveryMonitor as Object).notifyAll()
            } else {
                // If we haven't quiesced all threads yet, wait to be signalled after recovery.
                // The final thread to be quiesced will handle the codec recovery.
                while (codecRecoveryType.get() != CR_RECOVERY_TYPE_NONE) {
                    try {
                        LimeLog.info("Waiting to quiesce decoder threads: $codecRecoveryThreadQuiescedFlags")
                        (codecRecoveryMonitor as Object).wait(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()

                        // InterruptedException clears the thread's interrupt status. Since we can't
                        // handle that here, we will re-interrupt the thread to set the interrupt
                        // status back to true.
                        Thread.currentThread().interrupt()

                        break
                    }
                }
            }
        }

        return true
    }

    // Returns true if the exception is transient
    private fun handleDecoderException(e: IllegalStateException): Boolean {
        // Eat decoder exceptions if we're in the process of stopping
        if (stopping) {
            return false
        }

        if (e is CodecException) {
            if (e.isTransient) {
                // We'll let transient exceptions go
                LimeLog.warning(e.diagnosticInfo)
                return true
            }

            LimeLog.severe(e.diagnosticInfo)

            // We can attempt a recovery or reset at this stage to try to start decoding again
            if (codecRecoveryAttempts < CR_MAX_TRIES) {
                // If the exception is non-recoverable or we already require a reset, perform a reset.
                // If we have no prior unrecoverable failure, we will try a restart instead.
                if (e.isRecoverable) {
                    if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESTART)) {
                        LimeLog.info("Decoder requires restart for recoverable CodecException")
                        e.printStackTrace()
                    } else if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_FLUSH, CR_RECOVERY_TYPE_RESTART)) {
                        LimeLog.info("Decoder flush promoted to restart for recoverable CodecException")
                        e.printStackTrace()
                    } else if (codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET && codecRecoveryType.get() != CR_RECOVERY_TYPE_RESTART) {
                        throw IllegalStateException("Unexpected codec recovery type: " + codecRecoveryType.get())
                    }
                } else if (!e.isRecoverable) {
                    if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESET)) {
                        LimeLog.info("Decoder requires reset for non-recoverable CodecException")
                        e.printStackTrace()
                    } else if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_FLUSH, CR_RECOVERY_TYPE_RESET)) {
                        LimeLog.info("Decoder flush promoted to reset for non-recoverable CodecException")
                        e.printStackTrace()
                    } else if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_RESTART, CR_RECOVERY_TYPE_RESET)) {
                        LimeLog.info("Decoder restart promoted to reset for non-recoverable CodecException")
                        e.printStackTrace()
                    } else if (codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET) {
                        throw IllegalStateException("Unexpected codec recovery type: " + codecRecoveryType.get())
                    }
                }

                // The recovery will take place when all threads reach doCodecRecoveryIfRequired().
                return false
            }
        } else {
            // IllegalStateException was primarily used prior to the introduction of CodecException.
            // Recovery from this requires a full decoder reset.
            //
            // NB: CodecException is an IllegalStateException, so we must check for it first.
            if (codecRecoveryAttempts < CR_MAX_TRIES) {
                if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESET)) {
                    LimeLog.info("Decoder requires reset for IllegalStateException")
                    e.printStackTrace()
                } else if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_FLUSH, CR_RECOVERY_TYPE_RESET)) {
                    LimeLog.info("Decoder flush promoted to reset for IllegalStateException")
                    e.printStackTrace()
                } else if (codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_RESTART, CR_RECOVERY_TYPE_RESET)) {
                    LimeLog.info("Decoder restart promoted to reset for IllegalStateException")
                    e.printStackTrace()
                } else if (codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET) {
                    throw IllegalStateException("Unexpected codec recovery type: " + codecRecoveryType.get())
                }

                return false
            }
        }

        // Only throw if we're not in the middle of codec recovery
        if (codecRecoveryType.get() == CR_RECOVERY_TYPE_NONE) {
            //
            // There seems to be a race condition with decoder/surface teardown causing some
            // decoders to to throw IllegalStateExceptions even before 'stopping' is set.
            // To workaround this while allowing real exceptions to propagate, we will eat the
            // first exception. If we are still receiving exceptions 3 seconds later, we will
            // throw the original exception again.
            //
            if (initialException != null) {
                // This isn't the first time we've had an exception processing video
                if (SystemClock.uptimeMillis() - initialExceptionTimestamp >= EXCEPTION_REPORT_DELAY_MS) {
                    // It's been over 3 seconds and we're still getting exceptions. Throw the original now.
                    if (!reportedCrash) {
                        reportedCrash = true
                        crashListener.notifyCrash(initialException!!)
                    }
                    throw initialException!!
                }
            } else {
                // This is the first exception we've hit
                initialException = RendererException(createDiagnostics(), e)
                initialExceptionTimestamp = SystemClock.uptimeMillis()
            }
        }

        // Not transient
        return false
    }

    /**
     * Delivers a decoded frame to the appropriate output path based on frame pacing mode.
     * Called only from the async output callback (onOutputBufferAvailable); the sync
     * renderer thread in startRendererThread() releases output buffers directly and does
     * not go through this method.
     */
    private fun deliverDecodedFrame(bufferIndex: Int, hostPtsUs: Long = -1L) {
        if (prefs.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED ||
            prefs.framePacing == PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY ||
            prefs.framePacing == PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC
        ) {
            // Buffered modes - queue for frame pacing controller
            framePacingController.offerOutputBuffer(bufferIndex, hostPtsUs)
        } else {
            // Direct render modes (MIN_LATENCY, MAX_SMOOTHNESS, CAP_FPS)
            try {
                if (prefs.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                    prefs.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS
                ) {
                    if (useHostCadencePacing && hostPtsUs >= 0) {
                        // host-cadence 呈现：按主机出帧节奏复现，去抖时钟给出精确呈现时刻。
                        // 偏平滑档(MAX_SMOOTHNESS/CAP_FPS)权衡少量缓冲延迟换取无判抖，语义相符。
                        val fps = if (refreshRate > 0) refreshRate else 60
                        val frameIntervalNs = 1_000_000_000L / fps
                        videoDecoder!!.releaseOutputBuffer(bufferIndex, hostCadenceClock.presentTimeNs(hostPtsUs, frameIntervalNs))
                    } else {
                        videoDecoder!!.releaseOutputBuffer(bufferIndex, 0)
                    }
                } else {
                    videoDecoder!!.releaseOutputBuffer(bufferIndex, System.nanoTime())
                }
                activeWindowVideoStats.totalFramesRendered++
                frameIntervalTracker.recordFrame()
                // 兜底首帧通知（API < 23 没有 OnFrameRenderedListener，buffered 模式也走这里之外的路径）
                // 这里 release 已发出，SurfaceFlinger 会在下一次 vsync 合成；UI 层 dismiss 时再延一帧能避开缝隙。
                if (!firstFrameDelivered) {
                    firstFrameDelivered = true
                    try { firstFrameCallback?.invoke() } catch (_: Throwable) {}
                    requestFramegenOutputSurfaceSwitch("first released frame")
                }
            } catch (e: IllegalStateException) {
                handleDecoderException(e)
            }
        }
    }

    /**
     * Sets up the async MediaCodec callback for event-driven input/output buffer handling.
     * Must be called before configure() on API 30+.
     */
    private fun setupAsyncCallback() {
        if (!asyncModeEnabled || videoDecoder == null) return

        availableInputBuffers!!.clear()

        videoDecoder!!.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                if (!stopping) {
                    if (!availableInputBuffers!!.offer(index)) {
                        LimeLog.warning("Async input buffer queue overflow; flushing decoder")
                        codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_FLUSH)
                    }
                }
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: BufferInfo) {
                if (stopping) return

                numFramesOut++
                hdr10PlusOutputObserver.observeOutput(codec, index, info.presentationTimeUs)

                // Deliver to frame pacing. Remove the host PTS before decoder-time tracking,
                // because calculateDecoderTime() removes the paired enqueue entry.
                val hostPtsUs = if (needsHostPtsMapping) {
                    frameTimestampTracker.takeHostPresentationTime(info.presentationTimeUs, -1L)
                } else {
                    -1L
                }

                // Calculate decoder time
                val delta = calculateDecoderTime(info.presentationTimeUs)
                if (delta >= 0 && delta < 1000) {
                    activeWindowVideoStats.decoderTimeMs += delta
                    if (!USE_FRAME_RENDER_TIME) {
                        activeWindowVideoStats.totalTimeMs += delta
                    }
                }

                deliverDecodedFrame(index, hostPtsUs)

                doCodecRecoveryIfRequired(CR_FLAG_RENDER_THREAD)
            }

            override fun onError(codec: MediaCodec, e: CodecException) {
                handleDecoderException(e)
                doCodecRecoveryIfRequired(CR_FLAG_RENDER_THREAD)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                handleOutputFormatChanged(format, "async")
            }
        }, codecCallbackHandler)
    }

    private fun startRendererThread() {
        if (asyncModeEnabled) {
            // In async mode, output is handled by onOutputBufferAvailable callback.
            // No renderer thread needed.
            return
        }

        rendererThread = object : Thread() {
            override fun run() {
                // Create PerformanceHint session for this thread
                perfBoostManager.createHintSession(refreshRate)

                val info = BufferInfo()
                while (!stopping) {
                    try {
                        // Try to output a frame
                        var outIndex = videoDecoder!!.dequeueOutputBuffer(info, 50000)
                        if (outIndex >= 0) {
                            hdr10PlusOutputObserver.observeOutput(videoDecoder!!, outIndex, info.presentationTimeUs)
                            var presentationTimeUs = info.presentationTimeUs
                            var lastIndex = outIndex

                            numFramesOut++

                            // Render the latest frame now if frame pacing isn't in balanced mode or Surface Flinger mode
                            if (prefs.framePacing != PreferenceConfiguration.FRAME_PACING_BALANCED &&
                                prefs.framePacing != PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY &&
                                prefs.framePacing != PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC
                            ) {
                                // Get the last output buffer in the queue
                                while (videoDecoder!!.dequeueOutputBuffer(info, 0).also { outIndex = it } >= 0) {
                                    hdr10PlusOutputObserver.observeOutput(videoDecoder!!, outIndex, info.presentationTimeUs)
                                    videoDecoder!!.releaseOutputBuffer(lastIndex, false)

                                    numFramesOut++

                                    lastIndex = outIndex
                                    presentationTimeUs = info.presentationTimeUs
                                }

                                if (prefs.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                                    prefs.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS
                                ) {
                                    // In max smoothness or cap FPS mode, we want to never drop frames
                                    // Use a PTS that will cause this frame to never be dropped
                                    videoDecoder!!.releaseOutputBuffer(lastIndex, 0)
                                } else {
                                    // Use a PTS that will cause this frame to be dropped if another comes in within
                                    // the same V-sync period
                                    videoDecoder!!.releaseOutputBuffer(lastIndex, System.nanoTime())
                                }

                                activeWindowVideoStats.totalFramesRendered++
                                frameIntervalTracker.recordFrame()
                            } else {
                                // Buffered modes: deliver to frame pacing controller
                                framePacingController.offerOutputBuffer(lastIndex)
                            }

                            // Add delta time to the totals (excluding probable outliers)
                            val delta = calculateDecoderTime(presentationTimeUs)
                            if (delta >= 0 && delta < 1000) {
                                activeWindowVideoStats.decoderTimeMs += delta
                                if (!USE_FRAME_RENDER_TIME) {
                                    activeWindowVideoStats.totalTimeMs += delta
                                }
                                // Report to PerformanceHintManager for DVFS optimization
                                perfBoostManager.reportActualWorkDuration(delta * 1000000L)
                            }
                        } else {
                            when (outIndex) {
                                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    handleOutputFormatChanged(videoDecoder!!.outputFormat, "sync")
                                }
                                MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                                else -> {}
                            }
                        }
                    } catch (e: IllegalStateException) {
                        handleDecoderException(e)
                    } finally {
                        doCodecRecoveryIfRequired(CR_FLAG_RENDER_THREAD)
                    }
                }
            }
        }
        rendererThread!!.name = "Video - Renderer (MediaCodec)"
        rendererThread!!.priority = Thread.NORM_PRIORITY + 2
        rendererThread!!.start()
    }

    private fun fetchNextInputBuffer(): Boolean {
        val startTime: Long
        val codecRecovered: Boolean

        if (nextInputBuffer != null) {
            // We already have an input buffer
            return true
        }

        startTime = SystemClock.uptimeMillis()

        try {
            // If we don't have an input buffer index yet, fetch one now
            if (asyncModeEnabled) {
                // Async mode: input buffers arrive via onInputBufferAvailable callback
                while (nextInputBufferIndex < 0 && !stopping) {
                    try {
                        val index = availableInputBuffers!!.poll(1, TimeUnit.SECONDS)
                        if (index != null) {
                            nextInputBufferIndex = index
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            } else {
                // Sync mode: poll the decoder directly
                while (nextInputBufferIndex < 0 && !stopping) {
                    nextInputBufferIndex = videoDecoder!!.dequeueInputBuffer(5000)
                }
            }

            // Get the backing ByteBuffer for the input buffer index
            if (nextInputBufferIndex >= 0) {
                // Using the new getInputBuffer() API on Lollipop allows
                // the framework to do some performance optimizations for us
                nextInputBuffer = videoDecoder!!.getInputBuffer(nextInputBufferIndex)
                if (nextInputBuffer == null) {
                    // According to the Android docs, getInputBuffer() can return null "if the
                    // index is not a dequeued input buffer". I don't think this ever should
                    // happen but if it does, let's try to get a new input buffer next time.
                    nextInputBufferIndex = -1
                }
            }
        } catch (e: IllegalStateException) {
            handleDecoderException(e)
            return false
        } finally {
            codecRecovered = doCodecRecoveryIfRequired(CR_FLAG_INPUT_THREAD)
        }

        // If codec recovery is required, always return false to ensure the caller will request
        // an IDR frame to complete the codec recovery.
        if (codecRecovered) {
            return false
        }

        // prepareForStop() deliberately breaks the input-buffer wait above. If the codec was
        // backpressured for several seconds (for example after heavy packet loss), deltaMs may
        // exceed the decoder-hang threshold by the time the user exits the stream. Treating that
        // expected shutdown wake-up as a hang records a false decoder crash on the next launch.
        if (stopping) {
            return false
        }

        val deltaMs = (SystemClock.uptimeMillis() - startTime).toInt()

        if (deltaMs >= 20) {
            LimeLog.warning("Dequeue input buffer ran long: $deltaMs ms")
        }

        if (nextInputBuffer == null) {
            // We've been hung for 5 seconds and no other exception was reported,
            // so generate a decoder hung exception
            if (deltaMs >= 5000 && initialException == null) {
                val decoderHungException = DecoderHungException(deltaMs)
                if (!reportedCrash) {
                    reportedCrash = true
                    crashListener.notifyCrash(decoderHungException)
                }
                throw RendererException(createDiagnostics(), decoderHungException)
            }

            return false
        }

        return true
    }

    override fun start() {
        startRendererThread()
        framePacingController.start(videoDecoder!!, refreshRate)

        // Start thermal monitoring to warn about throttling
        perfBoostManager.startThermalMonitoring { status ->
            LimeLog.warning("Severe thermal throttling (status $status), consider reducing stream quality")
        }
    }

    // !!! May be called even if setup()/start() fails !!!
    fun prepareForStop() {
        // Let the decoding code know to ignore codec exceptions now
        stopping = true

        // Clear timestamp tracking map
        clearTimestampTracking()

        // Halt the rendering thread
        rendererThread?.interrupt()

        // Stop frame pacing threads (Choreographer, PreciseSync)
        framePacingController.prepareForStop()

        // Stop any active codec recovery operations
        synchronized(codecRecoveryMonitor) {
            codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
            (codecRecoveryMonitor as Object).notifyAll()
        }
    }

    override fun stop() {
        // May be called already, but we'll call it now to be safe
        prepareForStop()

        // Wait for frame pacing threads (Choreographer, PreciseSync)
        framePacingController.joinThreads()

        // Wait for the renderer thread to shut down (sync mode only)
        if (rendererThread != null) {
            try {
                rendererThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Thread.currentThread().interrupt()
            }
        }

        // Close performance management
        perfBoostManager.close()
    }

    override fun cleanup() {
        if (videoDecoder != null) {
            try {
                videoDecoder!!.release()
            } catch (e: Exception) {
                // Ignore exceptions during shutdown
                LimeLog.warning("Exception during decoder release: " + e.message)
            }
        }
        clearTimestampTracking()

        // Stop codec callback thread (async mode)
        if (codecCallbackThread != null) {
            codecCallbackThread!!.quitSafely()
            codecCallbackThread = null
        }
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        // The enabled flag is the authoritative stream HDR state. Static metadata may be absent
        // for HLG, NVIDIA GameStream, or a valid Sunshine HDR transition.
        hdr10PlusOutputObserver.onHostHdrMode(enabled)

        // HDR metadata is only supported in Android 7.0 and later, so don't bother
        // restarting the codec on anything earlier than that.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (currentHdrMetadata != null && (!enabled || hdrMetadata == null)) {
                currentHdrMetadata = null
            } else if (enabled && hdrMetadata != null && !currentHdrMetadata.contentEquals(hdrMetadata)) {
                currentHdrMetadata = hdrMetadata
            } else {
                // Nothing to do
                return
            }

            // If we reach this point, we need to restart the MediaCodec instance to
            // pick up the HDR metadata change. This will happen on the next input
            // or output buffer.

            // HACK: Reset codec recovery attempt counter, since this is an expected "recovery"
            codecRecoveryAttempts = 0

            // Promote None/Flush to Restart and leave Reset alone
            if (!codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESTART)) {
                codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_FLUSH, CR_RECOVERY_TYPE_RESTART)
            }
        }
    }

    override fun onResolutionChanged(width: Int, height: Int) {
        // Skip if resolution hasn't actually changed
        if (width == initialWidth && height == initialHeight) {
            return
        }

        LimeLog.info("Decoder notified of resolution change: ${initialWidth}x${initialHeight} -> ${width}x${height}")

        // Check if new resolution exceeds current decoder configuration
        val needsRestart = width > initialWidth || height > initialHeight

        // Update tracked resolution
        initialWidth = width
        initialHeight = height

        if (needsRestart) {
            LimeLog.info("New resolution exceeds decoder config, triggering codec restart")

            // Reset recovery counter since this is an expected restart
            codecRecoveryAttempts = 0

            // Promote to restart: None->Restart or Flush->Restart
            if (!codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESTART)) {
                codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_FLUSH, CR_RECOVERY_TYPE_RESTART)
            }
        }
    }

    private fun queueNextInputBuffer(
        timestampUs: Long,
        codecFlags: Int,
        hostPresentationTimeUs: Long = -1L,
    ): Boolean {
        val codecRecovered: Boolean

        try {
            // Record the enqueue time for this timestamp
            recordQueuedTimestamp(timestampUs, codecFlags, hostPresentationTimeUs)

            if (linearBlockEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Modern QueueRequest API with LinearBlock for potential copy-free path
                try {
                    val dataSize = nextInputBuffer!!.position()
                    val block = MediaCodec.LinearBlock.obtain(
                        Math.max(dataSize, 4096), codecNameArray!!
                    )!!
                    try {
                        val mapped = block.map()
                        nextInputBuffer!!.flip()
                        mapped.put(nextInputBuffer!!)

                        videoDecoder!!.getQueueRequest(nextInputBufferIndex)
                            .setLinearBlock(block, 0, dataSize)
                            .setPresentationTimeUs(timestampUs)
                            .setFlags(codecFlags)
                            .queue()
                    } finally {
                        block.recycle()
                    }
                } catch (e: Exception) {
                    // Fall back to standard path on any failure
                    LimeLog.warning("LinearBlock failed, falling back: " + e.message)
                    linearBlockEnabled = false
                    nextInputBuffer!!.flip()
                    videoDecoder!!.queueInputBuffer(
                        nextInputBufferIndex,
                        0, nextInputBuffer!!.limit(), timestampUs, codecFlags
                    )
                }
            } else {
                videoDecoder!!.queueInputBuffer(
                    nextInputBufferIndex,
                    0, nextInputBuffer!!.position(),
                    timestampUs, codecFlags
                )
            }

            // We need a new buffer now
            nextInputBufferIndex = -1
            nextInputBuffer = null
        } catch (e: IllegalStateException) {
            if (handleDecoderException(e)) {
                // We encountered a transient error. In this case, just hold onto the buffer
                // (to avoid leaking it), clear it, and keep it for the next frame. We'll return
                // false to trigger an IDR frame to recover.
                nextInputBuffer!!.clear()
            } else {
                // We encountered a non-transient error. In this case, we will simply leak the
                // buffer because we cannot be sure we will ever succeed in queuing it.
                nextInputBufferIndex = -1
                nextInputBuffer = null
            }
            return false
        } finally {
            codecRecovered = doCodecRecoveryIfRequired(CR_FLAG_INPUT_THREAD)
        }

        // If codec recovery is required, always return false to ensure the caller will request
        // an IDR frame to complete the codec recovery.
        if (codecRecovered) {
            return false
        }

        // Fetch a new input buffer now while we have some time between frames
        // to have it ready immediately when the next frame arrives.
        //
        // We must propagate the return value here in order to properly handle
        // codec recovery happening in fetchNextInputBuffer(). If we don't, we'll
        // never get an IDR frame to complete the recovery process.
        return fetchNextInputBuffer()
    }

    private fun recordQueuedTimestamp(
        timestampUs: Long,
        codecFlags: Int,
        hostPresentationTimeUs: Long,
    ) {
        if ((codecFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return

        val trackHostPresentationTime = needsHostPtsMapping && hostPresentationTimeUs >= 0
        frameTimestampTracker.recordFrame(
            timestampUs,
            SystemClock.uptimeMillis(),
            hostPresentationTimeUs,
            trackHostPresentationTime,
        )
    }

    private fun clearTimestampTracking() {
        frameTimestampTracker.clear()
    }

    @Suppress("deprecation")
    override fun submitDecodeUnit(
        decodeUnitData: ByteArray,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
        hostPresentationTimeUs: Long
    ): Int {
        if (stopping || isProcessingPaused) {
            // Don't bother if we're stopping or paused
            return MoonBridge.DR_OK
        }

        if (needsIdrOnResume) {
            if (frameType != MoonBridge.FRAME_TYPE_IDR) {
                // Request an IDR frame to recover after resume
                return MoonBridge.DR_NEED_IDR
            }
            // We got our IDR frame
            needsIdrOnResume = false
        }

        if (lastFrameNumber == 0) {
            activeWindowVideoStats.measurementStartTimestamp = SystemClock.uptimeMillis()
        } else if (frameNumber > lastFrameNumber + 1) {
            // We can receive the same "frame" multiple times if it's an IDR frame.
            // In that case, each frame start NALU is submitted independently.
            val framesLost = frameNumber - lastFrameNumber - 1
            activeWindowVideoStats.framesLost += framesLost
            activeWindowVideoStats.totalFrames += framesLost
            activeWindowVideoStats.frameLossEvents++
            perfListener.onVideoFrameLoss(framesLost, frameNumber)
        }

        // Reset CSD data for each IDR frame
        if (lastFrameNumber != frameNumber && frameType == MoonBridge.FRAME_TYPE_IDR) {
            vpsBuffers.clear()
            spsBuffers.clear()
            ppsBuffers.clear()
        }

        lastFrameNumber = frameNumber

        // Flip stats windows roughly every second
        if (SystemClock.uptimeMillis() >= activeWindowVideoStats.measurementStartTimestamp + 1000) {
            val lastTwo = VideoStats()
            lastTwo.add(lastWindowVideoStats)
            lastTwo.add(activeWindowVideoStats)
            val fps = lastTwo.getFps()
            val decoder: String

            if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                decoder = avcDecoder!!.name
            } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
                decoder = hevcDecoder!!.name
            } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
                decoder = av1Decoder!!.name
            } else {
                decoder = "(unknown)"
            }
            val decodeTimeMs = lastTwo.decoderTimeMs.toFloat() / lastTwo.totalFramesReceived
            val rttInfo = MoonBridge.getEstimatedRttInfo()
            val lostFrameRate = lastTwo.framesLost.toFloat() / lastTwo.totalFrames * 100
            val minHostProcessingLatency = lastTwo.minHostProcessingLatency.code.toFloat() / 10
            val maxHostProcessingLatency = lastTwo.minHostProcessingLatency.code.toFloat() / 10
            val aveHostProcessingLatency = lastTwo.totalHostProcessingLatency.toFloat() / 10 / lastTwo.framesWithHostProcessingLatency

            // 计算平均"解码+渲染"总时间
            var aveTotalProcessingTimeMs = 0f
            if (lastTwo.totalFramesRendered > 0) {
                aveTotalProcessingTimeMs = lastTwo.totalTimeMs.toFloat() / lastTwo.totalFramesRendered
            }

            // 计算平均"纯渲染延迟"
            // 注意：这里用总处理时间减去解码时间。如果结果为负，说明数据有抖动，取0即可。
            val avePureRenderingLatencyMs = Math.max(0f, aveTotalProcessingTimeMs - decodeTimeMs)

            val performanceInfo = PerformanceInfo()
            performanceInfo.context = context
            performanceInfo.initialWidth = initialWidth
            performanceInfo.initialHeight = initialHeight
            performanceInfo.decoder = decoder
            performanceInfo.totalFps = fps.totalFps
            performanceInfo.receivedFps = fps.receivedFps
            performanceInfo.renderedFps = fps.renderedFps
            performanceInfo.lostFrameRate = lostFrameRate
            performanceInfo.rttInfo = rttInfo
            performanceInfo.framesWithHostProcessingLatency = frameHostProcessingLatency.code
            val hdr10PlusRuntime = hdr10PlusOutputObserver.snapshot()
            performanceInfo.hdrFormat = StreamHdrFormatPolicy.resolve(
                hdrEnabled = hdr10PlusRuntime.streamState == HdrStreamState.ENABLED,
                hdrStateKnown = hdr10PlusRuntime.streamState != HdrStreamState.UNKNOWN,
                isTenBitStream = (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0,
                isPqHdr = HdrModePolicy.isPqMode(prefs.hdrMode),
                isHlg = prefs.hdrMode == MoonBridge.HDR_MODE_HLG,
                hdr10PlusConfigured = hdr10PlusRuntime.configured,
                hdr10PlusMetadataObserved = hdr10PlusRuntime.metadataObserved,
            )
            performanceInfo.minHostProcessingLatency = minHostProcessingLatency
            performanceInfo.maxHostProcessingLatency = maxHostProcessingLatency
            performanceInfo.aveHostProcessingLatency = aveHostProcessingLatency
            performanceInfo.decodeTimeMs = decodeTimeMs
            performanceInfo.renderingLatencyMs = avePureRenderingLatencyMs
            performanceInfo.totalTimeMs = aveTotalProcessingTimeMs
            performanceInfo.onePercentLowFps = frameIntervalTracker.getOnePercentLowFps()

            perfListener.onPerfUpdateV(performanceInfo)
            perfListener.onPerfUpdateWG(performanceInfo)

            globalVideoStats.add(activeWindowVideoStats)
            lastWindowVideoStats.copy(activeWindowVideoStats)
            activeWindowVideoStats.clear()
            activeWindowVideoStats.measurementStartTimestamp = SystemClock.uptimeMillis()
        }

        var csdSubmittedForThisFrame = false

        // IDR frames require special handling for CSD buffer submission
        if (frameType == MoonBridge.FRAME_TYPE_IDR) {
            // H264 SPS
            if (decodeUnitType == MoonBridge.BUFFER_TYPE_SPS && (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                numSpsIn++

                val result = spsPatcher!!.patchSps(
                    decodeUnitData, decodeUnitLength,
                    refFrameInvalidationActive, initialWidth, initialHeight,
                    refreshRate, needsBaselineSpsHack
                )

                if (result.savedSps != null) {
                    savedSps = result.savedSps
                }

                spsBuffers.add(result.patchedNalu)
                return MoonBridge.DR_OK
            } else if (decodeUnitType == MoonBridge.BUFFER_TYPE_VPS) {
                numVpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(decodeUnitData, 0, naluBuffer, 0, decodeUnitLength)
                vpsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            }
            // Only the HEVC SPS hits this path (H.264 is handled above)
            else if (decodeUnitType == MoonBridge.BUFFER_TYPE_SPS) {
                numSpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(decodeUnitData, 0, naluBuffer, 0, decodeUnitLength)
                spsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if (decodeUnitType == MoonBridge.BUFFER_TYPE_PPS) {
                numPpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(decodeUnitData, 0, naluBuffer, 0, decodeUnitLength)
                ppsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if ((videoFormat and (MoonBridge.VIDEO_FORMAT_MASK_H264 or MoonBridge.VIDEO_FORMAT_MASK_H265)) != 0) {
                // If this is the first CSD blob or we aren't supporting fused IDR frames, we will
                // submit the CSD blob in a separate input buffer for each IDR frame.
                if (!submittedCsd || !fusedIdrFrame) {
                    if (!fetchNextInputBuffer()) {
                        return MoonBridge.DR_NEED_IDR
                    }

                    // Submit all CSD when we receive the first non-CSD blob in an IDR frame
                    for (vpsBuffer in vpsBuffers) {
                        nextInputBuffer!!.put(vpsBuffer)
                    }
                    for (spsBuffer in spsBuffers) {
                        nextInputBuffer!!.put(spsBuffer)
                    }
                    for (ppsBuffer in ppsBuffers) {
                        nextInputBuffer!!.put(ppsBuffer)
                    }

                    if (!queueNextInputBuffer(0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)) {
                        return MoonBridge.DR_NEED_IDR
                    }

                    // Remember that we already submitted CSD for this frame, so we don't do it
                    // again in the fused IDR case below.
                    csdSubmittedForThisFrame = true

                    // Remember that we submitted CSD globally for this MediaCodec instance
                    submittedCsd = true

                    if (needsBaselineSpsHack) {
                        needsBaselineSpsHack = false

                        if (!replaySps()) {
                            return MoonBridge.DR_NEED_IDR
                        }

                        LimeLog.info("SPS replay complete")
                    }
                }
            }
        }

        if (frameHostProcessingLatency.code != 0) {
            if (activeWindowVideoStats.minHostProcessingLatency.code != 0) {
                activeWindowVideoStats.minHostProcessingLatency = minOf(activeWindowVideoStats.minHostProcessingLatency, frameHostProcessingLatency)
            } else {
                activeWindowVideoStats.minHostProcessingLatency = frameHostProcessingLatency
            }
            activeWindowVideoStats.framesWithHostProcessingLatency += 1
        }
        activeWindowVideoStats.maxHostProcessingLatency = maxOf(activeWindowVideoStats.maxHostProcessingLatency, frameHostProcessingLatency)
        activeWindowVideoStats.totalHostProcessingLatency += frameHostProcessingLatency.code

        activeWindowVideoStats.totalFramesReceived++
        activeWindowVideoStats.totalFrames++

        if (!FRAME_RENDER_TIME_ONLY) {
            // Count time from first packet received to enqueue time as receive time
            // We will count DU queue time as part of decoding, because it is directly
            // caused by a slow decoder.
            // receiveTimeUs and enqueueTimeUs are in microseconds, convert to milliseconds
            activeWindowVideoStats.totalTimeMs += (enqueueTimeUs - receiveTimeUs) / 1000
        }

        if (!fetchNextInputBuffer()) {
            return MoonBridge.DR_NEED_IDR
        }

        var codecFlags = 0

        if (frameType == MoonBridge.FRAME_TYPE_IDR) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_SYNC_FRAME

            // If we are using fused IDR frames, submit the CSD with each IDR frame
            if (fusedIdrFrame && !csdSubmittedForThisFrame) {
                for (vpsBuffer in vpsBuffers) {
                    nextInputBuffer!!.put(vpsBuffer)
                }
                for (spsBuffer in spsBuffers) {
                    nextInputBuffer!!.put(spsBuffer)
                }
                for (ppsBuffer in ppsBuffers) {
                    nextInputBuffer!!.put(ppsBuffer)
                }
            }
        }

        var timestampUs = enqueueTimeUs
        if (timestampUs <= lastTimestampUs) {
            // We can't submit multiple buffers with the same timestamp
            // so bump it up by one before queuing
            timestampUs = lastTimestampUs + 1
        }
        lastTimestampUs = timestampUs

        numFramesIn++

        if (decodeUnitLength > nextInputBuffer!!.limit() - nextInputBuffer!!.position()) {
            val exception = IllegalArgumentException(
                "Decode unit length $decodeUnitLength too large for input buffer ${nextInputBuffer!!.limit()}"
            )
            if (!reportedCrash) {
                reportedCrash = true
                crashListener.notifyCrash(exception)
            }
            throw RendererException(createDiagnostics(), exception)
        }

        // Copy data from our buffer list into the input buffer
        nextInputBuffer!!.put(decodeUnitData, 0, decodeUnitLength)

        if (!queueNextInputBuffer(timestampUs, codecFlags, hostPresentationTimeUs)) {
            return MoonBridge.DR_NEED_IDR
        }

        return MoonBridge.DR_OK
    }

    private fun replaySps(): Boolean {
        if (!fetchNextInputBuffer()) {
            return false
        }

        val replayNalu = spsPatcher!!.buildReplaySps(savedSps!!)
        nextInputBuffer!!.put(replayNalu)

        // No need for the SPS anymore
        savedSps = null

        // Queue the new SPS
        return queueNextInputBuffer(0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
    }

    override fun getCapabilities(): Int {
        var capabilities = 0

        // Request the optimal number of slices per frame for this decoder
        capabilities = capabilities or MoonBridge.CAPABILITY_SLICES_PER_FRAME(optimalSlicesPerFrame)

        // Enable reference frame invalidation on supported hardware
        if (refFrameInvalidationAvc) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC
        }
        if (refFrameInvalidationHevc) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC
        }
        if (refFrameInvalidationAv1) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1
        }

        // common-c strips prepended HEVC SEI by default for legacy renderer compatibility.
        // Opt in only after the display/decoder HDR10+ preflight succeeds.
        if (hevcHdr10PlusEligible) {
            capabilities = capabilities or MoonBridge.CAPABILITY_PRESERVE_HEVC_SEI
        }

        // Enable direct submit on supported hardware
        if (directSubmit) {
            capabilities = capabilities or MoonBridge.CAPABILITY_DIRECT_SUBMIT
        }

        return capabilities
    }

    internal fun createDiagnostics(): RendererDiagnostics {
        val hdr10PlusRuntime = hdr10PlusOutputObserver.snapshot()
        val hdr10PlusSnapshot = hdr10PlusRuntime.metadata
        return RendererDiagnostics(
            numVpsIn = numVpsIn,
            numSpsIn = numSpsIn,
            numPpsIn = numPpsIn,
            numFramesIn = numFramesIn,
            numFramesOut = numFramesOut,
            videoFormat = videoFormat,
            initialWidth = initialWidth,
            initialHeight = initialHeight,
            refreshRate = refreshRate,
            bitrate = prefs.bitrate,
            framePacing = prefs.framePacing,
            consecutiveCrashCount = consecutiveCrashCount,
            adaptivePlayback = adaptivePlayback,
            refFrameInvalidationActive = refFrameInvalidationActive,
            fusedIdrFrame = fusedIdrFrame,
            hdr10PlusEligible = when {
                (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0 -> hevcHdr10PlusEligible
                (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0 -> av1Hdr10PlusEligible
                else -> false
            },
            hdr10PlusConfigured = hdr10PlusRuntime.configured,
            hdr10PlusOutputFramesQueried = hdr10PlusSnapshot.outputFramesQueried,
            hdr10PlusMetadataFrames = hdr10PlusSnapshot.metadataFrames,
            hdr10PlusMetadataChanges = hdr10PlusSnapshot.metadataChanges,
            hdr10PlusLastMetadataSize = hdr10PlusSnapshot.lastMetadataSize,
            hdr10PlusLastPresentationTimeUs = hdr10PlusSnapshot.lastPresentationTimeUs,
            hdr10PlusMetadataQueryFailures = hdr10PlusSnapshot.queryFailures,
            glRenderer = glRenderer,
            avcDecoder = avcDecoder,
            hevcDecoder = hevcDecoder,
            av1Decoder = av1Decoder,
            configuredFormat = configuredFormat,
            inputFormat = inputFormat,
            outputFormat = outputFormat,
            totalFramesReceived = globalVideoStats.totalFramesReceived.toLong(),
            totalFramesRendered = globalVideoStats.totalFramesRendered.toLong(),
            framesLost = globalVideoStats.framesLost.toLong(),
            frameLossEvents = globalVideoStats.frameLossEvents.toLong(),
            averageEndToEndLatency = getAverageEndToEndLatency(),
            averageDecoderLatency = getAverageDecoderLatency(),
        )
    }

    fun getAverageEndToEndLatency(): Int {
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0
        }
        return (globalVideoStats.totalTimeMs / globalVideoStats.totalFramesReceived).toInt()
    }

    fun getAverageDecoderLatency(): Int {
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0
        }
        return (globalVideoStats.decoderTimeMs / globalVideoStats.totalFramesReceived).toInt()
    }

    @SuppressLint("DefaultLocale")
    fun getSurfaceFlingerStats(): String? {
        if (prefs.framePacing != PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC) {
            return null
        }

        if (globalVideoStats.totalFramesReceived == 0) {
            return null
        }

        // 计算跳帧率
        // surfaceFlingerSkippedFrames: Surface Flinger线程因缓冲区为空而跳过的帧
        // 总跳帧 = SF线程跳帧 + 网络丢帧
        val totalFramesExpected = framePacingController.getSurfaceFlingerFrameCount() +
            framePacingController.getSurfaceFlingerSkippedFrames()
        var skipRate = 0f

        if (totalFramesExpected > 0) {
            skipRate = framePacingController.getSurfaceFlingerSkippedFrames().toFloat() /
                totalFramesExpected * 100f
        }

        return String.format(
            "[精确同步: %d渲染/%d接收, 跳帧率: %.1f%%]",
            globalVideoStats.totalFramesRendered,
            globalVideoStats.totalFramesReceived,
            skipRate
        )
    }

    // Calculate decoder time using the enqueue time we recorded
    // presentationTimeUs: presentation timestamp in microseconds (from MediaCodec)
    // Returns: decoder time in milliseconds
    private fun calculateDecoderTime(presentationTimeUs: Long): Long {
        // Look up the enqueue time for this timestamp (stored in milliseconds)
        val enqueueTimeMs = frameTimestampTracker.takeEnqueueTime(presentationTimeUs, -1L)
        if (enqueueTimeMs >= 0) {
            val delta = SystemClock.uptimeMillis() - enqueueTimeMs
            return if (delta > 0 && delta < 1000) delta else 0
        }
        // If we can't find the enqueue time, return 0
        return 0
    }
}
