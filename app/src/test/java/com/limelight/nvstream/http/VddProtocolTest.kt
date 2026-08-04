package com.limelight.nvstream.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class VddProtocolTest {
    @Test
    fun parsesDisplayCatalogVddCapabilityAndState() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """
            {
              "status_code": 200,
              "displays": [
                {
                  "friendly_name": "Main display",
                  "display_name": "\\\\.\\DISPLAY1",
                  "device_id": "device-1"
                }
              ],
              "vdd": {
                "capability_version": 1,
                "state": "ready"
              }
            }
            """.trimIndent()
        )

        assertEquals(1, catalog.displays.size)
        assertEquals("Main display", catalog.displays.single().name)
        assertEquals("device-1", catalog.displays.single().guid)
        assertEquals(1, catalog.vddCapabilityVersion)
        assertEquals(NvHTTP.VddState.READY, catalog.vddState)
        assertTrue(catalog.supportsVdd(1))
    }

    @Test
    fun missingVddMetadataKeepsLegacyVddCompatibility() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """{"status_code":200,"displays":[]}"""
        )

        assertNull(catalog.vddCapabilityVersion)
        assertEquals(NvHTTP.VddState.UNKNOWN, catalog.vddState)
        assertTrue(catalog.supportsVdd(null))
    }

    @Test
    fun missingCapabilityFieldKeepsLegacyVddCompatibility() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """{"status_code":200,"displays":[],"vdd":{"state":"driver_unreachable"}}"""
        )

        assertNull(catalog.vddCapabilityVersion)
        assertTrue(catalog.supportsVdd(null))
    }

    @Test
    fun explicitUnsupportedVddCapabilityIsRejected() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """{"status_code":200,"displays":[],"vdd":{"capability_version":0,"state":"unsupported_platform"}}"""
        )

        assertFalse(catalog.supportsVdd(null))
    }

    @Test
    fun nonReadyVddStateDoesNotPreventSelection() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """{"status_code":200,"displays":[],"vdd":{"capability_version":1,"state":"driver_unreachable"}}"""
        )

        assertEquals(NvHTTP.VddState.DRIVER_UNREACHABLE, catalog.vddState)
        assertTrue(catalog.supportsVdd(1))
    }

    @Test
    fun negativeXmlCapabilityKeepsLegacyVddCompatibility() {
        val capability = NvHTTP.parseVddCapabilityVersion("-1")

        assertNull(capability)
        assertTrue(
            NvHTTP.DisplayCatalog(emptyList(), null, NvHTTP.VddState.UNKNOWN)
                .supportsVdd(capability)
        )
    }

    @Test
    fun negativeJsonCapabilityKeepsLegacyVddCompatibility() {
        val catalog = NvHTTP.parseDisplayCatalog(
            """{"status_code":200,"displays":[],"vdd":{"capability_version":-1,"state":"driver_unreachable"}}"""
        )

        assertNull(catalog.vddCapabilityVersion)
        assertTrue(catalog.supportsVdd(null))
    }

    @Test(expected = IOException::class)
    fun nonSuccessDisplayCatalogResponseThrowsIOException() {
        NvHTTP.parseDisplayCatalog(
            """{"status_code":500,"status_message":"Display query failed"}"""
        )
    }

    @Test
    fun hostErrorCarriesMachineReadableSunshineErrorCode() {
        val original = HostHttpResponseException(503, "VDD unavailable")
        val error = original.withSunshineErrorCode("VDD_DRIVER_UNREACHABLE")

        assertEquals(503, error.getErrorCode())
        assertEquals("VDD_DRIVER_UNREACHABLE", error.getSunshineErrorCode())
        assertSame(original, error.cause)
    }

    @Test
    fun computerDetailsCopyPreservesVddCapabilityVersion() {
        val copy = ComputerDetails(
            ComputerDetails().apply {
                vddCapabilityVersion = 1
            }
        )

        assertEquals(1, copy.vddCapabilityVersion)
    }
}
