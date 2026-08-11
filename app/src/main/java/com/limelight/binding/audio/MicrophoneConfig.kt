package com.limelight.binding.audio

import android.content.Context
import com.limelight.preferences.PreferenceConfiguration

/**
 * 麦克风配置类
 * 管理麦克风相关的配置参数
 */
object MicrophoneConfig {
    // 音频参数
    const val SAMPLE_RATE = 48000 // 采样率
    const val CHANNELS = 1 // 声道数（单声道）
    private var opusBitrateValue = 64 // Opus编码比特率 (默认64 kbps)

    // 网络参数
    const val MAX_QUEUE_SIZE = 5

    // 权限请求码
    const val PERMISSION_REQUEST_MICROPHONE = 1001

    // 延迟参数
    const val PERMISSION_DELAY_MS = 100 // 权限授予后的延迟时间

    // 音频连续性参数
    const val FRAME_SIZE_MS = 20 // Opus帧大小 (毫秒)
    const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 每帧采样数 (960)
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * CHANNELS * 2 // 每帧字节数 (1920)

    // 发送线程参数
    const val SENDER_THREAD_SLEEP_MS = 5 // 发送线程睡眠时间

    // 音频捕获优化参数
    const val CAPTURE_BUFFER_SIZE_MS = 40 // 捕获缓冲区大小 (毫秒)
    const val CAPTURE_BUFFER_SIZE = SAMPLE_RATE * CAPTURE_BUFFER_SIZE_MS / 1000 * CHANNELS * 2 // 捕获缓冲区字节数
    const val FRAME_INTERVAL_MS = 20 // 帧间隔时间 (毫秒)
    const val FRAME_INTERVAL_NS = FRAME_INTERVAL_MS * 1000000L // 帧间隔纳秒

    // 音频质量参数
    const val ENABLE_AUDIO_SYNC = true // 启用音频同步

    // 回声消除和音频处理参数
    private var enableAEC = true // 启用回声消除器
    private var enableAGC = true // 启用自动增益控制
    private var enableNS = true // 启用噪声抑制
    private var useVoiceComm = false // 使用VOICE_COMMUNICATION音频源（自动启用AEC+AGC+NS）

    // 音量增益及其平衡参数
    private var volumeProcessingEnabled = false // 音量增益及其平衡总开关
    private var volumeGainEnabled = false // 音量增益（固定增益模式）
    private var volumeGainDb = 0 // 固定增益 dB (-20 ~ +20, 0dB为原始音量)
    private var volumeBalanceEnabled = false // 音量平衡（自动增益模式）
    private var volumeBalanceTargetPercent = 50 // 平衡目标音量百分比 (1-100)
    private var voiceEnhancementEnabled = true // 人声增强独立开关（默认开启）

    /**
     * 获取当前配置的Opus比特率
     * @return 比特率（bps）
     */
    fun getOpusBitrate(): Int {
        return opusBitrateValue
    }

    /**
     * 设置Opus比特率
     * @param bitrateKbps 比特率（kbps）
     */
    fun setOpusBitrate(bitrateKbps: Int) {
        opusBitrateValue = bitrateKbps * 1000 // 转换为bps
    }

    /**
     * 从配置中更新比特率设置
     * @param context 上下文
     */
    fun updateBitrateFromConfig(context: Context?) {
        if (context != null) {
            val config = PreferenceConfiguration.readPreferences(context)
            setOpusBitrate(config.micBitrate)
        }
    }

    /**
     * 从配置中更新音量增益及其平衡设置
     * @param context 上下文
     */
    fun updateVolumeProcessingFromConfig(context: Context?) {
        if (context != null) {
            val config = PreferenceConfiguration.readPreferences(context)
            volumeProcessingEnabled = config.micVolumeProcessingEnabled
            volumeGainEnabled = config.micGainEnabled
            volumeGainDb = config.micGainDb
            volumeBalanceEnabled = config.micBalanceEnabled
            volumeBalanceTargetPercent = config.micBalanceTargetPercent
            voiceEnhancementEnabled = config.micVoiceEnhancementEnabled
        }
    }

    // ========== 回声消除和音频处理配置方法 ==========

    /**
     * 是否启用回声消除器(AEC)
     */
    fun enableAcousticEchoCanceler(): Boolean {
        return enableAEC
    }

    /**
     * 设置是否启用回声消除器(AEC)
     */
    fun setEnableAcousticEchoCanceler(enable: Boolean) {
        enableAEC = enable
    }

    /**
     * 是否启用自动增益控制(AGC)
     * 使用软件音量处理（音量增益及其平衡）时禁用硬件AGC，避免两者互相干扰
     */
    fun enableAutomaticGainControl(): Boolean {
        return enableAGC && !softwareVolumeProcessingActive()
    }

    /**
     * 设置是否启用自动增益控制(AGC)
     */
    fun setEnableAutomaticGainControl(enable: Boolean) {
        enableAGC = enable
    }

    /**
     * 是否启用噪声抑制(NS)
     */
    fun enableNoiseSuppressor(): Boolean {
        return enableNS
    }

    /**
     * 设置是否启用噪声抑制(NS)
     */
    fun setEnableNoiseSuppressor(enable: Boolean) {
        enableNS = enable
    }

    /**
     * 是否使用VOICE_COMMUNICATION音频源
     * VOICE_COMMUNICATION会自动启用系统级的AEC、AGC、NS
     */
    fun useVoiceCommunication(): Boolean {
        return useVoiceComm && !softwareVolumeProcessingActive()
    }

    /**
     * 设置是否使用VOICE_COMMUNICATION音频源
     */
    fun setUseVoiceCommunication(use: Boolean) {
        useVoiceComm = use
    }

    // ========== 音量增益及其平衡配置方法 ==========

    /**
     * 是否启用音量增益及其平衡（总开关）
     */
    fun isVolumeProcessingEnabled(): Boolean {
        return volumeProcessingEnabled
    }

    /**
     * 是否启用音量增益（固定增益模式）
     */
    fun isVolumeGainEnabled(): Boolean {
        return volumeGainEnabled && volumeProcessingEnabled
    }

    /**
     * 获取固定增益 dB (-20 ~ +20, 0dB为原始音量)
     */
    fun getVolumeGainDb(): Int {
        return volumeGainDb
    }

    /**
     * 是否启用音量平衡（自动增益模式）
     */
    fun isVolumeBalanceEnabled(): Boolean {
        return volumeBalanceEnabled && volumeProcessingEnabled
    }

    /**
     * 获取平衡模式目标音量百分比 (1-100)
     */
    fun getVolumeBalanceTargetPercent(): Int {
        return volumeBalanceTargetPercent
    }

    /**
     * 是否启用独立的人声增强（音量增益/音量平衡模式下均可生效）
     */
    fun isVoiceEnhancementEnabled(): Boolean {
        return voiceEnhancementEnabled
    }

    /**
     * 软件音量处理是否实际生效（任一子功能开启时）
     */
    private fun softwareVolumeProcessingActive(): Boolean {
        return volumeProcessingEnabled && (volumeGainEnabled || volumeBalanceEnabled)
    }
}
