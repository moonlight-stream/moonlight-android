package com.limelight.binding.input.advance_setting.element;

final class WheelPadGeometry {
    private static final float FULL_CIRCLE_DEGREES = 360.0f;

    private WheelPadGeometry() {
    }

    static float sweepAngleDegrees(int segmentCount) {
        if (segmentCount <= 0) {
            throw new IllegalArgumentException("segmentCount must be positive");
        }
        return FULL_CIRCLE_DEGREES / segmentCount;
    }

    static float rotationOffsetDegrees(int segmentCount, boolean alignSegmentEdge) {
        return alignSegmentEdge ? sweepAngleDegrees(segmentCount) / 2.0f : 0.0f;
    }

    static float segmentStartAngleDegrees(int index, int segmentCount, boolean alignSegmentEdge) {
        float sweepAngle = sweepAngleDegrees(segmentCount);
        return (index * sweepAngle) - (sweepAngle / 2.0f) - 90.0f
                + rotationOffsetDegrees(segmentCount, alignSegmentEdge);
    }

    static int segmentIndexForVector(float dx, float dy, int segmentCount, boolean alignSegmentEdge) {
        double angleFromTop = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        return segmentIndexForAngleFromTop(angleFromTop, segmentCount, alignSegmentEdge);
    }

    static int segmentIndexForAngleFromTop(double angleFromTop, int segmentCount, boolean alignSegmentEdge) {
        float sweepAngle = sweepAngleDegrees(segmentCount);
        double adjustedAngle = normalizeDegrees(
                angleFromTop - rotationOffsetDegrees(segmentCount, alignSegmentEdge) + sweepAngle / 2.0f
        );
        return ((int) Math.floor(adjustedAngle / sweepAngle)) % segmentCount;
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % FULL_CIRCLE_DEGREES;
        return normalized < 0.0 ? normalized + FULL_CIRCLE_DEGREES : normalized;
    }
}
