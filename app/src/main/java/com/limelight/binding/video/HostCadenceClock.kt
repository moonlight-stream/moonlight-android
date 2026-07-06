package com.limelight.binding.video

import com.limelight.LimeLog

/**
 * host-cadence 去抖呈现时钟（alpha-beta / PI 时钟恢复）。
 *
 * 将主机 host PTS（以首个被捕获帧为原点、源自 common-c RtpVideoQueue 的 90kHz RTP 时间戳）
 * 恢复为本地单调钟（System.nanoTime 基准）的目标呈现时刻，滤除网络抖动与主机/客户端时钟频差。
 * 与鸿蒙 native_render::CalculatePresentTime 同一模型、同一常数（Kp=1/64、Ki=1/2048），已离线仿真验证。
 *
 * 用法：每帧调用 [presentTimeNs]；流不连续（重连/seek/编码器重启）时内部自动重锚，
 * 也可显式 [reset] 强制重锚。返回值可能早于 now：调用方按“过去时间戳=立即呈现”处理，
 * 网格保持刚性连续（勿把网格拽到到达时刻）。
 *
 * 线程约束：非线程安全，持有内部滤波状态；每条呈现路径应各自持有独立实例。
 *
 * cushion 由构造方注入而非写死，因为其安全下限取决于下游是否有 vsync snap 兜底：
 *  - 直渲染路径（MAX_SMOOTHNESS/CAP_FPS，无 snap）：一帧迟到即可见 hitch，需较大 cushion
 *    （[cushionMul]=3.0、[cushionFloorNs]=1ms），覆盖整个抖动分布。
 *  - PRECISE_SYNC 路径（有 vsync snap）：snap 向上取整自带 ~0.5 vsync 隐性余量，迟到帧只落到
 *    下一个 vsync 槽（= 正常帧行为），故 cushion 可低至 0.5×MAD floor0（延迟增量 +1~3ms）。
 *
 * @param cushionMul    自适应 cushion = cushionMul × 抖动估计(MAD)。
 * @param cushionFloorNs cushion 下限（纳秒）；上限恒为一帧间隔。
 * @param enableDebugStats 为 true 时周期性把标定指标(迟到率/残差分位/jitterEst/skew/cushion)打到
 *                  logcat(tag=Limelight)，供真机标定 cushion；应由调用方传入 BuildConfig.DEBUG，
 *                  release 构建关闭、零开销。
 */
