package com.limelight.binding.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioVibrationServiceTest {
    @Test
    fun gamepadOnlyNeverFallsBackToDevice() {
        assertEquals(
            false,
            AudioVibrationService.shouldRouteAudioToDevice(
                AudioVibrationService.MODE_GAMEPAD_ONLY,
                hasRumbleCapableGamepad = false
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRouteAudioToGamepad(
                AudioVibrationService.MODE_GAMEPAD_ONLY,
                hasRumbleCapableGamepad = false
            )
        )
    }

    @Test
    fun autoRoutesOnlyToCapableGamepad() {
        assertEquals(
            true,
            AudioVibrationService.shouldRouteAudioToDevice(
                AudioVibrationService.MODE_AUTO,
                hasRumbleCapableGamepad = false
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRouteAudioToGamepad(
                AudioVibrationService.MODE_AUTO,
                hasRumbleCapableGamepad = false
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRouteAudioToDevice(
                AudioVibrationService.MODE_AUTO,
                hasRumbleCapableGamepad = true
            )
        )
        assertEquals(
            true,
            AudioVibrationService.shouldRouteAudioToGamepad(
                AudioVibrationService.MODE_AUTO,
                hasRumbleCapableGamepad = true
            )
        )
    }

    @Test
    fun systemAudioCoupledHapticsIsMusicDeviceOnlyPolicy() {
        assertEquals(
            true,
            AudioVibrationService.shouldRequestSystemAudioCoupledHaptics(
                enabled = true,
                sceneMode = AudioVibrationService.SCENE_MUSIC,
                vibrationMode = AudioVibrationService.MODE_DEVICE_ONLY,
                hasGamepad = false
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRequestSystemAudioCoupledHaptics(
                enabled = true,
                sceneMode = AudioVibrationService.SCENE_GAME,
                vibrationMode = AudioVibrationService.MODE_DEVICE_ONLY,
                hasGamepad = false
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRequestSystemAudioCoupledHaptics(
                enabled = true,
                sceneMode = AudioVibrationService.SCENE_MUSIC,
                vibrationMode = AudioVibrationService.MODE_AUTO,
                hasGamepad = true
            )
        )
        assertEquals(
            false,
            AudioVibrationService.shouldRequestSystemAudioCoupledHaptics(
                enabled = true,
                sceneMode = AudioVibrationService.SCENE_MUSIC,
                vibrationMode = AudioVibrationService.MODE_AUTO,
                hasGamepad = false
            )
        )
    }
}
