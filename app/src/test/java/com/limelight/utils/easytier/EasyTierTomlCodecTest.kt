package com.limelight.utils.easytier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyTierTomlCodecTest {
    @Test
    fun buildPreservesEmptyNetworkSecret() {
        val toml = EasyTierTomlCodec.build(EasyTierConfigUiState(
                networkName = "easytier",
                networkSecret = ""
        ))

        assertTrue(toml.contains("network_secret = \"\""))
    }

    @Test
    fun buildUsesDefaultIpv4WhenConfigIpv4IsBlank() {
        val toml = EasyTierTomlCodec.build(EasyTierConfigUiState(ipv4 = ""))

        assertTrue(toml.contains("ipv4 = \"10.0.0.1/24\""))
    }

    @Test
    fun stringsRoundTripEscapedCharacters() {
        val original = EasyTierConfigUiState(
                networkName = "easy\"tier\\name",
                networkSecret = "line1\nline2\\secret\"",
                ipv4 = "10.0.0.6",
                peers = "tcp://example.com/path\\with\"quote"
        )

        val parsed = EasyTierTomlCodec.parseConfig(EasyTierTomlCodec.build(original))

        assertEquals(original.networkName, parsed.networkName)
        assertEquals(original.networkSecret, parsed.networkSecret)
        assertEquals(original.peers, parsed.peers)
    }

    @Test
    fun listenersArrayRoundTripsEscapedItems() {
        val original = EasyTierConfigUiState(
                listeners = "udp://0.0.0.0:11010\ntcp://host/with\\slash\nwg://host/with\"quote"
        )

        val parsed = EasyTierTomlCodec.parseConfig(EasyTierTomlCodec.build(original))

        assertEquals(original.listeners, parsed.listeners)
    }
}
