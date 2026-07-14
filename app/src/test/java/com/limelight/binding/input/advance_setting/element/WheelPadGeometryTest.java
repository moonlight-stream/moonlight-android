package com.limelight.binding.input.advance_setting.element;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WheelPadGeometryTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void edgeAlignmentUsesHalfSegmentOffset() {
        assertEquals(22.5f, WheelPadGeometry.rotationOffsetDegrees(8, true), DELTA);
        assertEquals(15.0f, WheelPadGeometry.rotationOffsetDegrees(12, true), DELTA);
        assertEquals(11.25f, WheelPadGeometry.rotationOffsetDegrees(16, true), DELTA);
    }

    @Test
    public void centerAlignmentKeepsFirstSegmentCenteredAtTop() {
        assertEquals(-105.0f, WheelPadGeometry.segmentStartAngleDegrees(0, 12, false), DELTA);
        assertEquals(0, WheelPadGeometry.segmentIndexForAngleFromTop(0.0, 12, false));
        assertEquals(0, WheelPadGeometry.segmentIndexForAngleFromTop(14.9, 12, false));
        assertEquals(1, WheelPadGeometry.segmentIndexForAngleFromTop(15.1, 12, false));
    }

    @Test
    public void edgeAlignmentPlacesBoundaryAtTop() {
        assertEquals(-90.0f, WheelPadGeometry.segmentStartAngleDegrees(0, 12, true), DELTA);
        assertEquals(11, WheelPadGeometry.segmentIndexForAngleFromTop(-0.1, 12, true));
        assertEquals(0, WheelPadGeometry.segmentIndexForAngleFromTop(0.1, 12, true));
    }

    @Test
    public void hitTestingWrapsAroundFullCircle() {
        assertEquals(
                WheelPadGeometry.segmentIndexForAngleFromTop(-1.0, 8, false),
                WheelPadGeometry.segmentIndexForAngleFromTop(359.0, 8, false)
        );
        assertEquals(
                WheelPadGeometry.segmentIndexForAngleFromTop(1.0, 7, true),
                WheelPadGeometry.segmentIndexForAngleFromTop(361.0, 7, true)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSegmentCount() {
        WheelPadGeometry.sweepAngleDegrees(0);
    }
}
