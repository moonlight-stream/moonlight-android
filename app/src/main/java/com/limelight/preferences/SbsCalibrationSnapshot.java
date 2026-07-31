package com.limelight.preferences;

import java.util.Map;
import java.util.HashMap;

public final class SbsCalibrationSnapshot {
    public static final float MIN_OFFSET_PERCENTAGE = -25.0f;
    public static final float MAX_OFFSET_PERCENTAGE = 25.0f;
    public static final int MIN_CHROMATIC_CORRECTION_PERCENTAGE = -100;
    public static final int MAX_CHROMATIC_CORRECTION_PERCENTAGE = 100;
    public static final float MIN_COMMON_ANGLE_DEGREES = -30.0f;
    public static final float MAX_COMMON_ANGLE_DEGREES = 30.0f;
    public static final float MIN_CORRECTION_ANGLE_DEGREES = -15.0f;
    public static final float MAX_CORRECTION_ANGLE_DEGREES = 15.0f;

    public final int scalePercentage;
    public final int separationPercentage;
    public final int verticalPositionPercentage;
    public final boolean headTrackingEnabled;
    public final boolean headTrackingHorizontalEnabled;
    public final int headTrackingHorizontalSensitivityPercentage;
    public final boolean headTrackingVerticalEnabled;
    public final int headTrackingVerticalSensitivityPercentage;
    public final boolean lensHorizontalEnabled;
    public final int lensHorizontalCorrectionPercentage;
    public final boolean lensVerticalEnabled;
    public final int lensVerticalCorrectionPercentage;
    public final boolean chromaticHorizontalEnabled;
    public final int chromaticHorizontalCorrectionPercentage;
    public final boolean chromaticVerticalEnabled;
    public final int chromaticVerticalCorrectionPercentage;
    public final float commonHorizontalOffsetPercentage;
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
                                   int verticalPositionPercentage,
                                   boolean headTrackingEnabled,
                                   boolean headTrackingHorizontalEnabled,
                                   int headTrackingHorizontalSensitivityPercentage,
                                   boolean headTrackingVerticalEnabled,
                                   int headTrackingVerticalSensitivityPercentage,
                                   boolean lensHorizontalEnabled,
                                   int lensHorizontalCorrectionPercentage,
                                   boolean lensVerticalEnabled,
                                   int lensVerticalCorrectionPercentage,
                                   boolean chromaticHorizontalEnabled,
                                   int chromaticHorizontalCorrectionPercentage,
                                   boolean chromaticVerticalEnabled,
                                   int chromaticVerticalCorrectionPercentage,
                                   float commonHorizontalOffsetPercentage,
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
        this.headTrackingEnabled = headTrackingEnabled;
        this.headTrackingHorizontalEnabled = headTrackingHorizontalEnabled;
        this.headTrackingHorizontalSensitivityPercentage =
                headTrackingHorizontalSensitivityPercentage;
        this.headTrackingVerticalEnabled = headTrackingVerticalEnabled;
        this.headTrackingVerticalSensitivityPercentage =
                headTrackingVerticalSensitivityPercentage;
        this.lensHorizontalEnabled = lensHorizontalEnabled;
        this.lensHorizontalCorrectionPercentage = lensHorizontalCorrectionPercentage;
        this.lensVerticalEnabled = lensVerticalEnabled;
        this.lensVerticalCorrectionPercentage = lensVerticalCorrectionPercentage;
        this.chromaticHorizontalEnabled = chromaticHorizontalEnabled;
        this.chromaticHorizontalCorrectionPercentage =
                chromaticHorizontalCorrectionPercentage;
        this.chromaticVerticalEnabled = chromaticVerticalEnabled;
        this.chromaticVerticalCorrectionPercentage = chromaticVerticalCorrectionPercentage;
        this.commonHorizontalOffsetPercentage = commonHorizontalOffsetPercentage;
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
                false,
                true,
                PreferenceConfiguration.DEFAULT_SBS_HEAD_TRACKING_SENSITIVITY,
                true,
                PreferenceConfiguration.DEFAULT_SBS_HEAD_TRACKING_SENSITIVITY,
                true,
                PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION,
                true,
                PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION,
                true,
                0,
                true,
                0,
                PreferenceConfiguration.DEFAULT_SBS_OFFSET,
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
            boolean headTrackingEnabled, boolean headTrackingHorizontalEnabled,
            int headTrackingHorizontalSensitivityPercentage, boolean headTrackingVerticalEnabled,
            int headTrackingVerticalSensitivityPercentage,
            boolean lensHorizontalEnabled, int lensHorizontalCorrectionPercentage,
            boolean lensVerticalEnabled, int lensVerticalCorrectionPercentage,
            boolean chromaticHorizontalEnabled,
            int chromaticHorizontalCorrectionPercentage, boolean chromaticVerticalEnabled,
            int chromaticVerticalCorrectionPercentage,
            float commonHorizontalOffsetPercentage, float leftHorizontalOffsetPercentage,
            float rightHorizontalOffsetPercentage, float leftVerticalOffsetPercentage,
            float rightVerticalOffsetPercentage, float commonYawDegrees, float commonPitchDegrees,
            float leftYawCorrectionDegrees, float rightYawCorrectionDegrees,
            float leftPitchCorrectionDegrees, float rightPitchCorrectionDegrees) {
        return new SbsCalibrationSnapshot(
                clamp(scalePercentage, 50, 100),
                clamp(separationPercentage, 0, 100),
                clamp(verticalPositionPercentage, 0, 100),
                headTrackingEnabled,
                headTrackingHorizontalEnabled,
                clamp(headTrackingHorizontalSensitivityPercentage, 0, 100),
                headTrackingVerticalEnabled,
                clamp(headTrackingVerticalSensitivityPercentage, 0, 100),
                lensHorizontalEnabled,
                clamp(lensHorizontalCorrectionPercentage, 0, 100),
                lensVerticalEnabled,
                clamp(lensVerticalCorrectionPercentage, 0, 100),
                chromaticHorizontalEnabled,
                clamp(chromaticHorizontalCorrectionPercentage,
                        MIN_CHROMATIC_CORRECTION_PERCENTAGE,
                        MAX_CHROMATIC_CORRECTION_PERCENTAGE),
                chromaticVerticalEnabled,
                clamp(chromaticVerticalCorrectionPercentage,
                        MIN_CHROMATIC_CORRECTION_PERCENTAGE,
                        MAX_CHROMATIC_CORRECTION_PERCENTAGE),
                clamp(commonHorizontalOffsetPercentage,
                        MIN_OFFSET_PERCENTAGE, MAX_OFFSET_PERCENTAGE),
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
                getBoolean(values,
                        PreferenceConfiguration.SBS_HEAD_TRACKING_ENABLED_PREF_STRING, false),
                getBoolean(values,
                        PreferenceConfiguration.SBS_HEAD_TRACKING_HORIZONTAL_ENABLED_PREF_STRING,
                        true),
                getInt(values,
                        PreferenceConfiguration.SBS_HEAD_TRACKING_HORIZONTAL_SENSITIVITY_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_HEAD_TRACKING_SENSITIVITY),
                getBoolean(values,
                        PreferenceConfiguration.SBS_HEAD_TRACKING_VERTICAL_ENABLED_PREF_STRING,
                        true),
                getInt(values,
                        PreferenceConfiguration.SBS_HEAD_TRACKING_VERTICAL_SENSITIVITY_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_SBS_HEAD_TRACKING_SENSITIVITY),
                getBoolean(values,
                        PreferenceConfiguration.SBS_LENS_HORIZONTAL_ENABLED_PREF_STRING, true),
                getInt(values,
                        PreferenceConfiguration.SBS_LENS_HORIZONTAL_CORRECTION_PREF_STRING,
                        getInt(values, PreferenceConfiguration.SBS_LENS_CORRECTION_PREF_STRING,
                                PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION)),
                getBoolean(values,
                        PreferenceConfiguration.SBS_LENS_VERTICAL_ENABLED_PREF_STRING, true),
                getInt(values,
                        PreferenceConfiguration.SBS_LENS_VERTICAL_CORRECTION_PREF_STRING,
                        getInt(values, PreferenceConfiguration.SBS_LENS_CORRECTION_PREF_STRING,
                                PreferenceConfiguration.DEFAULT_SBS_LENS_CORRECTION)),
                getBoolean(values,
                        PreferenceConfiguration.SBS_CHROMATIC_HORIZONTAL_ENABLED_PREF_STRING, true),
                getInt(values,
                        PreferenceConfiguration.SBS_CHROMATIC_HORIZONTAL_CORRECTION_PREF_STRING,
                        getInt(values,
                                PreferenceConfiguration.SBS_CHROMATIC_CORRECTION_PREF_STRING, 0)),
                getBoolean(values,
                        PreferenceConfiguration.SBS_CHROMATIC_VERTICAL_ENABLED_PREF_STRING, true),
                getInt(values,
                        PreferenceConfiguration.SBS_CHROMATIC_VERTICAL_CORRECTION_PREF_STRING,
                        getInt(values,
                                PreferenceConfiguration.SBS_CHROMATIC_CORRECTION_PREF_STRING, 0)),
                getFloat(values, PreferenceConfiguration.SBS_COMMON_HORIZONTAL_OFFSET_PREF_STRING,
                        0.0f),
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
        values.put(PreferenceConfiguration.SBS_HEAD_TRACKING_ENABLED_PREF_STRING,
                headTrackingEnabled);
        values.put(PreferenceConfiguration.SBS_HEAD_TRACKING_HORIZONTAL_ENABLED_PREF_STRING,
                headTrackingHorizontalEnabled);
        values.put(PreferenceConfiguration.SBS_HEAD_TRACKING_HORIZONTAL_SENSITIVITY_PREF_STRING,
                headTrackingHorizontalSensitivityPercentage);
        values.put(PreferenceConfiguration.SBS_HEAD_TRACKING_VERTICAL_ENABLED_PREF_STRING,
                headTrackingVerticalEnabled);
        values.put(PreferenceConfiguration.SBS_HEAD_TRACKING_VERTICAL_SENSITIVITY_PREF_STRING,
                headTrackingVerticalSensitivityPercentage);
        values.put(PreferenceConfiguration.SBS_LENS_HORIZONTAL_ENABLED_PREF_STRING,
                lensHorizontalEnabled);
        values.put(PreferenceConfiguration.SBS_LENS_HORIZONTAL_CORRECTION_PREF_STRING,
                lensHorizontalCorrectionPercentage);
        values.put(PreferenceConfiguration.SBS_LENS_VERTICAL_ENABLED_PREF_STRING,
                lensVerticalEnabled);
        values.put(PreferenceConfiguration.SBS_LENS_VERTICAL_CORRECTION_PREF_STRING,
                lensVerticalCorrectionPercentage);
        values.put(PreferenceConfiguration.SBS_CHROMATIC_HORIZONTAL_ENABLED_PREF_STRING,
                chromaticHorizontalEnabled);
        values.put(PreferenceConfiguration.SBS_CHROMATIC_HORIZONTAL_CORRECTION_PREF_STRING,
                chromaticHorizontalCorrectionPercentage);
        values.put(PreferenceConfiguration.SBS_CHROMATIC_VERTICAL_ENABLED_PREF_STRING,
                chromaticVerticalEnabled);
        values.put(PreferenceConfiguration.SBS_CHROMATIC_VERTICAL_CORRECTION_PREF_STRING,
                chromaticVerticalCorrectionPercentage);
        values.put(PreferenceConfiguration.SBS_COMMON_HORIZONTAL_OFFSET_PREF_STRING,
                commonHorizontalOffsetPercentage);
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

    public float leftHorizontalCenterPercentage() {
        return commonHorizontalOffsetPercentage + leftHorizontalOffsetPercentage;
    }

    public float rightHorizontalCenterPercentage() {
        return commonHorizontalOffsetPercentage + rightHorizontalOffsetPercentage;
    }

    private static int getInt(Map<String, ?> values, String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static float getFloat(Map<String, ?> values, String key, float defaultValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
    }

    private static boolean getBoolean(Map<String, ?> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
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
