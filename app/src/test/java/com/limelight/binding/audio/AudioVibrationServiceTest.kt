package com.limelight.binding.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioVibrationServiceTest {
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
    }
}
