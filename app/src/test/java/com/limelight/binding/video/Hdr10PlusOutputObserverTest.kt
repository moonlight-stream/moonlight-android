package com.limelight.binding.video

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Hdr10PlusOutputObserverTest {
    @Test
    fun probeScheduleSamplesInitialWindowAndThenIntervals() {
        assertFalse(Hdr10PlusProbeSchedule.shouldProbe(0, false))
        assertTrue(Hdr10PlusProbeSchedule.shouldProbe(1, false))
        assertTrue(Hdr10PlusProbeSchedule.shouldProbe(120, false))
        assertFalse(Hdr10PlusProbeSchedule.shouldProbe(121, false))
        assertTrue(Hdr10PlusProbeSchedule.shouldProbe(180, false))
    }

    @Test
    fun probeScheduleKeepsSamplingObservedMetadataAtInterval() {
        assertFalse(Hdr10PlusProbeSchedule.shouldProbe(1, true))
        assertFalse(Hdr10PlusProbeSchedule.shouldProbe(59, true))
        assertTrue(Hdr10PlusProbeSchedule.shouldProbe(60, true))
        assertFalse(Hdr10PlusProbeSchedule.shouldProbe(61, true))
    }

    @Test
    fun codecConfigurationPreservesSelectedProfileUntilStart() {
        val observer = Hdr10PlusOutputObserver()

        observer.beginCodecConfiguration(true)
        assertTrue(observer.snapshot().configured)
        assertFalse(observer.snapshot().queryEnabled)

        observer.restartCodecConfiguration()
        assertTrue(observer.snapshot().configured)
        assertFalse(observer.snapshot().queryEnabled)

        observer.onCodecStarted()
        assertTrue(observer.snapshot().queryEnabled)
    }

    @Test
    fun explicitHostDisablePreventsQueriesAcrossCodecRestart() {
        val observer = Hdr10PlusOutputObserver()

        observer.beginCodecConfiguration(true)
        observer.onHostHdrMode(false)
        observer.restartCodecConfiguration()
        observer.onCodecStarted()

        val snapshot = observer.snapshot()
        assertEquals(HdrStreamState.DISABLED, snapshot.streamState)
        assertTrue(snapshot.configured)
        assertFalse(snapshot.queryEnabled)
    }

    @Test
    fun realHdrToggleStartsNewEnabledEpoch() {
        val observer = Hdr10PlusOutputObserver()
        observer.beginCodecConfiguration(true)
        observer.onCodecStarted()
        observer.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 1)
        assertTrue(observer.snapshot().metadataObserved)

        observer.onHostHdrMode(false)
        assertFalse(observer.snapshot().queryEnabled)
        assertFalse(observer.snapshot().metadataObserved)

        observer.recordMetadata(ByteBuffer.wrap(byteArrayOf(4, 5, 6)), 2)
        observer.onHostHdrMode(true)
        val snapshot = observer.snapshot()
        assertEquals(HdrStreamState.ENABLED, snapshot.streamState)
        assertTrue(snapshot.configured)
        assertTrue(snapshot.queryEnabled)
        assertEquals(0, snapshot.metadata.metadataFrames)
    }

    @Test
    fun profileFallbackDisablesMetadataQueries() {
        val observer = Hdr10PlusOutputObserver()
        observer.beginCodecConfiguration(true)
        observer.onCodecStarted()
        observer.recordMetadata(ByteBuffer.wrap(byteArrayOf(1)), 1)
        assertTrue(observer.snapshot().queryEnabled)
        assertTrue(observer.snapshot().metadataObserved)

        observer.beginCodecConfiguration(false)
        val snapshot = observer.snapshot()
        assertFalse(snapshot.configured)
        assertFalse(snapshot.queryEnabled)
        assertFalse(snapshot.metadataObserved)
    }

    @Test
    fun delayedFirstEnablePreservesObservedMetadata() {
        val observer = Hdr10PlusOutputObserver()
        observer.beginCodecConfiguration(true)
        observer.onCodecStarted()
        observer.recordMetadata(ByteBuffer.wrap(byteArrayOf(1, 2)), 1)

        observer.onHostHdrMode(true)

        val snapshot = observer.snapshot()
        assertEquals(HdrStreamState.ENABLED, snapshot.streamState)
        assertTrue(snapshot.metadataObserved)
        assertEquals(1, snapshot.metadata.metadataFrames)
    }
}
