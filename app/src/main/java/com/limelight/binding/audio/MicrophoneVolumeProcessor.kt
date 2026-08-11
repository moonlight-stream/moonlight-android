package com.limelight.binding.audio

import com.limelight.LimeLog

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 麦克风音量处理器
 *
 * 两种互斥模式：
 * - 音量增益(GAIN)：固定增益，>0dB 增强，<0dB 减弱（范围 -20 ~ +20dB）
 * - 音量平衡(BALANCE)：手机游戏开麦式语音链——
 *   80Hz 高通（去喷麦/风噪）→ 2400Hz 中频提升（语音清晰度）
 *   → 压缩器（-18dB 阈值, 3:1, attack 5ms / release 150ms）
 *   → 输出响度补偿（make-up gain）→ 峰值限幅器（-1dBFS）
 *
 * 压缩器是消除"声音忽高忽低"的关键：正常语音（低于阈值）增益恒定，
 * 只有喊叫等大声才被压缩，150ms 慢释放避免抽吸感。
 *
 * 人声增强（高通 + 中频提升）为独立开关 [voiceEnhancementEnabled]，
 * 音量增益与音量平衡模式下均可启用。
 *
 * 输入为 PCM 16-bit 小端单声道，按帧原地处理。
 */
class MicrophoneVolumeProcessor {

    private enum class Mode {
        OFF,
        GAIN,
        BALANCE
    }

    private var mode: Mode = Mode.OFF

    /** 固定增益 (dB)，0dB 不处理，范围 -20 ~ +20 */
    private var gainDb = 0

    /** 平衡模式输出响度补偿 (dB)，50% 对应 +4dB 推荐值 */
    private var makeupGainDb = DEFAULT_MAKEUP_GAIN_DB

    /** 人声增强独立开关（默认开启，两种模式均生效） */
    private var voiceEnhancementEnabled = true

    // ---- 人声增强滤波器（系数已除以 a0 归一化，init 中预计算）----
    private val hpB0: Double
    private val hpB1: Double
    private val hpB2: Double
    private val hpA1: Double
    private val hpA2: Double
    private val peB0: Double
    private val peB1: Double
    private val peB2: Double
    private val peA1: Double
    private val peA2: Double

    // 滤波器延迟线（跨帧保持连续性）
    private var hpX1 = 0.0
    private var hpX2 = 0.0
    private var hpY1 = 0.0
    private var hpY2 = 0.0
    private var peX1 = 0.0
    private var peX2 = 0.0
    private var peY1 = 0.0
    private var peY2 = 0.0

    // ---- 压缩器与限幅器状态 ----
    private var compEnv = 0.0
    private var compBlockPeak = 0.0
    private var signalBlockPeak = 0.0

    // 块内滤波输出缓存（复用，零分配），配合增益延迟写回
    private val blockBuffer = DoubleArray(GAIN_UPDATE_INTERVAL)

    init {
        // RBJ biquad 滤波器系数，采样率 48000Hz
        // 高通滤波器（Butterworth，Q = 1/√2，fc = 80Hz）
        val hpW0 = 2.0 * Math.PI * HP_FILTER_FREQ / SAMPLE_RATE
        val hpCosW = cos(hpW0)
        val hpAlpha = sin(hpW0) / sqrt(2.0)
        val hpA0 = 1.0 + hpAlpha
        hpB0 = ((1.0 + hpCosW) / 2.0) / hpA0
        hpB1 = (-(1.0 + hpCosW)) / hpA0
        hpB2 = ((1.0 + hpCosW) / 2.0) / hpA0
        hpA1 = (-2.0 * hpCosW) / hpA0
        hpA2 = (1.0 - hpAlpha) / hpA0

        // 峰值均衡（fc = 2400Hz，Q = 1.0，增益 +4dB）
        val peAmp = 10.0.pow(PRESENCE_GAIN_DB / 40.0)
        val peW0 = 2.0 * Math.PI * PRESENCE_FREQ / SAMPLE_RATE
        val peCosW = cos(peW0)
        val peAlpha = sin(peW0) / 2.0
        val peA0 = 1.0 + peAlpha / peAmp
        peB0 = (1.0 + peAlpha * peAmp) / peA0
        peB1 = (-2.0 * peCosW) / peA0
        peB2 = (1.0 - peAlpha * peAmp) / peA0
        peA1 = (-2.0 * peCosW) / peA0
        peA2 = (1.0 - peAlpha / peAmp) / peA0
    }

