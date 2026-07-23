package com.limelight.binding.audio

import android.content.Context
import android.os.SystemClock

import com.limelight.LimeLog
import com.limelight.binding.input.ControllerHandler
import com.moonlight.haptics.HapticFrame
import com.moonlight.haptics.android.AndroidHapticRenderer
import com.moonlight.haptics.android.NativeHapticsSession

/**
 * Audio-driven vibration service for Android.
 *
 * Moonlight Audio Haptics SDK produces portable haptic intent. This service
 * applies user routing and strength, then sends it to the SDK's Android renderer
 * and the controller rumble mixer.
 */
class AudioVibrationService(context: Context) {

    private data class RuntimeSettings(
        val enabled: Boolean,
        val strength: Int,
        val vibrationMode: String,
        val sceneMode: Int
    )

    @Volatile
    private var runtimeSettings = RuntimeSettings(
        enabled = false,
        strength = 100, // 0-200; values above 100 are boost headroom
        vibrationMode = MODE_AUTO,
        sceneMode = SCENE_GAME
    )

    // State
    @Volatile
    private var isSdkDeviceActive = false
    @Volatile
    private var isSystemAudioCoupledDeviceActive = false
    private var isGamepadRumbling = false

    private var lastHapticTimestampUs: Long = -1
    private var rhythmStatsStartMs: Long = SystemClock.elapsedRealtime()
    private var rhythmTransientCount = 0
    private var rhythmPredictedCount = 0
    private var rhythmDiagnosticCount = 0
    private var rhythmActivationSum = 0f
    private var rhythmLowSupportSum = 0f
    private var lastMusicGrooveBedAmplitude = 0f
    private var lastControllerWatchdogRefreshMs = 0L
    private val sdkDeviceRenderer: AndroidHapticRenderer
    private val nativeSession: NativeHapticsSession

    val nativeSessionHandle: Long
        get() = nativeSession.nativeHandle

    val systemAudioCoupledDeviceActive: Boolean
        get() = isSystemAudioCoupledDeviceActive

    // Gamepad rumble handler (optional, set externally)
    var controllerHandler: ControllerHandler? = null
        set(value) {
            if (field === value) return
            field?.stopAudioRumble()
            field = value
        }

    init {
        nativeSession = NativeHapticsSession(
            listener = { frame -> handleHapticFrame(frame) },
            initialScene = runtimeSettings.sceneMode
        )
        sdkDeviceRenderer = AndroidHapticRenderer(
            context.applicationContext,
            workerLooper = nativeSession.deliveryLooper
        )
        LimeLog.info("AudioHaptics: deviceProfile=${sdkDeviceRenderer.deviceProfileId}")
    }

    fun setSettings(enabled: Boolean, strength: Int, vibrationMode: String, sceneMode: Int) {
        val previous = runtimeSettings
        val updated = RuntimeSettings(
            enabled = enabled,
            strength = strength.coerceIn(0, MAX_STRENGTH),
            vibrationMode = vibrationMode,
            sceneMode = sceneMode
        )
        if (updated.sceneMode != previous.sceneMode) {
            val onsetSensitivity = if (updated.sceneMode == SCENE_MUSIC) {
                MUSIC_ONSET_SENSITIVITY
            } else {
                DEFAULT_ONSET_SENSITIVITY
            }
            nativeSession.setSensitivity(onsetSensitivity)
            nativeSession.setScene(updated.sceneMode)
        }
        runtimeSettings = updated
        if (updated.enabled != previous.enabled ||
            updated.vibrationMode != previous.vibrationMode ||
            updated.sceneMode != previous.sceneMode
        ) {
            LimeLog.info(
                "AudioVibration: scene=${updated.sceneMode}, strength=${updated.strength}"
            )
        }

        if (!updated.enabled) {
            stopAll()
        }
    }

    val sceneModeInt: Int
        get() = runtimeSettings.sceneMode

