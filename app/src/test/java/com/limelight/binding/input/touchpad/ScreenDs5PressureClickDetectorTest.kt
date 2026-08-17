package com.limelight.binding.input.touchpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenDs5PressureClickDetectorTest {
    @Test
    fun constantPressureDoesNotClick() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(1f))
        assertNull(detector.update(1f))
        assertNull(detector.end())
    }

    @Test
    fun pressureRiseClicksAndHysteresisReleases() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(0.18f))
        assertNull(detector.update(0.34f))
        assertEquals(true, detector.update(0.36f))
        assertNull(detector.update(0.30f))
        assertEquals(false, detector.update(0.24f))
    }

    @Test
    fun deepPressWorksWithoutUsefulPressureAxis() {
        val detector = ScreenDs5PressureClickDetector()

        assertNull(detector.begin(1f))
        assertEquals(true, detector.update(1f, deepPress = true))
        assertEquals(false, detector.end())
    }

    @Test
    fun endingGestureAlwaysReleasesPressedButton() {
        val detector = ScreenDs5PressureClickDetector()

        detector.begin(0.2f)
        assertEquals(true, detector.update(0.5f))
        assertEquals(false, detector.end())
        assertNull(detector.end())
    }
}
