package com.limelight

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusToolClassifierTest {
    @Test
    fun stylusToolIsDetected() {
        assertTrue(containsStylus(MotionEvent.TOOL_TYPE_STYLUS))
    }

    @Test
    fun eraserToolIsDetected() {
        assertTrue(containsStylus(MotionEvent.TOOL_TYPE_ERASER))
    }

    @Test
    fun stylusIsDetectedWhenItIsNotTheFirstPointer() {
        assertTrue(containsStylus(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS))
    }

    @Test
    fun fingerOnlyEventIsNotStylusInput() {
        assertFalse(containsStylus(MotionEvent.TOOL_TYPE_FINGER))
    }

    @Test
    fun mouseOnlyEventIsNotStylusInput() {
        assertFalse(containsStylus(MotionEvent.TOOL_TYPE_MOUSE))
    }

    @Test
    fun historicalMotionSamplesAreVisitedBeforeCurrentSamples() {
        val visited = mutableListOf<Pair<Int, Int>>()

        assertTrue(visitMotionEventSamples(historySize = 2, pointerCount = 2) { pointerIndex, historyPosition ->
            visited += historyPosition to pointerIndex
            true
        })

        assertEquals(
            listOf(
                0 to 0,
                0 to 1,
                1 to 0,
                1 to 1,
                CURRENT_MOTION_SAMPLE to 0,
                CURRENT_MOTION_SAMPLE to 1
            ),
            visited
        )
    }

    private fun containsStylus(vararg toolTypes: Int): Boolean =
        containsStylusTool(toolTypes.size) { toolTypes[it] }
}
