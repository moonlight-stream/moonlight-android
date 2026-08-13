package com.limelight.binding.video

import org.junit.Assert.assertEquals
import org.junit.Test

class HdrDecoderProfilePolicyTest {
    @Test
    fun hdr10PlusFallsBackToHdr10ThenAutomatic() {
        assertEquals(
            listOf<Int?>(8192, 4096, null),
            HdrDecoderProfilePolicy.buildCandidates(
                isTenBit = true,
                isPqHdr = true,
                hdr10PlusEligible = true,
                hdr10Advertised = true,
                hdr10PlusProfile = 8192,
                hdr10Profile = 4096,
            ),
        )
    }

    @Test
    fun hdr10OnlyFallsBackToAutomatic() {
        assertEquals(
            listOf<Int?>(4096, null),
            HdrDecoderProfilePolicy.buildCandidates(
                isTenBit = true,
                isPqHdr = true,
                hdr10PlusEligible = false,
                hdr10Advertised = true,
                hdr10PlusProfile = 8192,
                hdr10Profile = 4096,
            ),
        )
    }

    @Test
    fun hlgAndEightBitKeepLegacyAutomaticProfile() {
        assertEquals(
            listOf<Int?>(null),
            HdrDecoderProfilePolicy.buildCandidates(
                isTenBit = true,
                isPqHdr = false,
                hdr10PlusEligible = true,
                hdr10Advertised = true,
                hdr10PlusProfile = 8192,
                hdr10Profile = 4096,
            ),
        )
        assertEquals(
            listOf<Int?>(null),
            HdrDecoderProfilePolicy.buildCandidates(
                isTenBit = false,
                isPqHdr = true,
                hdr10PlusEligible = true,
                hdr10Advertised = true,
                hdr10PlusProfile = 8192,
                hdr10Profile = 4096,
            ),
        )
    }
}
