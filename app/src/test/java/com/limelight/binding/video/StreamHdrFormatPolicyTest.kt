package com.limelight.binding.video

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamHdrFormatPolicyTest {
    @Test
    fun disabledOrEightBitStreamIsSdr() {
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(hdrEnabled = false, configured = true, observed = true),
        )
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(isTenBitStream = false, configured = true, observed = true),
        )
    }

    @Test
    fun pqRequiresConfigurationAndObservedMetadataForHdr10Plus() {
        assertEquals(StreamHdrFormat.HDR10, resolve())
        assertEquals(StreamHdrFormat.HDR10, resolve(configured = true))
        assertEquals(StreamHdrFormat.HDR10, resolve(observed = true))
        assertEquals(
            StreamHdrFormat.HDR10_PLUS,
            resolve(configured = true, observed = true),
        )
    }

    @Test
    fun hlgTakesPrecedenceOverStaleHdr10PlusState() {
        assertEquals(
            StreamHdrFormat.HLG,
            resolve(
                isPqHdr = false,
                isHlg = true,
                configured = true,
                observed = true,
            ),
        )
    }

    @Test
    fun unknownOrSdrTransferModeFailsClosedToSdr() {
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(isPqHdr = false, isHlg = false),
        )
    }

    @Test
    fun observedHdr10PlusCanProveActivationWhenInitialHostCallbackWasMissed() {
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(hdrEnabled = false, hdrStateKnown = false, configured = true),
        )
        assertEquals(
            StreamHdrFormat.HDR10_PLUS,
            resolve(
                hdrEnabled = false,
                hdrStateKnown = false,
                configured = true,
                observed = true,
            ),
        )
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(
                hdrEnabled = false,
                hdrStateKnown = true,
                configured = true,
                observed = true,
            ),
        )
    }

    @Test
    fun compatibilityBooleanTracksTypedFormat() {
        val performanceInfo = PerformanceInfo()
        assertEquals(false, performanceInfo.isHdrActive)

        performanceInfo.hdrFormat = StreamHdrFormat.HLG
        assertEquals(true, performanceInfo.isHdrActive)

        performanceInfo.isHdrActive = false
        assertEquals(StreamHdrFormat.SDR, performanceInfo.hdrFormat)

        performanceInfo.isHdrActive = true
        assertEquals(StreamHdrFormat.HDR10, performanceInfo.hdrFormat)
    }

    @Test
    fun observationEpochResetsOnlyForRealHdrTransitions() {
        assertEquals(false, HdrObservationEpochPolicy.shouldReset(null, true))
        assertEquals(true, HdrObservationEpochPolicy.shouldReset(null, false))
        assertEquals(false, HdrObservationEpochPolicy.shouldReset(true, true))
        assertEquals(false, HdrObservationEpochPolicy.shouldReset(false, false))
        assertEquals(true, HdrObservationEpochPolicy.shouldReset(true, false))
        assertEquals(true, HdrObservationEpochPolicy.shouldReset(false, true))
    }

    @Test
    fun observationLifecycleMovesBetweenHdr10AndHdr10Plus() {
        assertEquals(StreamHdrFormat.SDR, resolve(hdrEnabled = false))
        assertEquals(StreamHdrFormat.HDR10, resolve(configured = true))
        assertEquals(
            StreamHdrFormat.HDR10_PLUS,
            resolve(configured = true, observed = true),
        )
        // A codec reconfiguration resets the current observation epoch.
        assertEquals(StreamHdrFormat.HDR10, resolve(configured = true, observed = false))
        // Disabling HDR wins even if the asynchronous observation is still stale.
        assertEquals(
            StreamHdrFormat.SDR,
            resolve(hdrEnabled = false, configured = true, observed = true),
        )
    }

    private fun resolve(
        hdrEnabled: Boolean = true,
        hdrStateKnown: Boolean = true,
        isTenBitStream: Boolean = true,
        isPqHdr: Boolean = true,
        isHlg: Boolean = false,
        configured: Boolean = false,
        observed: Boolean = false,
    ): StreamHdrFormat = StreamHdrFormatPolicy.resolve(
        hdrEnabled = hdrEnabled,
        hdrStateKnown = hdrStateKnown,
        isTenBitStream = isTenBitStream,
        isPqHdr = isPqHdr,
        isHlg = isHlg,
        hdr10PlusConfigured = configured,
        hdr10PlusMetadataObserved = observed,
    )
}
