package com.limelight.binding.video

import android.annotation.SuppressLint
import android.app.Activity
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.SparseLongArray
import android.view.Choreographer
import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.locks.LockSupport

/**
 * Controls frame output timing for decoded video frames.
 * Supports Choreographer-based (balanced/experimental), PreciseSync (busy-wait),
 * and VsyncCallback (API 33+) modes.
 *
 * Extracted from MediaCodecDecoderRenderer for separation of concerns.
 */
internal class FramePacingController(
    private val callbacks: Callbacks,
    private val prefs: PreferenceConfiguration,
    private val activity: Activity,
) : Choreographer.FrameCallback {

    companion object {
        private const val HOST_CADENCE_MAX_FUTURE_FRAMES = 3L
        private const val HOST_CADENCE_INVALID_LOG_INTERVAL_NS = 1_000_000_000L
    }

    interface Callbacks {
        fun onFrameRendered()
        fun onDecoderException(e: IllegalStateException): Boolean
        fun onCodecRecoveryCheck(flag: Int): Boolean
    }

    private var videoDecoder: MediaCodec? = null
    private var refreshRate = 0

    @Volatile
    private var stopping = false

    // Output buffer queue for buffered pacing modes (BALANCED, EXPERIMENTAL, PRECISE_SYNC).
    // One extra slot is reserved for the shutdown sentinel.
    val outputBufferQueue = ArrayBlockingQueue<Int>(prefs.outputBufferQueueLimit + 1)

    // ---- PRECISE_SYNC 两步 host-cadence 呈现（step1: host-PI 去抖 → step2: snap 到本地 vsync）----
    // 由隐藏设置项 checkbox_enable_host_cadence_precise_sync 控制（默认开）。
    // 关闭时 PRECISE_SYNC 完全走原有本地网格逻辑，行为零变化；
    // 开启后仅在 PRECISE_SYNC 用 host PTS 复现主机出帧节奏(step1)，再 snap 到最近 vsync 上沿(step2)，
    // 消除"本地自由网格 vs 主机节奏"错拍的周期性判抖/重复丢帧；因有 snap 兜底，cushion 取低档(0.5×MAD, floor0)，
    // 延迟增量约 +1~3ms(已离线仿真验证)。仅本线程访问，故 HostCadenceClock 无需线程安全。
    private val useHostCadencePreciseSync = prefs.enableHostCadencePreciseSync
    private val preciseHostCadenceClock =
        HostCadenceClock(cushionMul = 0.5, cushionFloorNs = 0L, enableDebugStats = BuildConfig.DEBUG)

    // 旁挂 host-cadence step1 目标(纳秒,本地单调钟)通道，键为 bufferIndex；不改 outputBufferQueue 的 Int 类型，
    // 故 BALANCED/EXPERIMENTAL 路径零影响。仅上述开关开启且 PRECISE_SYNC 时填充，poll/丢弃/清空时同步移除，无泄漏。
    // 关键：step1 时钟在"帧到达时"(offerOutputBuffer, 解码回调线程)采样，避免把 pacing 排队抖动注入 instOffset；
    // loop 出队时只做 step2 的 vsync snap。时钟因此仅被到达线程单线程访问，无需线程安全。
    private val hostTargetByIndex = SparseLongArray(prefs.outputBufferQueueLimit + 1)
    private val hostTargetLock = Any()
    private var lastHostCadenceInvalidLogNs = 0L

    // present-interval 对照埋点：两步开/关都记录同一指标(相邻呈现间隔，以 vsync 为单位)，
    // 捕捉"错拍 dup/drop"判抖，供开/关态用同一把尺对比。仅 DEBUG，release 关闭零开销。
    private val presentStats = if (BuildConfig.DEBUG) PresentStats() else null

    // Choreographer state
    private var lastRenderedFrameTimeNanos = 0L
    private var choreographerHandlerThread: HandlerThread? = null
    private var choreographerHandler: Handler? = null
    private var appVsyncOffsetNs = 0L
    private var presentationDeadlineNs = 0L

    // PreciseSync state
    private var surfaceFlingerThread: Thread? = null

    @Volatile
    private var surfaceFlingerActive = false
    private var surfaceFlingerLastFrameTime = 0L
    private var surfaceFlingerFrameInterval = 0L
    private var surfaceFlingerFrameCount = 0
    private var surfaceFlingerSkippedFrames = 0
    private var surfaceFlingerTargetTime = 0L
    private var surfaceFlingerTimingError = 0L

    fun start(decoder: MediaCodec, refreshRate: Int) {
        this.videoDecoder = decoder
        this.refreshRate = refreshRate
        this.stopping = false
        JitterMonitor.reset(refreshRate, framePacingLabel())
        startChoreographerThread()
        startSurfaceFlingerThread()
    }

    fun updateDecoder(decoder: MediaCodec) {
        this.videoDecoder = decoder
    }

    fun hasActiveTimingThread(): Boolean =
        choreographerHandlerThread != null || surfaceFlingerThread != null

    fun prepareForStop() {
        if (stopping) return
        stopping = true
        surfaceFlingerActive = false

        surfaceFlingerThread?.interrupt()

        val currentChoreographerThread = choreographerHandlerThread
        choreographerHandler?.post {
            Choreographer.getInstance().removeFrameCallback(this)
            currentChoreographerThread?.quit()
        }

        // Signal any pacing loop that happens to observe the queue during shutdown.
        outputBufferQueue.offer(-1)
    }

    fun joinThreads() {
        choreographerHandlerThread?.runCatching { join() }
        surfaceFlingerThread?.runCatching { join() }
    }

    fun clearBuffers() {
        outputBufferQueue.clear()
        clearHostTargets()
    }

    /**
     * Enqueues a decoded frame for pacing. If the queue is full, the oldest frame
     * is released without rendering to prevent decoder starvation.
     *
     * @param hostPtsUs host PTS(微秒，以首帧为原点)；仅 PRECISE_SYNC 两步呈现开启时使用，
     *                  其余模式/未传入(-1)时忽略，行为与旧签名一致。
     *                  此处(帧到达点)即调用 step1 时钟算出目标呈现时刻，避免出队排队抖动污染 instOffset。
     */
    fun offerOutputBuffer(bufferIndex: Int, hostPtsUs: Long = -1L) {
        while (outputBufferQueue.size >= prefs.outputBufferQueueLimit) {
            val dropped = outputBufferQueue.poll() ?: break
            try {
                takeHostTarget(dropped)
                if (dropped >= 0) {
                    videoDecoder?.releaseOutputBuffer(dropped, false)
                }
            } catch (_: IllegalStateException) {
                // Buffer index may be stale after codec recovery
            }
        }
        if (useHostCadencePreciseSync && hostPtsUs >= 0 &&
            prefs.framePacing == PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC
        ) {
            // step1: 帧到达即用 host-PI 去抖时钟算目标(时钟内部采样 nowNs=到达时刻)
            val targetNs =
                preciseHostCadenceClock.presentTimeNs(hostPtsUs, effectiveFrameIntervalNs())
            synchronized(hostTargetLock) {
                hostTargetByIndex.put(bufferIndex, targetNs)
            }
        }
        if (!outputBufferQueue.offer(bufferIndex)) {
            takeHostTarget(bufferIndex)
            try {
                videoDecoder?.releaseOutputBuffer(bufferIndex, false)
            } catch (_: IllegalStateException) {
                // Buffer index may be stale after codec recovery
            }
        }
    }

    fun getSurfaceFlingerFrameCount(): Int = surfaceFlingerFrameCount

    fun getSurfaceFlingerSkippedFrames(): Int = surfaceFlingerSkippedFrames

    /** 安全的帧间隔（纳秒）：surfaceFlinger 未初始化时回退到 refreshRate 推导，避免除零/0 间隔。 */
    private fun effectiveFrameIntervalNs(): Long =
        if (surfaceFlingerFrameInterval > 0) surfaceFlingerFrameInterval
        else 1_000_000_000L / (if (refreshRate > 0) refreshRate else 60)

    /** PRECISE_SYNC 两步 host-cadence 呈现是否处于激活态（需要上游注入 host PTS）。 */
    fun isHostCadencePreciseSyncActive(): Boolean =
        useHostCadencePreciseSync &&
            prefs.framePacing == PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC

    /** 抖动图表标题用的 pacing 模式短标签。 */
    private fun framePacingLabel(): String = when {
        isHostCadencePreciseSyncActive() -> "精确同步(增强)"
        prefs.framePacing == PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC -> "精确同步"
        prefs.framePacing == PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY -> "低延迟"
        prefs.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED -> "均衡"
        else -> "平滑"
    }

    // ==================== Choreographer mode ====================

    override fun doFrame(frameTimeNanos: Long) {
        if (stopping) return

        var adjustedTime = frameTimeNanos - appVsyncOffsetNs

        // Don't render unless a new frame is due. This prevents microstutter when streaming
        // at a frame rate that doesn't match the display (such as 60 FPS on 120 Hz).
        val actualFrameTimeDeltaNs = adjustedTime - lastRenderedFrameTimeNanos
        val expectedFrameTimeDeltaNs = 800_000_000L / refreshRate // within 80% of the next frame

        if (actualFrameTimeDeltaNs >= expectedFrameTimeDeltaNs) {
            val nextOutputBuffer = outputBufferQueue.poll()
            if (nextOutputBuffer != null && nextOutputBuffer >= 0) {
                if (prefs.framePacing == PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY) {
                    // 实验性低延迟模式：安全的提前量不超过V-Sync周期的1/2
                    adjustedTime -= 500_000_000L / refreshRate
                }
                try {
                    videoDecoder?.releaseOutputBuffer(nextOutputBuffer, adjustedTime)
                    lastRenderedFrameTimeNanos = adjustedTime
                    JitterMonitor.recordPresent(adjustedTime)
                    callbacks.onFrameRendered()
                } catch (_: IllegalStateException) {
                    try {
                        videoDecoder?.releaseOutputBuffer(nextOutputBuffer, false)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                        callbacks.onDecoderException(e)
                    }
                }
            }
        }

        // Attempt codec recovery even if we have nothing to render right now.
        if (stopping) return
        callbacks.onCodecRecoveryCheck(MediaCodecDecoderRenderer.CR_FLAG_CHOREOGRAPHER)

        // Request another callback for next frame
        if (!stopping) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startChoreographerThread() {
        if (prefs.framePacing != PreferenceConfiguration.FRAME_PACING_BALANCED &&
            prefs.framePacing != PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY
        ) return

        cacheDisplayTiming(includePresentationDeadline = false)

        val thread = HandlerThread(
            "Video - Choreographer",
            if (prefs.framePacing == PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY)
                Process.THREAD_PRIORITY_DISPLAY
            else
                Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_MORE_FAVORABLE
        ).also { it.start() }

        choreographerHandlerThread = thread
        choreographerHandler = Handler(thread.looper).also { handler ->
            handler.post { Choreographer.getInstance().postFrameCallback(this) }
        }
    }

    // ==================== PreciseSync mode ====================

    private fun startSurfaceFlingerThread() {
        if (prefs.framePacing != PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC) return

        LimeLog.info("启动精确同步模式")
        cacheDisplayTiming(includePresentationDeadline = true)
        surfaceFlingerActive = true
        surfaceFlingerFrameInterval = (1_000_000_000.0 / refreshRate).toLong()
        surfaceFlingerTargetTime = System.nanoTime() + surfaceFlingerFrameInterval
        surfaceFlingerLastFrameTime = System.nanoTime()
        surfaceFlingerFrameCount = 0
        surfaceFlingerSkippedFrames = 0
        surfaceFlingerTimingError = 0

        val fVsyncOffset = appVsyncOffsetNs
        val fDeadline = presentationDeadlineNs

        surfaceFlingerThread = Thread {
            Thread.currentThread().name = "Video - Precise Sync"
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            } catch (e: Exception) {
                LimeLog.warning("无法设置精确同步线程优先级: ${e.message}")
            }
            runSurfaceFlingerLoop(fVsyncOffset, fDeadline)
            LimeLog.info("精确同步模式线程结束")
        }.also { it.start() }
    }

    @Suppress("DEPRECATION")
    private fun cacheDisplayTiming(includePresentationDeadline: Boolean) {
        presentationDeadlineNs = 0L
        val display = try {
            activity.windowManager.defaultDisplay
        } catch (e: Exception) {
            appVsyncOffsetNs = 0L
            LimeLog.warning("无法获取显示设备: ${e.message}")
            return
        }

        try {
            appVsyncOffsetNs = display.appVsyncOffsetNanos
        } catch (e: Exception) {
            appVsyncOffsetNs = 0L
            LimeLog.warning("无法获取 Vsync 偏移: ${e.message}")
        }

        if (includePresentationDeadline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                presentationDeadlineNs = display.presentationDeadlineNanos
            } catch (e: Exception) {
                LimeLog.warning("无法获取 Presentation Deadline: ${e.message}")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun runSurfaceFlingerLoop(vsyncOffsetNs: Long, presentationDeadlineNs: Long) {
        while (surfaceFlingerActive && !stopping) {
            try {
                val currentTime = System.nanoTime()
                if (currentTime >= surfaceFlingerTargetTime) {
                    renderNextFrame(currentTime, vsyncOffsetNs, presentationDeadlineNs)
                    updateTargetTime(currentTime)
                }

                // Participate in codec recovery quiescence (same as Choreographer path)
                callbacks.onCodecRecoveryCheck(MediaCodecDecoderRenderer.CR_FLAG_CHOREOGRAPHER)

                waitForNextFrame()
            } catch (e: Exception) {
                LimeLog.warning("Surface Flinger线程异常: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun renderNextFrame(
        currentTime: Long, vsyncOffsetNs: Long, presentationDeadlineNs: Long
    ) {
        val nextOutputBuffer = outputBufferQueue.poll()
        if (nextOutputBuffer == null || nextOutputBuffer < 0) {
            surfaceFlingerSkippedFrames++
            return
        }
        val hostTargetNs = takeHostTarget(nextOutputBuffer)
        try {
            val presentationTimeNs =
                if (useHostCadencePreciseSync && hostTargetNs != Long.MIN_VALUE && vsyncOffsetNs != 0L) {
                    computeHostCadenceSnapTime(
                        hostTargetNs, currentTime, vsyncOffsetNs, presentationDeadlineNs
                    )
                } else {
                    calculatePresentationTime(currentTime, vsyncOffsetNs, presentationDeadlineNs)
                }
            videoDecoder?.releaseOutputBuffer(nextOutputBuffer, presentationTimeNs)
            if (presentStats != null || JitterMonitor.enabled) {
                // presentationTimeNs==0 表示立即呈现 → 用 currentTime snap 近似 SurfaceFlinger 落点
                val effectivePresentNs = if (presentationTimeNs > 0) presentationTimeNs
                else snapUpToVsync(currentTime, vsyncOffsetNs)
                presentStats?.record(effectivePresentNs)
                JitterMonitor.recordPresent(effectivePresentNs)
            }
            updateTimingStats(currentTime)
        } catch (e: IllegalStateException) {
            LimeLog.warning("精确同步模式渲染异常: ${e.message}")
            callbacks.onDecoderException(e)
        }
    }

    /**
     * PRECISE_SYNC 两步呈现的 step2：把已在帧到达时算好的 host-cadence 目标 snap 到最近 vsync 上沿。
     * step1(host-PI 去抖)已在 offerOutputBuffer 完成，这里不再触碰时钟(保持时钟单线程访问)。
     * @param hostTargetNs offerOutputBuffer 里由时钟算出的目标呈现时刻(本地单调钟)。
     */
    private fun computeHostCadenceSnapTime(
        hostTargetNs: Long, currentTime: Long, vsyncOffsetNs: Long, presentationDeadlineNs: Long
    ): Long {
        val frameIntervalNs = effectiveFrameIntervalNs()
        // 迟到帧：目标已过去 → 以当前时刻为基准 snap（等价立即呈现语义），绝不把网格拽回到达时刻
        val rawNs = if (hostTargetNs < currentTime) currentTime else hostTargetNs

        // step2: snap 向上取整到最近 vsync 边界
        val nextVsyncNs = ((rawNs - vsyncOffsetNs + frameIntervalNs - 1) /
            frameIntervalNs) * frameIntervalNs + vsyncOffsetNs

        if (presentationDeadlineNs > 0) {
            val timeUntilDeadline = nextVsyncNs - presentationDeadlineNs - currentTime
            if (timeUntilDeadline < 0) return 0
        }
        val timeUntilVsync = nextVsyncNs - currentTime
        val maxFutureNs = frameIntervalNs * HOST_CADENCE_MAX_FUTURE_FRAMES
        if (timeUntilVsync < 0 || timeUntilVsync > maxFutureNs) {
            resetInvalidHostCadence(
                "host-cadence 时间戳无效 (距离: ${timeUntilVsync / 1_000_000}ms, " +
                    "上限: ${maxFutureNs / 1_000_000}ms)，重置时钟并立即渲染"
            )
            return 0
        }
        return nextVsyncNs
    }

    private fun resetInvalidHostCadence(message: String) {
        preciseHostCadenceClock.reset()
        clearHostTargets()

        val nowNs = System.nanoTime()
        if (nowNs - lastHostCadenceInvalidLogNs >= HOST_CADENCE_INVALID_LOG_INTERVAL_NS) {
            lastHostCadenceInvalidLogNs = nowNs
            LimeLog.warning(message)
        }
    }

    private fun snapUpToVsync(rawNs: Long, vsyncOffsetNs: Long): Long {
        if (surfaceFlingerFrameInterval <= 0) return rawNs
        return ((rawNs - vsyncOffsetNs + surfaceFlingerFrameInterval - 1) /
            surfaceFlingerFrameInterval) * surfaceFlingerFrameInterval + vsyncOffsetNs
    }

    private fun calculatePresentationTime(
        currentTime: Long, vsyncOffsetNs: Long, presentationDeadlineNs: Long
    ): Long {
        if (vsyncOffsetNs == 0L) return 0

        val nextVsyncNs = ((currentTime - vsyncOffsetNs + surfaceFlingerFrameInterval - 1) /
            surfaceFlingerFrameInterval) * surfaceFlingerFrameInterval + vsyncOffsetNs

        if (presentationDeadlineNs > 0) {
            val timeUntilDeadline = nextVsyncNs - presentationDeadlineNs - currentTime
            if (timeUntilDeadline < 0) return 0
        }

        val timeUntilVsync = nextVsyncNs - currentTime
        if (timeUntilVsync < 0 || timeUntilVsync > 1_000_000_000L) {
            LimeLog.warning("时间戳无效 (距离: ${timeUntilVsync / 1_000_000}ms)，使用立即渲染")
            return 0
        }
        return nextVsyncNs
    }

    @SuppressLint("DefaultLocale")
    private fun updateTimingStats(currentTime: Long) {
        val actualInterval = currentTime - surfaceFlingerLastFrameTime
        if (actualInterval > 0) {
            surfaceFlingerTimingError += (actualInterval - surfaceFlingerFrameInterval)
        }
        surfaceFlingerLastFrameTime = currentTime
        surfaceFlingerFrameCount++
        callbacks.onFrameRendered()

        if (BuildConfig.DEBUG && surfaceFlingerFrameCount % 12000 == 0) {
            val avgError = surfaceFlingerTimingError / 1_000_000.0f / surfaceFlingerFrameCount
            LimeLog.info(
                String.format(
                    "精确同步: %d帧, 跳帧: %d, 平均误差: %.3fms",
                    surfaceFlingerFrameCount, surfaceFlingerSkippedFrames, avgError
                )
            )
        }
    }

    private fun updateTargetTime(currentTime: Long) {
        surfaceFlingerTargetTime += surfaceFlingerFrameInterval
        val timeDrift = Math.abs(currentTime - surfaceFlingerTargetTime)
        if (timeDrift > surfaceFlingerFrameInterval * 2) {
            LimeLog.warning("精确同步: 时间漂移过大 (${timeDrift / 1_000_000}ms)，重新同步")
            surfaceFlingerTargetTime = currentTime + surfaceFlingerFrameInterval
            surfaceFlingerTimingError = 0
        }
    }

    private fun waitForNextFrame() {
        val sleepTimeNs = surfaceFlingerTargetTime - System.nanoTime()
        if (sleepTimeNs <= 0) return

        // Use LockSupport.parkNanos() for efficient waiting, wake early for precision
        if (sleepTimeNs > 1_000_000) { // > 1ms
            LockSupport.parkNanos(sleepTimeNs - 500_000) // Wake 0.5ms early
        } else if (sleepTimeNs > 100_000) { // > 0.1ms
            LockSupport.parkNanos(sleepTimeNs shr 1) // Wait half the time
        }

        // Busy-wait for sub-microsecond precision
        @Suppress("ControlFlowWithEmptyBody")
        while (System.nanoTime() < surfaceFlingerTargetTime) {
        }
    }

    /**
     * present-interval 对照埋点（仅 DEBUG）。统计相邻呈现时间戳间隔（四舍五入到 vsync 个数），
     * 以直方图形式每 [PERIOD] 帧打一行到 logcat：
     *   PS[present] n=.. mode=..vsync judder=..%(非众数占比) hist={1:..,2:..,3:..}
     * 众数即"每帧几个 vsync"(如 60fps@120Hz 应为 2)；非众数占比越低越平滑。
     * 两步开/关都记录同一指标，用同一把尺对比错拍判抖。单线程(surfaceFlinger)访问。
     */
    private inner class PresentStats {
        private var lastPresentNs = 0L
        private var frames = 0L
        private val hist = HashMap<Long, Long>()

        fun record(presentNs: Long) {
            if (surfaceFlingerFrameInterval <= 0) return
            if (lastPresentNs != 0L) {
                val units = Math.round((presentNs - lastPresentNs).toDouble() / surfaceFlingerFrameInterval)
                if (units in 0..8) {
                    hist[units] = (hist[units] ?: 0L) + 1
                    frames++
                    if (frames % PERIOD == 0L) printAndReset()
                }
            }
            lastPresentNs = presentNs
        }

        private fun printAndReset() {
            var mode = 0L; var modeCount = 0L; var total = 0L
            for ((u, c) in hist) { total += c; if (c > modeCount) { modeCount = c; mode = u } }
            val judderPct = if (total > 0) 100.0 * (total - modeCount) / total else 0.0
            val sorted = hist.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
            LimeLog.info(
                String.format(
                    "PS[present] n=%d mode=%dvsync judder=%.2f%% hist={%s}",
                    total, mode, judderPct, sorted
                )
            )
            frames = 0; hist.clear()
        }

        private val PERIOD = 600L
    }

    private fun takeHostTarget(bufferIndex: Int): Long = synchronized(hostTargetLock) {
        val targetNs = hostTargetByIndex.get(bufferIndex, Long.MIN_VALUE)
        hostTargetByIndex.delete(bufferIndex)
        targetNs
    }

    private fun clearHostTargets() {
        synchronized(hostTargetLock) {
            hostTargetByIndex.clear()
        }
    }

}
