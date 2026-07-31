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
}
