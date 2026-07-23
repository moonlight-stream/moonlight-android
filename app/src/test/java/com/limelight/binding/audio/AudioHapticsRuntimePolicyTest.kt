package com.limelight.binding.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioHapticsRuntimePolicyTest {
    private val applied = AudioHapticsSettings(
        enabled = true,
        strength = 80,
        mode = "device",
        scene = 1
    )

    @Test
    fun portableRendererAcceptsRuntimeChanges() {
        assertTrue(
            AudioHapticsRuntimePolicy.canApplyImmediately(
                systemAudioCoupledActive = false,
                applied = applied,
                desired = applied.copy(strength = 120)
            )
        )
    }

    @Test
    fun activeSystemGeneratorDefersChangedSettings() {
        assertFalse(
            AudioHapticsRuntimePolicy.canApplyImmediately(
                systemAudioCoupledActive = true,
                applied = applied,
                desired = applied.copy(mode = "gamepad")
            )
        )
    }

    @Test
    fun unchangedSettingsNeverRequireRestart() {
        assertTrue(
            AudioHapticsRuntimePolicy.canApplyImmediately(
                systemAudioCoupledActive = true,
                applied = applied,
                desired = applied
            )
        )
    }
}
