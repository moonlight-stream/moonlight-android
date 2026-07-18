package com.limelight.binding.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimestampTrackerTest {
    @Test
    fun metadataCanBeConsumedIndependently() {
        val tracker = FrameTimestampTracker(4)

        tracker.recordFrame(100, 20, 10, hasHostPresentationTime = true)

        assertEquals(10, tracker.takeHostPresentationTime(100, -1))
        assertEquals(20, tracker.takeEnqueueTime(100, -1))
        assertEquals(-1, tracker.takeHostPresentationTime(100, -1))
        assertEquals(-1, tracker.takeEnqueueTime(100, -1))
    }

    @Test
    fun supportsOutOfOrderCodecOutput() {
        val tracker = FrameTimestampTracker(4)
        tracker.recordFrame(100, 1, -1, hasHostPresentationTime = false)
        tracker.recordFrame(200, 2, -1, hasHostPresentationTime = false)
        tracker.recordFrame(300, 3, -1, hasHostPresentationTime = false)

        assertEquals(2, tracker.takeEnqueueTime(200, -1))
        assertEquals(1, tracker.takeEnqueueTime(100, -1))
        assertEquals(3, tracker.takeEnqueueTime(300, -1))
    }

    @Test
    fun overwritesOldestSlotWhenCapacityIsExhausted() {
        val tracker = FrameTimestampTracker(2)
        tracker.recordFrame(100, 1, -1, hasHostPresentationTime = false)
        tracker.recordFrame(200, 2, -1, hasHostPresentationTime = false)
        tracker.recordFrame(300, 3, -1, hasHostPresentationTime = false)

        assertEquals(-1, tracker.takeEnqueueTime(100, -1))
        assertEquals(2, tracker.takeEnqueueTime(200, -1))
        assertEquals(3, tracker.takeEnqueueTime(300, -1))
    }

    @Test
    fun clearDropsAllTrackedMetadata() {
        val tracker = FrameTimestampTracker(2)
        tracker.recordFrame(100, 1, 2, hasHostPresentationTime = true)

        tracker.clear()

        assertEquals(-1, tracker.takeEnqueueTime(100, -1))
        assertEquals(-1, tracker.takeHostPresentationTime(100, -1))
    }
}
