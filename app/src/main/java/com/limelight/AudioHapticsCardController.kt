package com.limelight

import com.limelight.binding.audio.AudioVibrationService
import com.limelight.preferences.PreferenceConfiguration

internal data class AudioHapticsSettings(
    val enabled: Boolean,
    val strength: Int,
    val mode: String,
    val scene: Int
)

internal data class AudioHapticsCardState(
    val enabled: Boolean,
    val strength: Int,
    val mode: String,
    val scene: Int,
    val pendingRestart: Boolean
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

/**
 * Owns the Game Menu audio-haptics state.
 *
 * Preference writes stay here, while [Game] decides whether the active audio
 * backend can safely accept the new settings without restarting the stream.
 */
internal class AudioHapticsCardController(private val game: Game) {
    private var onStateChanged: ((AudioHapticsCardState) -> Unit)? = null
    private var runtimeSettings = game.currentAudioHapticsSettings()
    private var state = readSettings().let { desired ->
        desired.toCardState(pendingRestart = desired != runtimeSettings)
    }

    fun snapshot(): AudioHapticsCardState = state

    fun start(onStateChanged: (AudioHapticsCardState) -> Unit) {
        this.onStateChanged = onStateChanged
        runtimeSettings = game.currentAudioHapticsSettings()
        state = readSettings().let { desired ->
            desired.toCardState(pendingRestart = desired != runtimeSettings)
        }
        emitState()
    }

    fun setEnabled(enabled: Boolean) {
        game.prefConfig.enableAudioVibration = enabled
        applyAndPersist()
    }

    /**
     * Applies strength while dragging for immediate feedback. Persistence is
     * intentionally deferred until [persistStrength] to avoid preference I/O
     * for every slider sample.
     */
    fun previewStrength(strength: Float): Boolean {
        val bounded = strength.toInt().coerceIn(0, AudioVibrationService.MAX_STRENGTH)
        val previous = game.prefConfig.audioVibrationStrength
        if (bounded == previous) return false

        game.prefConfig.audioVibrationStrength = bounded
        val applied = game.applyAudioHapticsStrength(bounded)
        if (applied) {
            runtimeSettings = runtimeSettings.copy(strength = bounded)
        }
        val desired = readSettings()
        state = desired.toCardState(pendingRestart = !applied || desired != runtimeSettings)
        emitState()
        return bounded / HAPTIC_STEP != previous / HAPTIC_STEP
    }

    fun persistStrength() {
        game.prefConfig.writePreferences(game)
    }

    fun setMode(mode: String) {
        if (mode !in SUPPORTED_MODES || mode == game.prefConfig.audioVibrationMode) return
        game.prefConfig.audioVibrationMode = mode
        applyAndPersist()
    }

    fun setScene(scene: Int) {
        if (scene !in SUPPORTED_SCENES || scene == game.prefConfig.audioVibrationScene) return
        game.prefConfig.audioVibrationScene = scene
        applyAndPersist()
    }

    /** Restores tuning defaults without unexpectedly disabling the feature. */
    fun resetTuning() {
        game.prefConfig.audioVibrationStrength =
            PreferenceConfiguration.DEFAULT_AUDIO_VIBRATION_STRENGTH
        game.prefConfig.audioVibrationMode =
            PreferenceConfiguration.DEFAULT_AUDIO_VIBRATION_MODE
        game.prefConfig.audioVibrationScene =
            PreferenceConfiguration.DEFAULT_AUDIO_VIBRATION_SCENE
        applyAndPersist()
    }

    fun dispose() {
        onStateChanged = null
    }

    private fun applyAndPersist() {
        applyCurrentSettings()
        game.prefConfig.writePreferences(game)
    }

    private fun applyCurrentSettings() {
        val desired = readSettings()
        val applied = when {
            desired == runtimeSettings -> true
            game.applyAudioHapticsSettings(desired) -> {
                runtimeSettings = desired
                true
            }
            else -> false
        }
        state = desired.toCardState(pendingRestart = !applied)
        emitState()
    }

    private fun readSettings(): AudioHapticsSettings {
        val prefs = game.prefConfig
        return AudioHapticsSettings(
            enabled = prefs.enableAudioVibration,
            strength = prefs.audioVibrationStrength.coerceIn(
                0,
                AudioVibrationService.MAX_STRENGTH
            ),
            mode = prefs.audioVibrationMode.takeIf { it in SUPPORTED_MODES }
                ?: PreferenceConfiguration.DEFAULT_AUDIO_VIBRATION_MODE,
            scene = prefs.audioVibrationScene.takeIf { it in SUPPORTED_SCENES }
                ?: PreferenceConfiguration.DEFAULT_AUDIO_VIBRATION_SCENE
        )
    }

    private fun AudioHapticsSettings.toCardState(
        pendingRestart: Boolean
    ): AudioHapticsCardState {
        return AudioHapticsCardState(
            enabled = enabled,
            strength = strength,
            mode = mode,
            scene = scene,
            pendingRestart = pendingRestart
        )
    }

    private fun emitState() {
        onStateChanged?.invoke(state)
    }

    companion object {
        const val HAPTIC_STEP = 5
        private val SUPPORTED_MODES = setOf(
            AudioVibrationService.MODE_AUTO,
            AudioVibrationService.MODE_DEVICE_ONLY,
            AudioVibrationService.MODE_GAMEPAD_ONLY,
            AudioVibrationService.MODE_BOTH
        )
        private val SUPPORTED_SCENES = setOf(
            AudioVibrationService.SCENE_GAME,
            AudioVibrationService.SCENE_MUSIC,
            AudioVibrationService.SCENE_AUTO
        )
    }
}
