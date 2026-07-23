package com.limelight.binding.audio

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import androidx.annotation.RequiresApi

import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.binding.input.ControllerHandler
import com.moonlight.haptics.HapticFrame
import com.moonlight.haptics.android.AndroidHapticRenderer
import com.moonlight.haptics.android.NativeHapticsSession

/**
 * Audio-driven vibration service for Android.
 *
 * Receives bass energy intensity (0-100) and low-frequency ratio (0-100) from the
 * native BassEnergyAnalyzer (via JNI callback) and routes vibration to:
 *   - Device vibrator with tiered haptic API support
 *   - Gamepad rumble with dynamic low/high motor allocation
 *
 * Haptic capability tiers (auto-detected):
 *   - ENVELOPE (API 36+): BasicEnvelopeBuilder — intensity + sharpness + duration envelope
 *   - COMPOSITION (API 31+): Primitives — THUD/CLICK with scale control
 *   - ONE_SHOT (API 26+): createOneShot — duration + amplitude
 *   - LEGACY (pre-26): simple vibrate(ms)
 *
 * Scene modes:
 *   - Game/Movie (0): Continuous low-freq vibration for explosions/gunfire/engines
 *   - Music/Rhythm (1): Short pulse vibration for beats/onsets
 *   - Auto (2): C++ layer auto-detects content type
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
    private var lastIntensity = 0
    private var lastLowFreqRatio = 50
    private var isDeviceVibrating = false
    @Volatile
    private var isSdkDeviceActive = false
    @Volatile
    private var isSystemAudioCoupledDeviceActive = false
    private var isGamepadRumbling = false

    // Debounce: tightened intervals matching HarmonyOS
    private var lastVibrationTime: Long = 0
    private var lastHapticTimestampUs: Long = -1
    private var rhythmStatsStartMs: Long = SystemClock.elapsedRealtime()
    private var rhythmTransientCount = 0
    private var rhythmPredictedCount = 0
    private var rhythmDiagnosticCount = 0
    private var rhythmActivationSum = 0f
    private var rhythmLowSupportSum = 0f
    private var lastMusicGrooveBedAmplitude = 0f
    private var lastControllerWatchdogRefreshMs = 0L
    // Android vibrator & capability
    private val deviceVibrator: Vibrator?
    private val hapticLevel: Int
    private val sdkDeviceRenderer: AndroidHapticRenderer
    private val nativeSession: NativeHapticsSession?

    val nativeSessionHandle: Long
        get() = nativeSession?.nativeHandle ?: 0L

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
        nativeSession = if (BuildConfig.AUDIO_HAPTICS_OUTPUT) {
            NativeHapticsSession(
                listener = { frame -> handleHapticFrame(frame) },
                initialScene = runtimeSettings.sceneMode
            )
        } else {
            null
        }
        sdkDeviceRenderer = AndroidHapticRenderer(
            context.applicationContext,
            workerLooper = nativeSession?.deliveryLooper
        )
        deviceVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        hapticLevel = detectHapticCapability()
        LimeLog.info(
            "AudioVibration: haptic level = ${hapticLevelName()}, " +
                "deviceProfile=${sdkDeviceRenderer.deviceProfileId}"
        )
    }

    private fun detectHapticCapability(): Int {
        if (deviceVibrator == null || !deviceVibrator.hasVibrator()) {
            return HAPTIC_LEGACY
        }

        // Tier 3: API 36+ BasicEnvelopeBuilder
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                if (deviceVibrator.areEnvelopeEffectsSupported()) {
                    return HAPTIC_ENVELOPE
                }
            } catch (_: Exception) {}
        }

        // Tier 2: API 31+ Composition with THUD & CLICK primitives
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val supported = deviceVibrator.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                )
                if (supported[0] && supported[1]) {
                    return HAPTIC_COMPOSITION
                }
            } catch (_: Exception) {}
        }

        // Tier 1: API 26+ createOneShot with amplitude control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && deviceVibrator.hasAmplitudeControl()) {
            return HAPTIC_ONE_SHOT
        }

        return HAPTIC_LEGACY
    }

    private fun hapticLevelName(): String = when (hapticLevel) {
        HAPTIC_ENVELOPE -> "ENVELOPE (API 36+)"
        HAPTIC_COMPOSITION -> "COMPOSITION (API 31+)"
        HAPTIC_ONE_SHOT -> "ONE_SHOT (API 26+)"
        else -> "LEGACY"
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
            nativeSession?.setSensitivity(onsetSensitivity)
            nativeSession?.setScene(updated.sceneMode)
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
     * Handle bass energy from native layer.
     *
     * @param intensity Bass energy intensity (0-100)
     * @param lowFreqRatio Low-frequency energy ratio (0-100), for motor allocation
     */
    fun handleBassEnergy(intensity: Int, lowFreqRatio: Int) {
        val settings = runtimeSettings
        if (!settings.enabled) return

        if (intensity == 0) {
            if (isDeviceVibrating || isGamepadRumbling) {
                stopAll()
            }
            return
        }

        val effectiveIntensity = (intensity * settings.strength / 100).coerceIn(0, 100)
        if (effectiveIntensity < 5) {
            if (isDeviceVibrating || isGamepadRumbling) {
                stopAll()
            }
            return
        }

        // Debounce
        val now = System.currentTimeMillis()
        val isMusic = isMusicScene(settings.sceneMode)
        val minInterval = if (isMusic) MIN_INTERVAL_MUSIC_MS else MIN_INTERVAL_GAME_MS
        if (now - lastVibrationTime < minInterval) {
            return
        }

        // Skip if change too small
        val changeTolerance = if (isMusic) 3 else 8
        if ((isDeviceVibrating || isGamepadRumbling) &&
            Math.abs(effectiveIntensity - lastIntensity) < changeTolerance
        ) {
            return
        }

        lastIntensity = effectiveIntensity
        lastLowFreqRatio = lowFreqRatio
        lastVibrationTime = now

        // Route vibration
        val shouldDevice = shouldVibrateDevice(settings.vibrationMode)
        val shouldGamepad = shouldVibrateGamepad(settings.vibrationMode)

        if (shouldDevice && !isSystemAudioCoupledDeviceActive) {
            triggerDeviceVibration(effectiveIntensity, isMusic)
        } else if (isDeviceVibrating) {
            stopDeviceVibration()
        }

        if (shouldGamepad) {
            triggerGamepadRumble(effectiveIntensity, lowFreqRatio, isMusic)
        } else if (isGamepadRumbling) {
            stopGamepadRumble()
        }
    }

    /**
     * Render a platform-independent HapticFrame produced by moonlight-haptics-core.
     * This is mutually exclusive with handleBassEnergy() at the Game integration layer.
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

        lastIntensity = (selectedAmplitude * 100f).toInt().coerceIn(0, 100)
        lastLowFreqRatio = (frame.lowBandRatio.coerceIn(0f, 1f) * 100f).toInt()

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
        nativeSession?.stop()
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
        nativeSession?.close()
    }

    // ==================== Scene detection ====================

    private fun isMusicScene(sceneMode: Int): Boolean {
        return sceneMode == SCENE_MUSIC || sceneMode == SCENE_AUTO
    }

    // ==================== Routing ====================

    private fun shouldVibrateDevice(vibrationMode: String): Boolean =
        shouldRouteAudioToDevice(vibrationMode, hasConnectedGamepad())

    private fun shouldVibrateGamepad(vibrationMode: String): Boolean =
        shouldRouteAudioToGamepad(vibrationMode, hasConnectedGamepad())

    private fun hasConnectedGamepad(): Boolean =
        controllerHandler?.hasRumbleCapableController() == true

    // ==================== Device Vibration ====================

    private fun triggerDeviceVibration(intensity: Int, isMusic: Boolean) {
        if (deviceVibrator == null || !deviceVibrator.hasVibrator()) {
            return
        }

        if (isDeviceVibrating) {
            deviceVibrator.cancel()
        }

        try {
            if (isMusic) {
                triggerMusicVibration(intensity)
            } else {
                triggerGameVibration(intensity)
            }
            controllerHandler?.claimDeviceVibratorForAudio()
            isDeviceVibrating = true
        } catch (e: Exception) {
            LimeLog.warning("AudioVibration: " + e.message)
        }
    }

    // ==================== Game mode vibration (tiered) ====================

    private fun triggerGameVibration(intensity: Int) {
        when (hapticLevel) {
            HAPTIC_ENVELOPE -> if (Build.VERSION.SDK_INT >= 36) {
                triggerGameEnvelope(intensity)
            }
            HAPTIC_COMPOSITION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                triggerGameComposition(intensity)
            }
            HAPTIC_ONE_SHOT -> triggerGameOneShot(intensity)
            else -> vibrateSimple(50 + intensity * 250 / 100)
        }
    }

    /**
     * Game envelope: deep sustained rumble (≈300ms).
     * Sharpness 0.1-0.3 → low frequency, equivalent to HarmonyOS HD Haptic 30-50Hz.
     */
    @RequiresApi(36)
    private fun triggerGameEnvelope(intensity: Int) {
        val amp = intensity / 100f
        val sharpness = 0.1f + amp * 0.2f // 0.1-0.3: deep rumble
        try {
            val effect = VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(sharpness)
                .addControlPoint(amp, sharpness, 20)          // attack: 20ms ramp up
                .addControlPoint(amp * 0.6f, sharpness, 200)  // sustain+decay: 200ms
                .addControlPoint(0f, sharpness, 80)           // release: 80ms fade out
                .build()
            vibrateWithAttributes(effect)
        } catch (_: Exception) {
            triggerGameOneShot(intensity)
        }
    }

    /**
     * Game composition: PRIMITIVE_THUD for heavy impact feel.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun triggerGameComposition(intensity: Int) {
        val scale = (intensity / 100f).coerceAtLeast(0.1f)
        try {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale)
                .compose()
            vibrateWithAttributes(effect)
        } catch (_: Exception) {
            triggerGameOneShot(intensity)
        }
    }

    /**
     * Game one-shot: 50-300ms with amplitude control.
     */
    private fun triggerGameOneShot(intensity: Int) {
        val duration = 50 + intensity * 250 / 100
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (intensity * 255 / 100).coerceAtLeast(1)
            val effect = VibrationEffect.createOneShot(duration.toLong(), amplitude)
            vibrateWithAttributes(effect)
        } else {
            vibrateSimple(duration)
        }
    }

    // ==================== Music mode vibration (tiered) ====================

    private fun triggerMusicVibration(intensity: Int) {
        when (hapticLevel) {
            HAPTIC_ENVELOPE -> if (Build.VERSION.SDK_INT >= 36) {
                triggerMusicEnvelope(intensity)
            }
            HAPTIC_COMPOSITION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                triggerMusicComposition(intensity)
            }
            HAPTIC_ONE_SHOT -> triggerMusicOneShot(intensity)
            else -> vibrateSimple(30 + intensity / 2)
        }
    }

    /**
     * Music envelope: sharp transient pulse (≈60ms).
     * Sharpness 0.4-0.7 → crisp/snappy, equivalent to HarmonyOS HD Haptic 40-60Hz.
     */
    @RequiresApi(36)
    private fun triggerMusicEnvelope(intensity: Int) {
        val amp = intensity / 100f
        val sharpness = 0.4f + amp * 0.3f // 0.4-0.7: crisp beat
        try {
            val effect = VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(sharpness)
                .addControlPoint(amp, sharpness, 5)    // instant attack: 5ms
                .addControlPoint(0f, sharpness, 55)    // quick decay: 55ms
                .build()
            vibrateWithAttributes(effect)
        } catch (_: Exception) {
            triggerMusicOneShot(intensity)
        }
    }

    /**
     * Music composition: PRIMITIVE_CLICK for crisp beat.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerMusicComposition(intensity: Int) {
        val scale = (intensity / 100f).coerceAtLeast(0.1f)
        try {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                .compose()
            vibrateWithAttributes(effect)
        } catch (_: Exception) {
            triggerMusicOneShot(intensity)
        }
    }

    /**
     * Music one-shot: 30-80ms with amplitude control.
     */
    private fun triggerMusicOneShot(intensity: Int) {
        val duration = 30 + intensity / 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (intensity * 255 / 100).coerceAtLeast(1)
            val effect = VibrationEffect.createOneShot(duration.toLong(), amplitude)
            vibrateWithAttributes(effect)
        } else {
            vibrateSimple(duration)
        }
    }

    // ==================== Vibration helpers ====================

    private fun vibrateWithAttributes(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_MEDIA)
                .build()
            deviceVibrator!!.vibrate(effect, attrs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deviceVibrator!!.vibrate(effect)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateSimple(durationMs: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deviceVibrator!!.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            deviceVibrator!!.vibrate(durationMs.toLong())
        }
    }

    // ==================== Gamepad Rumble ====================

    /**
     * Gamepad rumble with dynamic low/high motor allocation.
     * lowFreqRatio from C++ reflects actual audio frequency content:
     * - High ratio → explosion/bass → low-freq motor dominant
     * - Low ratio → crisp/high-pitched → high-freq motor dominant
     */
    private fun triggerGamepadRumble(
        intensity: Int,
        lowFreqRatio: Int,
        isMusic: Boolean
    ) {
        val handler = controllerHandler ?: return
        val amplitude = (intensity / 100f).coerceIn(0f, 1f)
        val lowSupport = (lowFreqRatio / 100f).coerceIn(0f, 1f)
        val lowFrequency = amplitude * (0.35f + 0.65f * lowSupport)
        val highFrequency = amplitude * (0.35f + 0.65f * (1f - lowSupport))
        val continuous = !isMusic
        val durationMs = if (continuous) {
            50 + intensity * 250 / 100
        } else {
            30 + intensity / 2
        }

        handler.submitAudioRumble(
            lowFrequency,
            highFrequency,
            durationMs,
            continuous
        )
        isGamepadRumbling = true
    }

    private fun stopGamepadRumble() {
        controllerHandler?.stopAudioRumble()
        isGamepadRumbling = false
    }

    // ==================== Stop ====================

    private fun stopAll() {
        sdkDeviceRenderer.stop()
        isSdkDeviceActive = false
        if (isDeviceVibrating) {
            stopDeviceVibration()
        }
        if (isGamepadRumbling) {
            stopGamepadRumble()
        }
        lastIntensity = 0
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

    private fun stopDeviceVibration() {
        deviceVibrator?.cancel()
        isDeviceVibrating = false
    }

    companion object {
        // Scene modes (must match BassEnergyAnalyzer SCENE_* constants)
        const val SCENE_GAME = 0
        const val SCENE_MUSIC = 1
        const val SCENE_AUTO = 2

        // Vibration routing modes
        const val MODE_AUTO = "auto"
        const val MODE_DEVICE_ONLY = "device"
        const val MODE_GAMEPAD_ONLY = "gamepad"
        const val MODE_BOTH = "both"

        // Haptic capability levels
        private const val HAPTIC_LEGACY = 0
        private const val HAPTIC_ONE_SHOT = 1
        private const val HAPTIC_COMPOSITION = 2
        private const val HAPTIC_ENVELOPE = 3

        private const val MIN_INTERVAL_GAME_MS: Long = 25
        private const val MIN_INTERVAL_MUSIC_MS: Long = 15
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
