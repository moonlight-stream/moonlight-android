package com.limelight.binding.input.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerHapticsMixerTest {
    private val controller0 = 0.toShort()
    private val controller1 = 1.toShort()

    @Test
    fun hostAndAudioDoNotClearEachOther() {
        val mixer = ControllerHapticsMixer()
        mixer.submitAudioContinuous(controller0, state(low = 0.6f, high = 0.8f), 0)
        val withHost = mixer.submit(
            controller0,
            RumbleSource.HOST,
            state(low = 0.7f, high = 0.1f),
            1
        )

        assertState(withHost, low = 0.7f, high = 0.2f)

        val audioCleared = mixer.clearSource(controller0, RumbleSource.AUDIO, 2)
        assertState(audioCleared, low = 0.7f, high = 0.1f)

        mixer.submitAudioContinuous(controller0, state(low = 0.6f, high = 0.8f), 3)
        val hostCleared = mixer.clearSource(controller0, RumbleSource.HOST, 4)
        assertState(hostCleared, low = 0.6f, high = 0.8f)
    }

    @Test
    fun audioContinuousAndTransientExpireIndependently() {
        val mixer = ControllerHapticsMixer()
        mixer.submitAudioContinuous(
            controller0,
            state(low = 0.4f, high = 0.2f),
            nowMs = 100,
            expiresAtMs = 300
        )
        val combined = mixer.submitAudioTransient(
            controller0,
            state(low = 0.1f, high = 0.9f),
            nowMs = 100,
            expiresAtMs = 150
        )

        assertState(combined, low = 0.4f, high = 0.9f)
        assertEquals(150L, mixer.nextExpiryAtMs())

        val transientExpired = mixer.pruneExpired(150)
        assertEquals(1, transientExpired.size)
        assertState(transientExpired.single(), low = 0.4f, high = 0.2f)
        assertEquals(300L, mixer.nextExpiryAtMs())

        val continuousExpired = mixer.pruneExpired(300)
        assertEquals(1, continuousExpired.size)
        assertTrue(continuousExpired.single().isZero)
        assertNull(mixer.nextExpiryAtMs())
    }

    @Test
    fun hostExpiryRestoresUnattenuatedAudio() {
        val mixer = ControllerHapticsMixer()
        mixer.submitAudioContinuous(controller0, state(low = 0.6f), 0)
        val attenuated = mixer.submit(
            controller0,
            RumbleSource.HOST,
            state(low = 0.1f),
            nowMs = 0,
            expiresAtMs = 50
        )

        assertState(attenuated, low = 0.15f)

        val expired = mixer.pruneExpired(50).single()
        assertState(expired, low = 0.6f)
    }

    @Test
    fun newerTransientSurvivesOlderExpiryDeadline() {
        val mixer = ControllerHapticsMixer()
        mixer.submitAudioTransient(
            controller0,
            state(high = 0.4f),
            nowMs = 0,
            expiresAtMs = 50
        )
        mixer.submitAudioTransient(
            controller0,
            state(high = 0.8f),
            nowMs = 25,
            expiresAtMs = 100
        )

        assertTrue(mixer.pruneExpired(50).isEmpty())
        assertState(mixer.mixedState(controller0, 50), high = 0.8f)
        assertTrue(mixer.pruneExpired(100).single().isZero)
    }

    @Test
    fun controllersAreMixedIndependently() {
        val mixer = ControllerHapticsMixer()
        mixer.submit(controller0, RumbleSource.HOST, state(low = 0.8f), 0)
        mixer.submitAudioContinuous(controller1, state(high = 0.7f), 0)

        assertState(mixer.mixedState(controller0, 1), low = 0.8f)
        assertState(mixer.mixedState(controller1, 1), high = 0.7f)

        assertState(mixer.mixedState(controller1, 2), high = 0.7f)
    }

    @Test
    fun clampsBaseMotorsAndInvalidValues() {
        val clamped = ControllerRumbleState(
            lowFrequency = -1f,
            highFrequency = 2f
        )
        val invalid = ControllerRumbleState(
            lowFrequency = Float.NaN,
            highFrequency = Float.POSITIVE_INFINITY
        )

        assertEquals(0f, clamped.lowFrequency, 0f)
        assertEquals(1f, clamped.highFrequency, 0f)
        assertEquals(0f, invalid.lowFrequency, 0f)
        assertEquals(0f, invalid.highFrequency, 0f)

        val mixed = ControllerHapticsMixer().submit(
            controller0,
            RumbleSource.TEST,
            ControllerRumbleState(0.2f, 0.3f),
            0
        )
        assertState(mixed, low = 0.2f, high = 0.3f)
    }

    @Test
    fun expiredSubmissionBehavesAsClear() {
        val mixer = ControllerHapticsMixer()
        mixer.submit(controller0, RumbleSource.HOST, state(low = 0.9f), 10)

        val cleared = mixer.submit(
            controller0,
            RumbleSource.HOST,
            state(low = 0.5f),
            nowMs = 20,
            expiresAtMs = 20
        )

        assertTrue(cleared.isZero)
    }

    @Test
    fun clearAllReturnsStopForEveryTrackedController() {
        val mixer = ControllerHapticsMixer()
        mixer.submit(controller0, RumbleSource.HOST, state(low = 0.4f), 0)
        mixer.submit(controller1, RumbleSource.TEST, state(high = 0.5f), 0)

        val stopped = mixer.clearAll()

        assertEquals(setOf(controller0, controller1), stopped.map { it.controllerNumber }.toSet())
        assertTrue(stopped.all { it.isZero })
        assertTrue(mixer.clearAll().isEmpty())
    }

    private fun state(
        low: Float = 0f,
        high: Float = 0f
    ) = ControllerRumbleState(low, high)

    private fun assertState(
        mixed: MixedRumbleState,
        low: Float = 0f,
        high: Float = 0f
    ) {
        assertEquals(low, mixed.output.lowFrequency, 0.0001f)
        assertEquals(high, mixed.output.highFrequency, 0.0001f)
    }
}
