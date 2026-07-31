package com.limelight.binding.video;

import android.view.Surface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SbsHeadTrackerMathTest {
    private static final float[] IDENTITY = {
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f,
    };

    @Test
    public void centeredPoseProducesZeroAngles() {
        float[] output = new float[2];

        SbsHeadTracker.computeOrientationDegrees(
                IDENTITY, IDENTITY, Surface.ROTATION_90, output);

        assertEquals(0.0f, output[0], 0.00001f);
        assertEquals(0.0f, output[1], 0.00001f);
    }

    @Test
    public void landscapeRotationMapsPhysicalYawAndPitchToScreenAxes() {
        float angle = (float) Math.toRadians(20.0);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float[] yawAroundScreenVertical = {
                1.0f, 0.0f, 0.0f,
                0.0f, cosine, sine,
                0.0f, -sine, cosine,
        };
        float[] pitchAroundScreenHorizontal = {
                cosine, 0.0f, sine,
                0.0f, 1.0f, 0.0f,
                -sine, 0.0f, cosine,
        };
        float[] output = new float[2];

        SbsHeadTracker.computeOrientationDegrees(
                IDENTITY, yawAroundScreenVertical, Surface.ROTATION_90, output);
        assertEquals(20.0f, output[0], 0.0001f);
        assertEquals(0.0f, output[1], 0.0001f);

        SbsHeadTracker.computeOrientationDegrees(
                IDENTITY, pitchAroundScreenHorizontal, Surface.ROTATION_90, output);
        assertEquals(0.0f, output[0], 0.0001f);
        assertEquals(20.0f, output[1], 0.0001f);
    }
}