class HostCadenceClock(
    private val cushionMul: Double = 0.5,
    private val cushionFloorNs: Long = 0L,
    private val enableDebugStats: Boolean = false,
) {
    private var initialized = false
    private var estimatedOffsetNs = 0L   // 平滑后的 (本地单调钟 - host PTS) 偏移均值(纳秒)
    private var skewNs = 0L              // 每帧频差估计(纳秒/帧)，消除时钟 skew 斜坡滞后
    private var jitterEstNs = 0.0        // 在线抖动估计(平均绝对偏差, 纳秒)，驱动自适应 cushion
    private var lastHostPtsUs = 0L       // 上一帧 host PTS(微秒)，检测不连续(重连/跳变)

    private val stats = if (enableDebugStats) Stats() else null

    /** 强制下一帧重锚（如流重启/surface 重建后调用）。 */
    fun reset() {
        initialized = false
    }

    /**
     * 计算本帧目标呈现时刻。
     *
     * @param hostPtsUs        host PTS（微秒，以首个被捕获帧为原点）。
     * @param frameIntervalNs  一帧标称间隔（纳秒），用于 cushion 上限与抖动初值；调用方按显示刷新率给出。
     * @return 本地单调钟(nanoTime 基准)的目标呈现时刻(纳秒)；可能早于当前时刻。
     */
    fun presentTimeNs(hostPtsUs: Long, frameIntervalNs: Long): Long {
        val nowNs = System.nanoTime()
        val hostNs = hostPtsUs * 1000L
        val instOffset = nowNs - hostNs

        val discontinuity = initialized &&
            (hostPtsUs < lastHostPtsUs || (hostPtsUs - lastHostPtsUs) > 2_000_000L)

        var residualE = 0L
        var haveResidual = false
        if (!initialized || discontinuity) {
            estimatedOffsetNs = instOffset
            skewNs = 0
            jitterEstNs = frameIntervalNs / 16.0
            initialized = true
        } else {
            val pred = estimatedOffsetNs + skewNs
            val e = instOffset - pred
            var ec = e
            if (ec > 8_000_000L) ec = 8_000_000L else if (ec < -8_000_000L) ec = -8_000_000L
            estimatedOffsetNs = pred + (ec / 64)    // Kp=1/64: 跟踪偏移均值、网格近似刚性
            skewNs += (ec / 2048)                    // Ki=1/2048: 跟踪频差，消除斜坡滞后
            val ae = if (e < 0) (-e).toDouble() else e.toDouble()
            jitterEstNs += (ae - jitterEstNs) / 32.0
            residualE = e
            haveResidual = true
        }
        lastHostPtsUs = hostPtsUs

        var cushionNs = (cushionMul * jitterEstNs).toLong()
        if (cushionNs < cushionFloorNs) cushionNs = cushionFloorNs
        else if (cushionNs > frameIntervalNs) cushionNs = frameIntervalNs

        val target = hostNs + estimatedOffsetNs + cushionNs
        stats?.record(
            nowNs, target, residualE, haveResidual, jitterEstNs, cushionNs, estimatedOffsetNs, skewNs
        )
        return target
    }

    /**
     * Debug 标定统计（仅 enableDebugStats=true）。单线程访问（与时钟同一路径），非线程安全。
     * 每 [PERIOD_FRAMES] 帧打印一行到 logcat：
     *   HC[stats] n=.. late=..(x.xx%) maxLate=..ms | e(ms) p50/p95/p99/max | jit=.. cush=.. off=.. skew=..
     * 其中残差分位由带符号直方图近似估计（桶边界见 [BUCKET_EDGES_NS]），
     * 迟到率 = target<now 的帧占比（cushion 偏小的直接信号），maxLate = 最大迟到量。
     */
    private class Stats {
        private var frames = 0L
        private var lateFrames = 0L
        private var maxLateNs = 0L
        private val hist = IntArray(BUCKET_EDGES_NS.size + 1)
        private var lastCushionNs = 0L
        private var lastOffsetNs = 0L
        private var lastSkewNs = 0L
        private var lastJitterNs = 0.0

        fun record(
            nowNs: Long, targetNs: Long, residualE: Long, haveResidual: Boolean,
            jitterNs: Double, cushionNs: Long, offsetNs: Long, skewNs: Long
        ) {
            frames++
            val lateNs = nowNs - targetNs
            if (lateNs > 0) {
                lateFrames++
                if (lateNs > maxLateNs) maxLateNs = lateNs
            }
            if (haveResidual) {
                var b = 0
                while (b < BUCKET_EDGES_NS.size && residualE >= BUCKET_EDGES_NS[b]) b++
                hist[b]++
            }
            lastCushionNs = cushionNs
            lastOffsetNs = offsetNs
            lastSkewNs = skewNs
            lastJitterNs = jitterNs

            if (frames % PERIOD_FRAMES == 0L) {
                printAndReset()
            }
        }

        private fun printAndReset() {
            val latePct = 100.0 * lateFrames / frames
            LimeLog.info(
                String.format(
                    "HC[stats] n=%d late=%d(%.2f%%) maxLate=%.2fms | e(ms) p50=%.2f p95=%.2f p99=%.2f max=%.2f " +
                        "| jit=%.2f cush=%.2f off=%.2f skew=%.3f",
                    frames, lateFrames, latePct, maxLateNs / 1e6,
                    percentileMs(0.50), percentileMs(0.95), percentileMs(0.99), percentileMs(1.0),
                    lastJitterNs / 1e6, lastCushionNs / 1e6, lastOffsetNs / 1e6, lastSkewNs / 1e6
                )
            )
            frames = 0; lateFrames = 0; maxLateNs = 0
            hist.fill(0)
        }

        // 带符号残差分位的直方图近似：返回该分位所在桶的上边界（ms），最后一桶用 >8ms 记为 8ms 上限。
        private fun percentileMs(p: Double): Double {
            var total = 0L
            for (c in hist) total += c
            if (total == 0L) return 0.0
            val threshold = (p * total).toLong().coerceAtLeast(1L)
            var acc = 0L
            for (i in hist.indices) {
                acc += hist[i]
                if (acc >= threshold) {
                    val edgeNs = if (i < BUCKET_EDGES_NS.size) BUCKET_EDGES_NS[i] else BUCKET_EDGES_NS.last()
                    return edgeNs / 1e6
                }
            }
            return BUCKET_EDGES_NS.last() / 1e6
        }

        companion object {
            private const val PERIOD_FRAMES = 600L // ≈10s @60fps
            // 带符号残差 e 的桶上边界(纳秒)：-4,-2,-1,-0.5,0,0.5,1,2,4,8 ms
            private val BUCKET_EDGES_NS = longArrayOf(
                -4_000_000L, -2_000_000L, -1_000_000L, -500_000L, 0L,
                500_000L, 1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L
            )
        }
    }
}
