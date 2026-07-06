package com.limelight.binding.video

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 抖动监控数据采集器（自足、跨 pacing 模式）。
 *
 * 由 pacing 线程在每帧「呈现落点」调用 [recordPresent] 写入相邻呈现间隔样本；
 * UI 线程调用 [snapshot] 读取快照绘制图表。所有统计（judder%、jit(MAD)、fps、直方图）
 * 直接从呈现间隔推导，不依赖 host-cadence 时钟，因此 BALANCED/EXPERIMENTAL/PRECISE_SYNC 通用。
 *
 * 关闭时零开销：[recordPresent]/[snapshot] 首行做一次 volatile 读即返回，不采样、不加锁、不分配。
 * 开启后稳态无 GC：环形缓冲与直方图桶均预分配。
 *
 * 线程模型：单个 pacing 线程写、单个 UI 线程读，写读共用一把锁（仅 enabled 时进入）。
 */
object JitterMonitor {

    /** 采样窗口容量（时间线最多显示的样本数）。 */
    const val CAPACITY = 240

    /** 呈现间隔按 vsync 取整后统计的最大桶（含）。超出并入溢出桶。 */
    private const val MAX_UNITS = 8

    @Volatile
    var enabled: Boolean = false

    private val lock = Any()

    // 环形缓冲：最近 CAPACITY 个呈现间隔（毫秒）及其 vsync 单位数
    private val intervalMs = FloatArray(CAPACITY)
    private val intervalUnits = IntArray(CAPACITY)
    private var head = 0
    private var size = 0

    // vsync 单位直方图（0..MAX_UNITS，末桶为溢出）
    private val histogram = LongArray(MAX_UNITS + 1)
    private var histTotal = 0L

    private var lastPresentNs = 0L
    private var frameIntervalNs = 0L
    private var pacingModeLabel: String = "-"

    /** 复用的快照对象，避免每次 snapshot 分配（仅 UI 线程持有）。 */
    private val snap = Snapshot()

    /**
     * 重置采集器并绑定当前刷新周期。串流启动或分辨率/刷新率变化时调用。
     * @param refreshRateHz 显示刷新率（Hz）；<=0 时按 60 处理。
     * @param modeLabel pacing 模式标签（用于图表标题）。
     */
    fun reset(refreshRateHz: Int, modeLabel: String) {
        synchronized(lock) {
            frameIntervalNs = (1_000_000_000.0 / (if (refreshRateHz > 0) refreshRateHz else 60)).toLong()
            pacingModeLabel = modeLabel
            head = 0
            size = 0
            histTotal = 0
            lastPresentNs = 0
            java.util.Arrays.fill(histogram, 0L)
        }
    }

    /**
     * 记录一帧的呈现落点（本地单调钟纳秒）。由 pacing 线程调用。
     * 关闭态：一次 volatile 读即返回，零开销。
     */
    fun recordPresent(presentNs: Long) {
        if (!enabled) return
        synchronized(lock) {
            if (frameIntervalNs <= 0) return
            val prev = lastPresentNs
            lastPresentNs = presentNs
            if (prev == 0L) return
            val deltaNs = presentNs - prev
            // 异常间隔（回退/超大空档）忽略，避免污染统计
            if (deltaNs <= 0 || deltaNs > frameIntervalNs * (MAX_UNITS + 4)) return

            val units = (deltaNs.toDouble() / frameIntervalNs).roundToLong().toInt()
            val bucket = units.coerceIn(0, MAX_UNITS)

            intervalMs[head] = deltaNs / 1_000_000f
            intervalUnits[head] = bucket
            head = (head + 1) % CAPACITY
            if (size < CAPACITY) size++

            histogram[bucket]++
            histTotal++
        }
    }

    /**
     * 生成当前快照供 UI 绘制。返回复用对象——仅 UI 线程调用，不可跨线程持有。
     * @return 若未启用或无数据返回 null。
     */
    fun snapshot(): Snapshot? {
        if (!enabled) return null
        synchronized(lock) {
            if (size == 0 || histTotal == 0L) return null

            // 拷贝时间线（按时间顺序：最旧 → 最新）
            val n = size
            if (snap.intervalsMs.size != CAPACITY) return null
            var idx = (head - size + CAPACITY) % CAPACITY
            var sumMs = 0.0
            for (i in 0 until n) {
                val ms = intervalMs[idx]
                snap.intervalsMs[i] = ms
                snap.units[i] = intervalUnits[idx]
                sumMs += ms
                idx = (idx + 1) % CAPACITY
            }
            snap.count = n
            val meanMs = sumMs / n

            // jit = 呈现间隔的平均绝对偏差（MAD），衡量抖动幅度
            var madSum = 0.0
            idx = (head - size + CAPACITY) % CAPACITY
            for (i in 0 until n) {
                madSum += abs(intervalMs[idx] - meanMs)
                idx = (idx + 1) % CAPACITY
            }
            snap.jitterMs = (madSum / n).toFloat()
            snap.avgIntervalMs = meanMs.toFloat()
            snap.fps = if (meanMs > 0) (1000.0 / meanMs).toFloat() else 0f

            // 直方图 + 众数 + judder%
            var mode = 0
            var modeCount = 0L
            for (u in histogram.indices) {
                val c = histogram[u]
                snap.histogram[u] = c
                if (c > modeCount) { modeCount = c; mode = u }
            }
            snap.modeUnits = mode
            snap.histTotal = histTotal
            snap.judderPct = if (histTotal > 0) 100f * (histTotal - modeCount) / histTotal else 0f
            snap.modeLabel = pacingModeLabel
            return snap
        }
    }

    /** UI 侧快照（可复用）。字段仅在 [snapshot] 返回后、下一次 snapshot 前有效。 */
    class Snapshot {
        val intervalsMs = FloatArray(CAPACITY)
        val units = IntArray(CAPACITY)
        var count = 0
        val histogram = LongArray(MAX_UNITS + 1)
        var histTotal = 0L
        var modeUnits = 0
        var judderPct = 0f
        var jitterMs = 0f
        var avgIntervalMs = 0f
        var fps = 0f
        var modeLabel = "-"
    }
}
