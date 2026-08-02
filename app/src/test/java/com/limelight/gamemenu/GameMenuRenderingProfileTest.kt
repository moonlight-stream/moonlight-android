package com.limelight.gamemenu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMenuRenderingProfileTest {
    @Test
    fun android6UsesOpaqueOverlayProfile() {
        assertTrue(
            GameMenuRenderingProfile.isLowEnd(
                sdkInt = 23,
                isLowRamDevice = false,
                memoryClassMb = 256,
                glRenderer = "Mali-G76"
            )
        )
    }

    @Test
    fun lowRamDeviceUsesOpaqueOverlayProfile() {
        assertTrue(
            GameMenuRenderingProfile.isLowEnd(
                sdkInt = 35,
                isLowRamDevice = true,
                memoryClassMb = 256,
                glRenderer = "Adreno (TM) 740"
            )
        )
    }

    @Test
    fun knownLegacyGpuUsesOpaqueOverlayProfile() {
        assertTrue(
            GameMenuRenderingProfile.isLowEnd(
                sdkInt = 34,
                isLowRamDevice = false,
                memoryClassMb = 256,
                glRenderer = "Mali-T720 MP2"
            )
        )
    }

    @Test
    fun modernCapableDeviceKeepsDecorativeProfile() {
        assertFalse(
            GameMenuRenderingProfile.isLowEnd(
                sdkInt = 35,
                isLowRamDevice = false,
                memoryClassMb = 512,
                glRenderer = "Mali-G715"
            )
        )
    }
}
