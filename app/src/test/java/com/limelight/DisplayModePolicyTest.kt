package com.limelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModePolicyTest {
    @Test
    fun selectsLowerRefreshHdrModeWhenCurrentModeDoesNotSupportHdr() {
        val currentSdrMode = mode(id = 1, refreshRate = 120f)
        val hdrMode = mode(id = 2, refreshRate = 60f, hdrTypes = listOf(HDR10))

        val result = DisplayModePolicy.selectBestMode(
            currentMode = currentSdrMode,
            supportedModes = listOf(currentSdrMode, hdrMode),
            request = request(acceptableHdrTypes = listOf(HDR10)),
        )

        assertEquals(hdrMode, result.mode)
        assertTrue(result.hdrFilterApplied)
    }

    @Test
    fun keepsCurrentModeWhenItSupportsRequestedHdrType() {
        val currentHdrMode = mode(id = 1, refreshRate = 120f, hdrTypes = listOf(HDR10))
        val lowerRefreshHdrMode = mode(id = 2, refreshRate = 60f, hdrTypes = listOf(HDR10))

        val result = DisplayModePolicy.selectBestMode(
            currentMode = currentHdrMode,
            supportedModes = listOf(currentHdrMode, lowerRefreshHdrMode),
            request = request(acceptableHdrTypes = listOf(HDR10)),
        )

        assertEquals(currentHdrMode, result.mode)
        assertTrue(result.hdrFilterApplied)
    }

    @Test
    fun fallsBackToNormalSelectionWhenNoModeSupportsRequestedHdrType() {
        val currentSdrMode = mode(id = 1, refreshRate = 60f)
        val fasterSdrMode = mode(id = 2, refreshRate = 120f)

        val result = DisplayModePolicy.selectBestMode(
            currentMode = currentSdrMode,
            supportedModes = listOf(currentSdrMode, fasterSdrMode),
            request = request(acceptableHdrTypes = listOf(HDR10)),
        )

        assertEquals(fasterSdrMode, result.mode)
        assertFalse(result.hdrFilterApplied)
    }

    @Test
    fun doesNotRetainHdrCandidateThatViolatesResolutionConstraints() {
        val currentSdrMode = mode(id = 1, width = 3840, height = 2160, refreshRate = 120f)
        val undersizedHdrMode = mode(
            id = 2,
            width = 1920,
            height = 1080,
            refreshRate = 60f,
            hdrTypes = listOf(HDR10),
        )

        val result = DisplayModePolicy.selectBestMode(
            currentMode = currentSdrMode,
            supportedModes = listOf(currentSdrMode, undersizedHdrMode),
            request = request(acceptableHdrTypes = listOf(HDR10)),
        )

        assertEquals(currentSdrMode, result.mode)
        assertTrue(result.hdrFilterApplied)
    }

    @Test
    fun modeEqualityUsesHdrTypeValues() {
        assertEquals(
            mode(id = 1, refreshRate = 60f, hdrTypes = listOf(HDR10)),
            mode(id = 1, refreshRate = 60f, hdrTypes = listOf(HDR10)),
        )
    }

    private fun mode(
        id: Int,
        width: Int = 3840,
        height: Int = 2160,
        refreshRate: Float,
        hdrTypes: List<Int> = emptyList(),
    ) = DisplayModePolicy.Mode(id, width, height, refreshRate, hdrTypes)

    private fun request(
        acceptableHdrTypes: List<Int>,
    ) = DisplayModePolicy.Request(
        width = 3840,
        height = 2160,
        fps = 60,
        usesNativeDisplayMode = true,
        mayReduceRefreshRate = false,
        acceptableHdrTypes = acceptableHdrTypes,
    )

    private companion object {
        const val HDR10 = 2
    }
}