    /**
     * Render a platform-independent HapticFrame produced by moonlight-haptics-core.
     */
    private fun handleHapticFrame(frame: HapticFrame) {
        val settings = runtimeSettings
        if (!settings.enabled || frame.timestampUs < lastHapticTimestampUs) return
        lastHapticTimestampUs = frame.timestampUs

        val activeScene = frame.activeScene.takeIf { it in SCENE_GAME..SCENE_AUTO }
            ?: settings.sceneMode

        val hasTransient = frame.hasFlag(HapticFrame.FLAG_TRANSIENT)
        val continuousChanged = frame.hasFlag(HapticFrame.FLAG_CONTINUOUS_CHANGED)
        val shouldStop = frame.hasFlag(HapticFrame.FLAG_STOP)
        val isMusic = activeScene == SCENE_MUSIC
        if (shouldStop) {
            stopSdkOutput()
            if (!hasTransient) return
        }
        if (!hasTransient && !continuousChanged) return

        val isMusicTransient = hasTransient && isMusic
        val isRhythmPredicted = isMusicTransient &&
            frame.hasFlag(HapticFrame.FLAG_RHYTHM_PREDICTED)
        if (isMusicTransient) {
            rhythmTransientCount++
            if (isRhythmPredicted) rhythmPredictedCount++
        }
        if (isMusic) {
            rhythmDiagnosticCount++
            rhythmActivationSum += frame.rhythmActivation
            rhythmLowSupportSum += frame.rhythmLowFrequencySupport
        }
        val rhythmStatsNowMs = SystemClock.elapsedRealtime()
        if (isMusic && rhythmStatsNowMs - rhythmStatsStartMs >= RHYTHM_STATS_INTERVAL_MS) {
            val diagnosticDivisor = rhythmDiagnosticCount.coerceAtLeast(1).toFloat()
            val latency = sdkDeviceRenderer.takeLatencySnapshot()
            LimeLog.info(
                "AudioHaptics rhythm: candidate=${frame.rhythmTempoBpm.toInt()} " +
                    "locked=${frame.rhythmLocked} " +
                    "coasting=${frame.rhythmCoasting} " +
                    "confidence=${(frame.rhythmConfidence * 100f).toInt()}% " +
                    "activation=${(rhythmActivationSum * 100f / diagnosticDivisor).toInt()}% " +
                    "lowSupport=${(rhythmLowSupportSum * 100f / diagnosticDivisor).toInt()}% " +
                    "grooveBed=${(lastMusicGrooveBedAmplitude * 100f).toInt()}% " +
                    "transients=$rhythmTransientCount predicted=$rhythmPredictedCount " +
                    "rendered=${latency.renderedCount} " +
                    "dispatchUs=${latency.dispatchP50Us}/${latency.dispatchP95Us}/${latency.dispatchP99Us} " +
                    "audioSkewUs=${latency.audioSkewP50Us}/${latency.audioSkewP95Us}/${latency.audioSkewP99Us} " +
                    "stale=${latency.staleTransientDrops} " +
                    "superseded=${latency.supersededTransientDrops} " +
                    "scheduled=${latency.scheduledFrames} " +
                    "clock=${latency.clockAcceptedDecisions}/${latency.clockRejectedDecisions} " +
                    "clockAgeUs=${latency.latestClockAgeUs} " +
                    "streamDeltaUs=${latency.latestStreamClockDeltaUs} " +
                    "targetDeltaUs=${latency.latestRawTargetDeltaUs}"
            )
            rhythmStatsStartMs = rhythmStatsNowMs
            rhythmTransientCount = 0
            rhythmPredictedCount = 0
            rhythmDiagnosticCount = 0
            rhythmActivationSum = 0f
            rhythmLowSupportSum = 0f
        }
        // Core owns scene authoring. The host applies only the user's global
        // strength; the SDK renderer maps portable intent to actuator limits.
        val continuous = (
            frame.continuousAmplitude.coerceIn(0f, 1f) * settings.strength / 100f
        ).coerceIn(0f, 1f)
        val transient = (
            frame.transientAmplitude.coerceIn(0f, 1f) * settings.strength / 100f
        ).coerceIn(0f, 1f)
        val transientDurationMs = frame.transientDurationMs
        val musicGrooveBedActive = isMusic && continuous >= MIN_IR_AMPLITUDE
        val rendererFlags = frame.flags
        val selectedAmplitude = if (hasTransient) {
            maxOf(transient, continuous)
        } else {
            continuous
        }
        // Transient device floors are applied inside the SDK renderer. Do not
        // discard a valid portable transient before it reaches that policy.
        if (!hasTransient && selectedAmplitude < MIN_IR_AMPLITUDE) {
            if (continuousChanged) stopSdkOutput()
            return
        }

        if (shouldVibrateDevice(settings.vibrationMode) && !isSystemAudioCoupledDeviceActive) {
            val accepted = sdkDeviceRenderer.submit(
                frame.timestampUs,
                rendererFlags,
                continuous,
                transient,
                transientDurationMs,
                frame.sharpness,
                frame.lowBandRatio,
                frame.stereoPan,
                frame.confidence,
                frame.activeScene,
                frame.producerTimeUs
            )
            if (accepted) {
                controllerHandler?.claimDeviceVibratorForAudio()
                isSdkDeviceActive = true
                if (isMusic) {
                    lastMusicGrooveBedAmplitude = if (musicGrooveBedActive) continuous else 0f
                }
            }
        } else if (isSdkDeviceActive) {
            sdkDeviceRenderer.stop()
            isSdkDeviceActive = false
            lastMusicGrooveBedAmplitude = 0f
        }

        if (shouldVibrateGamepad(settings.vibrationMode)) {
            val lowBandRatio = frame.lowBandRatio.coerceIn(0f, 1f)
            val sharpness = frame.sharpness.coerceIn(0f, 1f)
            val continuousLow = continuous * (0.55f + 0.45f * lowBandRatio)
            val continuousHigh = continuous * (1f - lowBandRatio) * 0.25f
            val transientLow = transient *
                (0.25f + 0.75f * lowBandRatio) *
                (1f - 0.35f * sharpness)
            val transientHigh = transient * (0.35f + 0.65f * sharpness)
            controllerHandler?.submitAudioHaptics(
                continuousLow,
                continuousHigh,
                transientLow,
                transientHigh,
                transientDurationMs.toInt(),
                hasTransient,
                continuousChanged
            )
            isGamepadRumbling = true
        } else if (isGamepadRumbling) {
            stopGamepadRumble()
        }

    }

