package com.limelight.preferences;

import java.util.Map;
import java.util.HashMap;

public final class SbsCalibrationSnapshot {
    public static final float MIN_OFFSET_PERCENTAGE = -25.0f;
    public static final float MAX_OFFSET_PERCENTAGE = 25.0f;
    public static final float MIN_COMMON_ANGLE_DEGREES = -30.0f;
    public static final float MAX_COMMON_ANGLE_DEGREES = 30.0f;
    public static final float MIN_CORRECTION_ANGLE_DEGREES = -15.0f;
    public static final float MAX_CORRECTION_ANGLE_DEGREES = 15.0f;

    public final int scalePercentage;
    public final int separationPercentage;
    public final int verticalPositionPercentage;
    public final int lensCorrectionPercentage;
    public final float leftHorizontalOffsetPercentage;
    public final float rightHorizontalOffsetPercentage;
    public final float leftVerticalOffsetPercentage;
    public final float rightVerticalOffsetPercentage;
    public final float commonYawDegrees;
    public final float commonPitchDegrees;
    public final float leftYawCorrectionDegrees;
    public final float rightYawCorrectionDegrees;
    public final float leftPitchCorrectionDegrees;
    public final float rightPitchCorrectionDegrees;

    private SbsCalibrationSnapshot(int scalePercentage, int separationPercentage,
                                   int verticalPositionPercentage, int lensCorrectionPercentage,
                                   float leftHorizontalOffsetPercentage,
                                   float rightHorizontalOffsetPercentage,
                                   float leftVerticalOffsetPercentage,
                                   float rightVerticalOffsetPercentage,
                                   float commonYawDegrees, float commonPitchDegrees,
                                   float leftYawCorrectionDegrees, float rightYawCorrectionDegrees,
                                   float leftPitchCorrectionDegrees, float rightPitchCorrectionDegrees) {
        this.scalePercentage = scalePercentage;
        this.separationPercentage = separationPercentage;
        this.verticalPositionPercentage = verticalPositionPercentage;
        this.lensCorrectionPercentage = lensCorrectionPercentage;
        this.leftHorizontalOffsetPercentage = leftHorizontalOffsetPercentage;
        this.rightHorizontalOffsetPercentage = rightHorizontalOffsetPercentage;
        this.leftVerticalOffsetPercentage = leftVerticalOffsetPercentage;
        this.rightVerticalOffsetPercentage = rightVerticalOffsetPercentage;
        this.commonYawDegrees = commonYawDegrees;
        this.commonPitchDegrees = commonPitchDegrees;
        this.leftYawCorrectionDegrees = leftYawCorrectionDegrees;
        this.rightYawCorrectionDegrees = rightYawCorrectionDegrees;
        this.leftPitchCorrectionDegrees = leftPitchCorrectionDegrees;
        this.rightPitchCorrectionDegrees = rightPitchCorrectionDegrees;
    }

    public static SbsCalibrationSnapshot defaults() {
        return create(
                PreferenceConfiguration.DEFAULT_SBS_SCALE,
                PreferenceConfiguration.DEFAULT_SBS_SEPARATION,
                PreferenceConfiguration.DEFAULT_SBS_VERTICAL_POSITION,
                PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION,
                PreferenceConfiguration.DEFAULT_SBS_OFFSET,
                PreferenceConfiguration.DEFAULT_SBS_OFFSET,
                PreferenceConfiguration.DEFAULT_SBS_OFFSET,
                PreferenceConfiguration.DEFAULT_SBS_OFFSET,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE,
                PreferenceConfiguration.DEFAULT_SBS_ANGLE);
    }

    public static SbsCalibrationSnapshot create(
            int scalePercentage, int separationPercentage, int verticalPositionPercentage,
            int lensCorrectionPercentage, float leftHorizontalOffsetPercentage,
            float rightHorizontalOffsetPercentage, float leftVerticalOffsetPercentage,
            float rightVerticalOffsetPercentage, float commonYawDegrees, float commonPitchDegrees,
            float leftYawCorrectionDegrees, float rightYawCorrectionDegrees,
            float leftPitchCorrectionDegrees, float rightPitchCorrectionDegrees) {
        return new SbsCalibrationSnapshot(
                clamp(scalePercentage, 50, 100),
                clamp(separationPercentage, 0, 100),
                clamp(verticalPositionPercentage, 0, 100),
                clamp(lensCorrectionPercentage, 0, 100),
                clamp(leftHorizontalOffsetPercentage, MIN_OFFSET_PERCENTAGE, MAX_OFFSET_PERCENTAGE),
                clamp(rightHorizontalOffsetPercentage, MIN_OFFSET_PERCENTAGE, MAX_OFFSET_PERCENTAGE),
                clamp(leftVerticalOffsetPercentage, MIN_OFFSET_PERCENTAGE, MAX_OFFSET_PERCENTAGE),
                clamp(rightVerticalOffsetPercentage, MIN_OFFSET_PERCENTAGE, MAX_OFFSET_PERCENTAGE),
                clamp(commonYawDegrees, MIN_COMMON_ANGLE_DEGREES, MAX_COMMON_ANGLE_DEGREES),
                clamp(commonPitchDegrees, MIN_COMMON_ANGLE_DEGREES, MAX_COMMON_ANGLE_DEGREES),
                clamp(leftYawCorrectionDegrees,
                        MIN_CORRECTION_ANGLE_DEGREES, MAX_CORRECTION_ANGLE_DEGREES),
                clamp(rightYawCorrectionDegrees,
                        MIN_CORRECTION_ANGLE_DEGREES, MAX_CORRECTION_ANGLE_DEGREES),
                clamp(leftPitchCorrectionDegrees,
                        MIN_CORRECTION_ANGLE_DEGREES, MAX_CORRECTION_ANGLE_DEGREES),
                clamp(rightPitchCorrectionDegrees,
                        MIN_CORRECTION_ANGLE_DEGREES, MAX_CORRECTION_ANGLE_DEGREES));
    }

