package com.limelight.binding.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class Hdr10PlusMetadataTrackerTest {
    @Test
    fun absentMetadataDoesNotActivateTracker() {
        val tracker = Hdr10PlusMetadataTracker()

        assertEquals(
            Hdr10PlusMetadataObservation.ABSENT,
            tracker.recordMetadata(null, 100),
        )
        assertEquals(1, tracker.outputFramesQueried)
        assertEquals(0, tracker.metadataFrames)
        assertEquals(-1, tracker.lastPresentationTimeUs)
    }

    @Test
    fun identicalMetadataIsNotCountedAsAChange() {
        val tracker = Hdr10PlusMetadataTracker()
        val payload = byteArrayOf(0xB5.toByte(), 0x00, 0x3C, 0x00, 0x01)

        assertEquals(
            Hdr10PlusMetadataObservation.FIRST,
            tracker.recordMetadata(ByteBuffer.wrap(payload), 100),
        )
        assertEquals(
            Hdr10PlusMetadataObservation.UNCHANGED,
            tracker.recordMetadata(ByteBuffer.wrap(payload), 200),
        )
        assertEquals(2, tracker.metadataFrames)
        assertEquals(0, tracker.metadataChanges)
        assertEquals(200, tracker.lastPresentationTimeUs)
    }

    @Test
    fun changedPayloadOrSizeIsCounted() {
        val tracker = Hdr10PlusMetadataTracker()

        tracker.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 100)
        assertEquals(
            Hdr10PlusMetadataObservation.CHANGED,
            tracker.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2, 4)), 200),
        )
        assertEquals(
            Hdr10PlusMetadataObservation.CHANGED,
            tracker.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2, 4, 0)), 300),
        )
        assertEquals(2, tracker.metadataChanges)
    }

    @Test
    fun respectsPositionAndLimitWithoutMutatingBuffer() {
        val tracker = Hdr10PlusMetadataTracker()
        val buffer = ByteBuffer.wrap(byteArrayOf(9, 1, 2, 3, 8)).asReadOnlyBuffer()
        buffer.position(1)
        buffer.limit(4)

        tracker.recordMetadata(buffer, 100)

        assertEquals(1, buffer.position())
        assertEquals(4, buffer.limit())
        assertEquals(3, tracker.lastMetadataSize)
    }

    @Test
    fun supportsDirectBuffers() {
        val tracker = Hdr10PlusMetadataTracker()
        val buffer = ByteBuffer.allocateDirect(3).put(byteArrayOf(1, 2, 3))
        buffer.flip()

        assertEquals(Hdr10PlusMetadataObservation.FIRST, tracker.recordMetadata(buffer, 100))
        assertEquals(3, tracker.lastMetadataSize)
    }

    @Test
    fun queryFailureIsLoggedOnlyOnceAndResetClearsState() {
        val tracker = Hdr10PlusMetadataTracker()

        assertTrue(tracker.recordQueryFailure())
        assertFalse(tracker.recordQueryFailure())
        assertEquals(2, tracker.outputFramesQueried)
        assertEquals(2, tracker.queryFailures)

        tracker.reset()

        assertEquals(0, tracker.outputFramesQueried)
        assertEquals(0, tracker.queryFailures)
        assertEquals(-1, tracker.lastPresentationTimeUs)
    }

    @Test
    fun snapshotContainsAConsistentCopyOfCurrentStatistics() {
        val tracker = Hdr10PlusMetadataTracker()
        tracker.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 1234)
        tracker.recordQueryFailure()

        val snapshot = tracker.snapshot()

        assertEquals(2, snapshot.outputFramesQueried)
        assertEquals(1, snapshot.metadataFrames)
        assertEquals(3, snapshot.lastMetadataSize)
        assertEquals(1234, snapshot.lastPresentationTimeUs)
        assertEquals(1, snapshot.queryFailures)
    }
}