    fun stop() {
        nativeSession.stop()
        sdkDeviceRenderer.clearAudioPresentationClock()
        stopAll()
    }

    /** True when Android's system audio-coupled backend should own the phone motor. */
    fun wantsSystemAudioCoupledDeviceHaptics(): Boolean {
        val settings = runtimeSettings
        return shouldRequestSystemAudioCoupledHaptics(
            settings.enabled,
            settings.sceneMode,
            settings.vibrationMode,
            hasConnectedGamepad()
        )
    }

    /** Called by the AudioTrack owner after HapticGenerator attach/detach. */
    fun setSystemAudioCoupledDeviceActive(active: Boolean) {
        if (isSystemAudioCoupledDeviceActive == active) return
        isSystemAudioCoupledDeviceActive = active
        if (active) {
            controllerHandler?.claimDeviceVibratorForAudio()
        }
        if (active && isSdkDeviceActive) {
            sdkDeviceRenderer.stop()
            isSdkDeviceActive = false
            lastMusicGrooveBedAmplitude = 0f
        }
        LimeLog.info("AudioHaptics: system audio-coupled device active=$active")
    }

    /** Updates the SDK renderer's mapping from stream samples to audible presentation time. */
    fun updateAudioPresentationClock(
        framePosition: Long,
        systemNanoTime: Long,
        sampleRate: Int
    ) {
        sdkDeviceRenderer.updateAudioPresentationClock(
            framePosition,
            systemNanoTime,
            sampleRate
        )
        if (framePosition >= 0L && sampleRate > 0) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastControllerWatchdogRefreshMs >= CONTROLLER_WATCHDOG_REFRESH_MS) {
                lastControllerWatchdogRefreshMs = nowMs
                controllerHandler?.refreshAudioRumbleWatchdog()
            }
        }
    }

    fun release() {
        stopAll()
        isSystemAudioCoupledDeviceActive = false
        sdkDeviceRenderer.close()
        nativeSession.close()
    }

    // ==================== Routing ====================

    private fun shouldVibrateDevice(vibrationMode: String): Boolean =
        shouldRouteAudioToDevice(vibrationMode, hasConnectedGamepad())

    private fun shouldVibrateGamepad(vibrationMode: String): Boolean =
        shouldRouteAudioToGamepad(vibrationMode, hasConnectedGamepad())

    private fun hasConnectedGamepad(): Boolean =
        controllerHandler?.hasRumbleCapableController() == true

    private fun stopGamepadRumble() {
        controllerHandler?.stopAudioRumble()
        isGamepadRumbling = false
    }

    // ==================== Stop ====================

    private fun stopAll() {
        sdkDeviceRenderer.stop()
        isSdkDeviceActive = false
        if (isGamepadRumbling) {
            stopGamepadRumble()
        }
        lastHapticTimestampUs = -1
        lastControllerWatchdogRefreshMs = 0L
        rhythmStatsStartMs = SystemClock.elapsedRealtime()
        rhythmTransientCount = 0
        rhythmPredictedCount = 0
        rhythmDiagnosticCount = 0
        rhythmActivationSum = 0f
        rhythmLowSupportSum = 0f
        lastMusicGrooveBedAmplitude = 0f
    }

    private fun stopSdkOutput() {
        sdkDeviceRenderer.stop()
        isSdkDeviceActive = false
        lastMusicGrooveBedAmplitude = 0f
        lastControllerWatchdogRefreshMs = 0L
        if (isGamepadRumbling) stopGamepadRumble()
    }

    companion object {
        // Scene modes defined by the public Moonlight Audio Haptics SDK ABI.
        const val SCENE_GAME = 0
        const val SCENE_MUSIC = 1
        const val SCENE_AUTO = 2

        // Vibration routing modes
        const val MODE_AUTO = "auto"
        const val MODE_DEVICE_ONLY = "device"
        const val MODE_GAMEPAD_ONLY = "gamepad"
        const val MODE_BOTH = "both"

        private const val MIN_IR_AMPLITUDE = 0.05f
        const val MAX_STRENGTH = 200
        private const val DEFAULT_ONSET_SENSITIVITY = 1.0f
        private const val MUSIC_ONSET_SENSITIVITY = 2.5f

        private const val RHYTHM_STATS_INTERVAL_MS = 5_000L
        private const val CONTROLLER_WATCHDOG_REFRESH_MS = 1_000L

        internal fun shouldRouteAudioToDevice(
            vibrationMode: String,
            hasRumbleCapableGamepad: Boolean
        ): Boolean = when (vibrationMode) {
            MODE_GAMEPAD_ONLY -> false
            MODE_DEVICE_ONLY, MODE_BOTH -> true
            else -> !hasRumbleCapableGamepad
        }

        internal fun shouldRouteAudioToGamepad(
            vibrationMode: String,
            hasRumbleCapableGamepad: Boolean
        ): Boolean = when (vibrationMode) {
            MODE_DEVICE_ONLY -> false
            MODE_GAMEPAD_ONLY, MODE_BOTH, MODE_AUTO -> hasRumbleCapableGamepad
            else -> hasRumbleCapableGamepad
        }

        @Suppress("UNUSED_PARAMETER")
        internal fun shouldRequestSystemAudioCoupledHaptics(
            enabled: Boolean,
            sceneMode: Int,
            vibrationMode: String,
            hasGamepad: Boolean
        ): Boolean {
            if (!enabled || sceneMode != SCENE_MUSIC) return false
            return when (vibrationMode) {
                MODE_GAMEPAD_ONLY -> false
                MODE_DEVICE_ONLY, MODE_BOTH -> true
                // Auto must remain dynamically routable when a controller is connected or
                // removed. The system HapticGenerator backend is fixed for an AudioTrack's
                // lifetime, so Auto uses the SDK renderer instead.
                else -> false
            }
        }
    }
}