    public static SbsCalibrationSnapshot fromPreferenceMap(Map<String, ?> values) {
        return create(
                getInt(values, PreferenceConfiguration.SBS_SCALE_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_SCALE),
                getInt(values, PreferenceConfiguration.SBS_SEPARATION_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_SEPARATION),
                getInt(values, PreferenceConfiguration.SBS_VERTICAL_POSITION_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_VERTICAL_POSITION),
                getInt(values, PreferenceConfiguration.SBS_LENS_CORRECTION_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION),
                getFloat(values, PreferenceConfiguration.SBS_LEFT_HORIZONTAL_OFFSET_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_RIGHT_HORIZONTAL_OFFSET_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_LEFT_VERTICAL_OFFSET_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_RIGHT_VERTICAL_OFFSET_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_COMMON_YAW_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_COMMON_PITCH_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_LEFT_YAW_CORRECTION_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_RIGHT_YAW_CORRECTION_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_LEFT_PITCH_CORRECTION_PREF_STRING, 0.0f),
                getFloat(values, PreferenceConfiguration.SBS_RIGHT_PITCH_CORRECTION_PREF_STRING, 0.0f));
    }

    public Map<String, Object> toPreferenceMap() {
        Map<String, Object> values = new HashMap<>();
        values.put(PreferenceConfiguration.SBS_SCALE_PREF_STRING, scalePercentage);
        values.put(PreferenceConfiguration.SBS_SEPARATION_PREF_STRING, separationPercentage);
        values.put(PreferenceConfiguration.SBS_VERTICAL_POSITION_PREF_STRING, verticalPositionPercentage);
        values.put(PreferenceConfiguration.SBS_LENS_CORRECTION_PREF_STRING, lensCorrectionPercentage);
        values.put(PreferenceConfiguration.SBS_LEFT_HORIZONTAL_OFFSET_PREF_STRING,
                leftHorizontalOffsetPercentage);
        values.put(PreferenceConfiguration.SBS_RIGHT_HORIZONTAL_OFFSET_PREF_STRING,
                rightHorizontalOffsetPercentage);
        values.put(PreferenceConfiguration.SBS_LEFT_VERTICAL_OFFSET_PREF_STRING,
                leftVerticalOffsetPercentage);
        values.put(PreferenceConfiguration.SBS_RIGHT_VERTICAL_OFFSET_PREF_STRING,
                rightVerticalOffsetPercentage);
        values.put(PreferenceConfiguration.SBS_COMMON_YAW_PREF_STRING, commonYawDegrees);
        values.put(PreferenceConfiguration.SBS_COMMON_PITCH_PREF_STRING, commonPitchDegrees);
        values.put(PreferenceConfiguration.SBS_LEFT_YAW_CORRECTION_PREF_STRING,
                leftYawCorrectionDegrees);
        values.put(PreferenceConfiguration.SBS_RIGHT_YAW_CORRECTION_PREF_STRING,
                rightYawCorrectionDegrees);
        values.put(PreferenceConfiguration.SBS_LEFT_PITCH_CORRECTION_PREF_STRING,
                leftPitchCorrectionDegrees);
        values.put(PreferenceConfiguration.SBS_RIGHT_PITCH_CORRECTION_PREF_STRING,
                rightPitchCorrectionDegrees);
        return values;
    }

    public float leftYawDegrees() {
        return commonYawDegrees + leftYawCorrectionDegrees;
    }

    public float rightYawDegrees() {
        return commonYawDegrees + rightYawCorrectionDegrees;
    }

    public float leftPitchDegrees() {
        return commonPitchDegrees + leftPitchCorrectionDegrees;
    }

    public float rightPitchDegrees() {
        return commonPitchDegrees + rightPitchCorrectionDegrees;
    }

    private static int getInt(Map<String, ?> values, String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static float getFloat(Map<String, ?> values, String key, float defaultValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