    /**
     * 更新处理参数（麦克风捕获启动时调用）
     * @param enabled 音量增益及其平衡总开关
     * @param gainEnabled 是否启用音量增益（与音量平衡互斥，gainEnabled 优先）
     * @param gainDb 固定增益 dB，-20 ~ +20，0dB 为原始音量
     * @param balanceEnabled 是否启用音量平衡
     * @param balanceTargetPercent 输出响度补偿百分比 1-100，50% 对应 +4dB 推荐值
     * @param voiceEnhancementEnabled 是否启用独立的人声增强（默认开启，两种模式均生效）
     */
    fun configure(
        enabled: Boolean,
        gainEnabled: Boolean,
        gainDb: Int,
        balanceEnabled: Boolean,
        balanceTargetPercent: Int,
        voiceEnhancementEnabled: Boolean = true
    ) {
        mode = when {
            !enabled -> Mode.OFF
            gainEnabled -> Mode.GAIN
            balanceEnabled -> Mode.BALANCE
            else -> Mode.OFF
        }
        this.gainDb = gainDb.coerceIn(-20, 20)
        makeupGainDb = balanceTargetPercent.coerceIn(1, 100) * MAKEUP_GAIN_PER_PERCENT
        this.voiceEnhancementEnabled = voiceEnhancementEnabled

        // 切换模式时复位全部状态，避免旧状态残留导致爆音
        resetState()

        if (mode != Mode.OFF) {
            LimeLog.info(
                "麦克风音量处理已启用: 模式=$mode, 增益=${this.gainDb}dB, " +
                    "输出响度补偿=${makeupGainDb}dB, 人声增强=${this.voiceEnhancementEnabled}"
            )
        }
    }

    private fun resetState() {
        hpX1 = 0.0
        hpX2 = 0.0
        hpY1 = 0.0
        hpY2 = 0.0
        peX1 = 0.0
        peX2 = 0.0
        peY1 = 0.0
        peY2 = 0.0
        compEnv = 0.0
        compBlockPeak = 0.0
        signalBlockPeak = 0.0
    }

    /**
     * 处理一帧 PCM 16-bit 小端单声道数据（原地修改）
     * @param data 数据缓冲区
     * @param offset 帧起始偏移
     * @param length 帧字节数（必须是偶数）
     */
    fun processFrame(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && length % 2 == 0 && offset <= data.size - length) {
            "Invalid PCM frame range: offset=$offset length=$length dataSize=${data.size}"
        }
        if (mode == Mode.OFF || length == 0) return

