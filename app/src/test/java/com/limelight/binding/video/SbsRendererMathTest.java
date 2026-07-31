package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SbsRendererMathTest {
    @Test
    public void zeroAnglesProduceIdentityMapping() {
        float[] inverse = new float[9];
        SbsRenderer.buildInverseHomography(0.0f, 0.0f, 16.0f / 9.0f, inverse);

        float scale = inverse[8];
        assertEquals(scale, inverse[0], 0.00001f);
        assertEquals(scale, inverse[4], 0.00001f);
        assertEquals(0.0f, inverse[1], 0.00001f);
        assertEquals(0.0f, inverse[2], 0.00001f);
        assertEquals(0.0f, inverse[3], 0.00001f);
        assertEquals(0.0f, inverse[5], 0.00001f);
        assertEquals(0.0f, inverse[6], 0.00001f);
        assertEquals(0.0f, inverse[7], 0.00001f);
    }

    @Test
    public void chromaticCorrectionPercentageMapsSymmetrically() {
        assertEquals(0.0f, SbsRenderer.getChromaticCorrectionCoefficient(true, 0), 0.0f);
        assertEquals(0.02f,
                SbsRenderer.getChromaticCorrectionCoefficient(true, 100), 0.00001f);
        assertEquals(-0.02f,
                SbsRenderer.getChromaticCorrectionCoefficient(true, -100), 0.00001f);
        assertEquals(0.0f,
                SbsRenderer.getChromaticCorrectionCoefficient(false, 100), 0.0f);
    }

    @Test
    public void lensCorrectionCanBeDisabledPerAxis() {
        assertEquals(0.0f, SbsRenderer.getLensCorrectionCoefficient(true, 0), 0.0f);
        assertEquals(0.4f,
                SbsRenderer.getLensCorrectionCoefficient(true, 100), 0.00001f);
        assertEquals(0.0f,
                SbsRenderer.getLensCorrectionCoefficient(false, 100), 0.0f);
    }

    @Test
    public void headTrackingSensitivityMapsAndClampsImageShift() {
        assertEquals(0.0f, SbsRenderer.getHeadTrackingOffset(20.0f, 0, 1.0f), 0.0f);
        assertEquals(-0.4f,
                SbsRenderer.getHeadTrackingOffset(20.0f, 50, 1.0f), 0.00001f);
        assertEquals(-0.5f,
                SbsRenderer.getHeadTrackingOffset(20.0f, 100, 0.5f), 0.00001f);
        assertEquals(0.5f,
                SbsRenderer.getHeadTrackingOffset(-20.0f, 100, 0.5f), 0.00001f);
    }

    @Test
    public void verticalHeadTrackingMovesImageWithHeadPitch() {
        assertEquals(0.4f,
                SbsRenderer.getVerticalHeadTrackingOffset(20.0f, 50, 1.0f), 0.00001f);
        assertEquals(-0.4f,
                SbsRenderer.getVerticalHeadTrackingOffset(-20.0f, 50, 1.0f), 0.00001f);
    }

    @Test
    public void fullScaleCoversTheWholeEyeViewportWithoutStretching() {
        float scale = SbsRenderer.getViewportCoverScale(960, 1080, 3072, 1440, 100);

        assertEquals(2304.0f, 3072 * scale, 0.00001f);
        assertEquals(1080.0f, 1440 * scale, 0.00001f);
    }

    @Test
    public void edgeReachControlsHowFarContentCanMoveIntoTheLensCenter() {
        assertEquals(1.4f,
                SbsRenderer.getHeadTrackingMaximumMagnitude(2.4f, 0), 0.00001f);
        assertEquals(1.9f,
                SbsRenderer.getHeadTrackingMaximumMagnitude(2.4f, 50), 0.00001f);
        assertEquals(2.4f,
                SbsRenderer.getHeadTrackingMaximumMagnitude(2.4f, 100), 0.00001f);
        assertEquals(0.25f,
                SbsRenderer.getHeadTrackingMaximumMagnitude(0.5f, 50), 0.00001f);
    }
}
