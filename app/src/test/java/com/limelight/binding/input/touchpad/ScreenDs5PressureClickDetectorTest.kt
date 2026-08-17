package com.limelight.binding.input.touchpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenDs5PressureClickDetectorTest {
    @Test
    fun constantPressureDoesNotClick() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(1f, 0.08f))
        assertNull(detector.update(1f, 0.08f))
        assertNull(detector.end())
    }

    @Test
    fun pressureRiseClicksAndHysteresisReleases() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(0.18f, 0.08f))
        assertNull(detector.update(0.34f, 0.08f))
        assertEquals(true, detector.update(0.36f, 0.08f))
        assertNull(detector.update(0.30f, 0.08f))
        assertEquals(false, detector.update(0.24f, 0.08f))
    }

    @Test
    fun contactSizeRiseClicksWhenPressureIsFixed() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(1f, 13f / 255f))
        assertNull(detector.update(1f, 16f / 255f))
        assertEquals(true, detector.update(1f, 18f / 255f))
        assertNull(detector.update(1f, 16f / 255f))
        assertEquals(false, detector.update(1f, 14f / 255f))
    }

    @Test
    fun deepPressWorksWithoutUsefulPressureAxis() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(1f, 0.08f))
        assertEquals(true, detector.update(1f, 0.08f, deepPress = true))
        assertEquals(false, detector.end())
    }

    @Test
    fun endingGestureAlwaysReleasesPressedButton() {
        val detector = ScreenDs5PressureClickDetector()

        detector.begin(0.2f, 0.08f)
        assertEquals(true, detector.update(0.5f, 0.08f))
        assertEquals(false, detector.end())
        assertNull(detector.end())
    }
}
