package com.limelight.binding.audio

internal data class AudioHapticsSettings(
    val enabled: Boolean,
    val strength: Int,
    val mode: String,
    val scene: Int
)

internal object AudioHapticsRuntimePolicy {
    fun canApplyImmediately(
        systemAudioCoupledActive: Boolean,
        applied: AudioHapticsSettings,
        desired: AudioHapticsSettings
    ): Boolean {
        return !systemAudioCoupledActive || applied == desired
    }
}
