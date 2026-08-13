package com.limelight.nvstream

import com.limelight.nvstream.jni.MoonBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrModePolicyTest {
    @Test
    fun hdr10PlusIsPqAndUsesHdr10OnTheWire() {
        assertTrue(HdrModePolicy.isHdr10PlusMode(MoonBridge.HDR_MODE_HDR10_PLUS))
        assertTrue(HdrModePolicy.isPqMode(MoonBridge.HDR_MODE_HDR10_PLUS))
        assertEquals(
            MoonBridge.HDR_MODE_HDR10,
            HdrModePolicy.toProtocolMode(MoonBridge.HDR_MODE_HDR10_PLUS),
        )
    }

    @Test
    fun staticHdr10RemainsPqWithoutChangingItsWireValue() {
        assertFalse(HdrModePolicy.isHdr10PlusMode(MoonBridge.HDR_MODE_HDR10))
        assertTrue(HdrModePolicy.isPqMode(MoonBridge.HDR_MODE_HDR10))
        assertEquals(
            MoonBridge.HDR_MODE_HDR10,
            HdrModePolicy.toProtocolMode(MoonBridge.HDR_MODE_HDR10),
        )
    }

    @Test
    fun hlgRemainsDistinctFromPq() {
        assertFalse(HdrModePolicy.isPqMode(MoonBridge.HDR_MODE_HLG))
        assertEquals(
            MoonBridge.HDR_MODE_HLG,
            HdrModePolicy.toProtocolMode(MoonBridge.HDR_MODE_HLG),
        )
    }

    @Test
    fun unknownModeCannotEscapeIntoTheNativeProtocol() {
        assertFalse(HdrModePolicy.isPqMode(99))
        assertEquals(MoonBridge.HDR_MODE_SDR, HdrModePolicy.toProtocolMode(99))
    }

    @Test
    fun hdr10PlusRequestRequiresExplicitModeDisplaySupportAndDirectRendering() {
        assertTrue(
            HdrModePolicy.shouldRequestHdr10Plus(
                hdrEnabled = true,
                hdrMode = MoonBridge.HDR_MODE_HDR10_PLUS,
                displaySupportsHdr10Plus = true,
                framegenRequested = false,
            ),
        )
        assertFalse(
            HdrModePolicy.shouldRequestHdr10Plus(
                hdrEnabled = true,
                hdrMode = MoonBridge.HDR_MODE_HDR10,
                displaySupportsHdr10Plus = true,
                framegenRequested = false,
            ),
        )
        assertFalse(
            HdrModePolicy.shouldRequestHdr10Plus(
                hdrEnabled = true,
                hdrMode = MoonBridge.HDR_MODE_HDR10_PLUS,
                displaySupportsHdr10Plus = true,
                framegenRequested = true,
            ),
        )
        assertFalse(
            HdrModePolicy.shouldRequestHdr10Plus(
                hdrEnabled = true,
                hdrMode = MoonBridge.HDR_MODE_HDR10_PLUS,
                displaySupportsHdr10Plus = false,
                framegenRequested = false,
            ),
        )
    }
}
