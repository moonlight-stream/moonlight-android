package com.limelight.binding.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualcommLowLatencyPolicyTest {
    @Test
    fun hdr10PlusWithOutputFenceDisablesPictureOrderAcrossAllAggressiveTries() {
        for (tryNumber in 0..3) {
            assertFalse(
                QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                    tryNumber,
                    hdr10PlusModeSelected = true,
                ),
            )
        }
    }

    @Test
    fun staticHdr10AndHlgKeepPictureOrderOnAggressiveTries() {
        for (tryNumber in 0..3) {
            assertTrue(
                QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                    tryNumber,
                    hdr10PlusModeSelected = false,
                ),
            )
        }
    }

    @Test
    fun hdr10PlusKeepsPictureOrderWhenOutputFenceIsDisabled() {
        for (tryNumber in 0..3) {
            assertTrue(
                QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                    tryNumber,
                    hdr10PlusModeSelected = true,
                    outputFenceEnabled = false,
                ),
            )
        }
    }

    @Test
    fun pictureOrderIsDisabledAtFallbackBoundaryAndForInvalidTry() {
        assertFalse(
            QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                tryNumber = 4,
                hdr10PlusModeSelected = false,
            ),
        )
        assertFalse(
            QualcommLowLatencyPolicy.shouldEnablePictureOrder(
                tryNumber = -1,
                hdr10PlusModeSelected = false,
            ),
        )
    }
}
