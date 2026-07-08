package com.limelight.utils

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

import com.limelight.BuildConfig
import com.limelight.LimeLog

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class FrameMetricsLogger(
    private val activity: Activity,
    private val label: String
) {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private val lock = Any()
    private var running = false
    private var reported = false
    private var startElapsedNs = 0L
    private var frameBudgetNs = DEFAULT_60HZ_FRAME_NS

    private var frameCount = 0
    private var totalDurationNs = 0L
    private var maxDurationNs = 0L
    private var jankFrames = 0
    private var slowFrames24Ms = 0
    private var slowFrames32Ms = 0
    private var frozenFrames48Ms = 0
    private var layoutMeasureNs = 0L
    private var drawNs = 0L
    private var syncNs = 0L
    private var commandIssueNs = 0L
    private val frameDurationsNs = ArrayList<Long>(REPORT_WINDOW_MS.toInt() / 8)

    fun start() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.N || running) {
            return
        }

        running = true
        reported = false
        resetCounters()
        frameBudgetNs = resolveFrameBudgetNs()
        startElapsedNs = SystemClock.elapsedRealtimeNanos()

        val thread = HandlerThread("FrameMetrics-$label")
        thread.start()
        handlerThread = thread
        handler = Handler(thread.looper)

        val metricsListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            recordFrame(frameMetrics)
        }
        listener = metricsListener
        activity.window.addOnFrameMetricsAvailableListener(metricsListener, handler)

        handler?.postDelayed({
            report("window")
            stop(reportIfNeeded = false)
        }, REPORT_WINDOW_MS)
    }

    fun stop(reportIfNeeded: Boolean = true) {
        if (!running) return

        if (reportIfNeeded) {
            report("stop")
        }

        listener?.let { activity.window.removeOnFrameMetricsAvailableListener(it) }
        listener = null
        handler?.removeCallbacksAndMessages(null)
        handler = null
        handlerThread?.quitSafely()
        handlerThread = null
        running = false
    }

    private fun resetCounters() {
        synchronized(lock) {
            frameCount = 0
            totalDurationNs = 0L
            maxDurationNs = 0L
            jankFrames = 0
            slowFrames24Ms = 0
            slowFrames32Ms = 0
            frozenFrames48Ms = 0
            layoutMeasureNs = 0L
            drawNs = 0L
            syncNs = 0L
            commandIssueNs = 0L
            frameDurationsNs.clear()
        }
    }

    private fun recordFrame(frameMetrics: FrameMetrics) {
        val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (totalNs <= 0L) return

        synchronized(lock) {
            frameCount++
            totalDurationNs += totalNs
            maxDurationNs = max(maxDurationNs, totalNs)
            frameDurationsNs.add(totalNs)

            if (totalNs > frameBudgetNs) jankFrames++
            if (totalNs > MS_24_NS) slowFrames24Ms++
            if (totalNs > MS_32_NS) slowFrames32Ms++
            if (totalNs > MS_48_NS) frozenFrames48Ms++

            layoutMeasureNs += frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            drawNs += frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            syncNs += frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
            commandIssueNs += frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
        }
    }

    private fun report(reason: String) {
        val message = synchronized(lock) {
            if (reported) return
            reported = true

            if (frameCount == 0) {
                return@synchronized "FrameMetrics[$label]: no frames captured ($reason)"
            }

            val sortedDurationsNs = frameDurationsNs.sorted()
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startElapsedNs) / NS_PER_MS.toDouble()
            val avgMs = totalDurationNs / frameCount / NS_PER_MS.toDouble()
            val jankPercent = jankFrames * 100.0 / frameCount
            val budgetMs = frameBudgetNs / NS_PER_MS.toDouble()

            String.format(
                Locale.US,
                "FrameMetrics[%s/%s]: %.0fms frames=%d avg=%.2fms p50=%.2fms p90=%.2fms " +
                    "p95=%.2fms max=%.2fms budget=%.2fms jank=%d(%.1f%%) >24ms=%d " +
                    ">32ms=%d >48ms=%d layout=%.2fms draw=%.2fms sync=%.2fms cmd=%.2fms",
                label,
                reason,
                elapsedMs,
                frameCount,
                avgMs,
                percentileMs(sortedDurationsNs, 0.50),
                percentileMs(sortedDurationsNs, 0.90),
                percentileMs(sortedDurationsNs, 0.95),
                maxDurationNs / NS_PER_MS.toDouble(),
                budgetMs,
                jankFrames,
                jankPercent,
                slowFrames24Ms,
                slowFrames32Ms,
                frozenFrames48Ms,
                averageMs(layoutMeasureNs),
                averageMs(drawNs),
                averageMs(syncNs),
                averageMs(commandIssueNs)
            )
        }

        log(message)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        LimeLog.info(message)
    }

    private fun averageMs(valueNs: Long): Double {
        return valueNs / frameCount / NS_PER_MS.toDouble()
    }

    private fun percentileMs(sortedValuesNs: List<Long>, percentile: Double): Double {
        val index = (ceil(percentile * sortedValuesNs.size).toInt() - 1)
            .coerceIn(0, sortedValuesNs.lastIndex)
        return sortedValuesNs[index] / NS_PER_MS.toDouble()
    }

    @Suppress("DEPRECATION")
    private fun resolveFrameBudgetNs(): Long {
        val refreshRate = activity.windowManager.defaultDisplay.refreshRate
        return if (refreshRate > 0f) {
            (NS_PER_SECOND / refreshRate).toLong()
        } else {
            DEFAULT_60HZ_FRAME_NS
        }
    }

    companion object {
        private const val TAG = "FrameMetricsLogger"
        private const val REPORT_WINDOW_MS = 10_000L
        private const val NS_PER_MS = 1_000_000L
        private const val NS_PER_SECOND = 1_000_000_000L
        private const val DEFAULT_60HZ_FRAME_NS = 16_666_667L
        private const val MS_24_NS = 24L * NS_PER_MS
        private const val MS_32_NS = 32L * NS_PER_MS
        private const val MS_48_NS = 48L * NS_PER_MS
    }
}