        val sampleCount = length / 2
        when (mode) {
            Mode.GAIN -> applyFixedGain(data, offset, sampleCount)
            Mode.BALANCE -> applyVoiceChain(data, offset, sampleCount)
            Mode.OFF -> Unit
        }
    }

    // ================= 滤波器 =================

    /**
     * 人声增强滤波（80Hz 高通 + 2400Hz 中频提升），逐样本处理。
     * 使用共享延迟线状态，保证跨帧连续。
     */
    private fun filterSample(sample: Double): Double {
        // 高通滤波：去喷麦/风噪/低频轰鸣
        val hpOut = hpB0 * sample + hpB1 * hpX1 + hpB2 * hpX2 - hpA1 * hpY1 - hpA2 * hpY2
        hpX2 = hpX1
        hpX1 = sample
        hpY2 = hpY1
        hpY1 = hpOut

        // 中频人声提升：提升语音清晰度
        val peOut = peB0 * hpOut + peB1 * peX1 + peB2 * peX2 - peA1 * peY1 - peA2 * peY2
        peX2 = peX1
        peX1 = hpOut
        peY2 = peY1
        peY1 = peOut

        return peOut
    }

    // ================= 音量增益模式 =================

    /** 固定增益：人声增强（若开启）+ 按 dB 换算系数并钳位 */
    private fun applyFixedGain(data: ByteArray, offset: Int, sampleCount: Int) {
        val factor = 10.0.pow(gainDb / 20.0)
        val enhance = voiceEnhancementEnabled

        for (i in 0 until sampleCount) {
            val index = offset + i * 2
            val sample = readSample(data, index)
            var value = if (enhance) filterSample(sample) else sample
            if (factor != 1.0) {
                value *= factor
            }
            writeSample(data, index, value)
        }
    }

    // ================= 音量平衡模式 =================

    /**
     * 音量平衡：人声增强（可选）→ 压缩器 → 输出补偿 → 限幅器。
     * 每 [GAIN_UPDATE_INTERVAL] 样本（0.67ms）计算一次增益并批量写回本块
     * 的滤波输出（× 增益），块内增益恒定，避免逐样本跳变产生噪声。
     */
    private fun applyVoiceChain(data: ByteArray, offset: Int, sampleCount: Int) {
        var pendingCount = 0
        val enhance = voiceEnhancementEnabled

        for (i in 0 until sampleCount) {
            val index = offset + i * 2
            val sample = readSample(data, index)

            // 人声增强（可选）+ 缓存滤波输出
            val processed = if (enhance) filterSample(sample) else sample
            blockBuffer[pendingCount] = processed
            pendingCount++

            // 压缩器峰值包络（逐样本平滑，attack 快 / release 慢）
            val peak = abs(processed)
            if (peak > compEnv) {
                compEnv += (peak - compEnv) * COMP_ATTACK_COEFF
            } else {
                compEnv += (peak - compEnv) * COMP_RELEASE_COEFF
            }
            if (compEnv > compBlockPeak) compBlockPeak = compEnv
            if (peak > signalBlockPeak) signalBlockPeak = peak

            // 每块计算一次合并增益并写回
            if (pendingCount == GAIN_UPDATE_INTERVAL) {
                val blockGain = computeBlockGain()
                writeBlock(data, offset + (i + 1 - GAIN_UPDATE_INTERVAL) * 2, GAIN_UPDATE_INTERVAL, blockGain)
                pendingCount = 0
            }
        }

        // 帧尾余数样本也必须按自身峰值计算增益。
        if (pendingCount > 0) {
            val tailGain = computeBlockGain()
            writeBlock(data, offset + (sampleCount - pendingCount) * 2, pendingCount, tailGain)
        }
    }

    /** 基于本块峰值计算合并增益（压缩器 × 限幅器），并复位块峰值 */
    private fun computeBlockGain(): Double {
        val compGain = computeCompressorGain(compBlockPeak)
        val amplifiedPeak = signalBlockPeak * compGain
        val limitGain = if (amplifiedPeak > LIMITER_PEAK) LIMITER_PEAK / amplifiedPeak else 1.0
        compBlockPeak = 0.0
        signalBlockPeak = 0.0
        return compGain * limitGain
    }

    /** 压缩器增益：低于阈值（-18dBFS）时增益恒定（仅输出补偿），超过阈值按 3:1 压缩 */
    private fun computeCompressorGain(blockPeak: Double): Double {
        val envDb = 20.0 * log10(blockPeak / 32768.0 + 1e-12)
        val overDb = envDb - COMP_THRESHOLD_DB
        val reductionDb = if (overDb > 0.0) {
            overDb * (1.0 - 1.0 / COMP_RATIO)
        } else {
            0.0
        }
        return 10.0.pow((makeupGainDb - reductionDb) / 20.0)
    }

    /** 将缓存块内的滤波输出乘上块增益并写回。 */
    private fun writeBlock(data: ByteArray, blockStart: Int, blockLength: Int, blockGain: Double) {
        for (i in 0 until blockLength) {
            writeSample(data, blockStart + i * 2, blockBuffer[i] * blockGain)
        }
    }

    // ================= PCM 读写辅助 =================

    /** 读取 16-bit 小端样本（返回有符号值） */
    private fun readSample(data: ByteArray, index: Int): Double {
        return ((data[index].toInt() and 0xFF) or (data[index + 1].toInt() shl 8)).toDouble()
    }

    /** 钳位并写回 16-bit 小端样本 */
    private fun writeSample(data: ByteArray, index: Int, value: Double) {
        val intVal = value.roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        data[index] = (intVal and 0xFF).toByte()
        data[index + 1] = ((intVal shr 8) and 0xFF).toByte()
    }

    companion object {
        // 人声增强滤波器参数
        private const val HP_FILTER_FREQ = 80.0 // 高通截止频率 (Hz)
        private const val PRESENCE_FREQ = 2400.0 // 中频提升中心频率 (Hz)
        private const val PRESENCE_GAIN_DB = 4.0 // 中频提升增益 (dB)

        // 压缩器参数
        private const val COMP_THRESHOLD_DB = -18.0 // 阈值 (dBFS)
        private const val COMP_RATIO = 3.0 // 压缩比
        private const val COMP_ATTACK_S = 0.005 // 启动时间 5ms（快速响应喊叫）
        private const val COMP_RELEASE_S = 0.15 // 释放时间 150ms（≥100ms，避免忽高忽低）
        private const val DEFAULT_MAKEUP_GAIN_DB = 4.0 // 默认输出响度补偿 +4dB
        private const val MAKEUP_GAIN_PER_PERCENT = 0.08 // 50% → +4dB 的映射系数

        // 限幅器参数（防爆音）
        private const val LIMITER_PEAK = 29204.0 // -1dBFS 峰值 (32768 * 10^(-1/20))

        // 采样率与增益更新间隔
        private const val SAMPLE_RATE = 48000.0
        private const val GAIN_UPDATE_INTERVAL = 32 // 每 32 样本（0.67ms）更新一次增益

        // 逐样本平滑系数（一阶低通）
        private val COMP_ATTACK_COEFF = 1.0 - exp(-1.0 / (SAMPLE_RATE * COMP_ATTACK_S))
        private val COMP_RELEASE_COEFF = 1.0 - exp(-1.0 / (SAMPLE_RATE * COMP_RELEASE_S))
    }
}
