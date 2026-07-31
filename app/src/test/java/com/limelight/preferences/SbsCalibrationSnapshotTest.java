package com.limelight.preferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SbsCalibrationSnapshotTest {
    @Test
    public void defaultsPreserveExistingSbsOutput() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.defaults();

        assertEquals(80, snapshot.scalePercentage);
        assertEquals(50, snapshot.separationPercentage);
        assertEquals(50, snapshot.verticalPositionPercentage);
        assertEquals(false, snapshot.headTrackingEnabled);
        assertEquals(true, snapshot.headTrackingHorizontalEnabled);
        assertEquals(50, snapshot.headTrackingHorizontalSensitivityPercentage);
        assertEquals(true, snapshot.headTrackingVerticalEnabled);
        assertEquals(50, snapshot.headTrackingVerticalSensitivityPercentage);
        assertEquals(true, snapshot.lensHorizontalEnabled);
        assertEquals(50, snapshot.lensHorizontalCorrectionPercentage);
        assertEquals(true, snapshot.lensVerticalEnabled);
        assertEquals(50, snapshot.lensVerticalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticHorizontalEnabled);
        assertEquals(0, snapshot.chromaticHorizontalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticVerticalEnabled);
        assertEquals(0, snapshot.chromaticVerticalCorrectionPercentage);
        assertEquals(0.0f, snapshot.commonHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.rightHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.commonYawDegrees, 0.0f);
        assertEquals(0.0f, snapshot.commonPitchDegrees, 0.0f);
    }

    @Test
    public void valuesAreClampedToSafeRanges() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.create(
                1, 200, -1, true, false, 500, true, -500,
                true, 500, false, -500,
                true, 500, false, -500, -100, -100, 100,
                Float.NaN, Float.POSITIVE_INFINITY,
                -100, 100, -100, 100, -100, 100);

        assertEquals(50, snapshot.scalePercentage);
        assertEquals(100, snapshot.separationPercentage);
        assertEquals(0, snapshot.verticalPositionPercentage);
        assertEquals(true, snapshot.headTrackingEnabled);
        assertEquals(false, snapshot.headTrackingHorizontalEnabled);
        assertEquals(100, snapshot.headTrackingHorizontalSensitivityPercentage);
        assertEquals(true, snapshot.headTrackingVerticalEnabled);
        assertEquals(0, snapshot.headTrackingVerticalSensitivityPercentage);
        assertEquals(100, snapshot.lensHorizontalCorrectionPercentage);
        assertEquals(0, snapshot.lensVerticalCorrectionPercentage);
        assertEquals(100, snapshot.chromaticHorizontalCorrectionPercentage);
        assertEquals(-100, snapshot.chromaticVerticalCorrectionPercentage);
        assertEquals(-25.0f, snapshot.commonHorizontalOffsetPercentage, 0.0f);
        assertEquals(-25.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
        assertEquals(25.0f, snapshot.rightHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.leftVerticalOffsetPercentage, 0.0f);
        assertEquals(-30.0f, snapshot.commonYawDegrees, 0.0f);
        assertEquals(30.0f, snapshot.commonPitchDegrees, 0.0f);
        assertEquals(-15.0f, snapshot.leftYawCorrectionDegrees, 0.0f);
        assertEquals(15.0f, snapshot.rightPitchCorrectionDegrees, 0.0f);
    }

    @Test
    public void preferenceRoundTripPreservesSnapshot() {
        SbsCalibrationSnapshot original = SbsCalibrationSnapshot.create(
                73, 42, 61, true, false, 35, true, 68,
                true, 22, false, 77,
                true, -35, false, 28,
                6.5f, -3.5f, 4.5f, 2.0f, -2.0f,
                8.0f, -6.0f, 1.5f, -1.5f, 2.5f, -2.5f);

        SbsCalibrationSnapshot restored =
                SbsCalibrationSnapshot.fromPreferenceMap(original.toPreferenceMap());

        assertEquals(original.scalePercentage, restored.scalePercentage);
        assertEquals(original.headTrackingEnabled, restored.headTrackingEnabled);
        assertEquals(original.headTrackingHorizontalEnabled,
                restored.headTrackingHorizontalEnabled);
        assertEquals(original.headTrackingHorizontalSensitivityPercentage,
                restored.headTrackingHorizontalSensitivityPercentage);
        assertEquals(original.headTrackingVerticalEnabled, restored.headTrackingVerticalEnabled);
        assertEquals(original.headTrackingVerticalSensitivityPercentage,
                restored.headTrackingVerticalSensitivityPercentage);
        assertEquals(original.lensHorizontalEnabled, restored.lensHorizontalEnabled);
        assertEquals(original.lensHorizontalCorrectionPercentage,
                restored.lensHorizontalCorrectionPercentage);
        assertEquals(original.lensVerticalEnabled, restored.lensVerticalEnabled);
        assertEquals(original.lensVerticalCorrectionPercentage,
                restored.lensVerticalCorrectionPercentage);
        assertEquals(original.chromaticHorizontalEnabled,
                restored.chromaticHorizontalEnabled);
        assertEquals(original.chromaticHorizontalCorrectionPercentage,
                restored.chromaticHorizontalCorrectionPercentage);
        assertEquals(original.chromaticVerticalEnabled, restored.chromaticVerticalEnabled);
        assertEquals(original.chromaticVerticalCorrectionPercentage,
                restored.chromaticVerticalCorrectionPercentage);
        assertEquals(original.commonHorizontalOffsetPercentage,
                restored.commonHorizontalOffsetPercentage, 0.0f);
        assertEquals(original.rightHorizontalOffsetPercentage,
                restored.rightHorizontalOffsetPercentage, 0.0f);
        assertEquals(original.commonYawDegrees, restored.commonYawDegrees, 0.0f);
        assertEquals(original.leftPitchCorrectionDegrees,
                restored.leftPitchCorrectionDegrees, 0.0f);
    }

    @Test
    public void legacyPreferencesGainOnlyZeroTransformDefaults() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put(PreferenceConfiguration.SBS_SCALE_PREF_STRING, 67);
        legacy.put(PreferenceConfiguration.SBS_SEPARATION_PREF_STRING, 39);
        legacy.put(PreferenceConfiguration.SBS_VERTICAL_POSITION_PREF_STRING, 58);
        legacy.put(PreferenceConfiguration.SBS_LENS_CORRECTION_PREF_STRING, 41);

        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.fromPreferenceMap(legacy);

        assertEquals(67, snapshot.scalePercentage);
        assertEquals(39, snapshot.separationPercentage);
        assertEquals(58, snapshot.verticalPositionPercentage);
        assertEquals(false, snapshot.headTrackingEnabled);
        assertEquals(true, snapshot.headTrackingHorizontalEnabled);
        assertEquals(50, snapshot.headTrackingHorizontalSensitivityPercentage);
        assertEquals(true, snapshot.headTrackingVerticalEnabled);
        assertEquals(50, snapshot.headTrackingVerticalSensitivityPercentage);
        assertEquals(true, snapshot.lensHorizontalEnabled);
        assertEquals(41, snapshot.lensHorizontalCorrectionPercentage);
        assertEquals(true, snapshot.lensVerticalEnabled);
        assertEquals(41, snapshot.lensVerticalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticHorizontalEnabled);
        assertEquals(0, snapshot.chromaticHorizontalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticVerticalEnabled);
        assertEquals(0, snapshot.chromaticVerticalCorrectionPercentage);
        assertEquals(0.0f, snapshot.commonHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.rightYawCorrectionDegrees, 0.0f);
    }

    @Test
    public void legacyChromaticCorrectionMigratesToBothEnabledAxes() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put(PreferenceConfiguration.SBS_CHROMATIC_CORRECTION_PREF_STRING, -42);

        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.fromPreferenceMap(legacy);

        assertEquals(true, snapshot.chromaticHorizontalEnabled);
        assertEquals(-42, snapshot.chromaticHorizontalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticVerticalEnabled);
        assertEquals(-42, snapshot.chromaticVerticalCorrectionPercentage);
    }

    @Test
    public void commonAndPerEyeAnglesAreAddedWithoutDestroyingDifference() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.create(
                80, 50, 50, false, true, 50, true, 50,
                true, 50, true, 50,
                true, 0, true, 0, 0, 0, 0, 0, 0,
                10, -4, 2, -3, 1, -2);

        assertEquals(12.0f, snapshot.leftYawDegrees(), 0.0f);
        assertEquals(7.0f, snapshot.rightYawDegrees(), 0.0f);
        assertEquals(-3.0f, snapshot.leftPitchDegrees(), 0.0f);
        assertEquals(-6.0f, snapshot.rightPitchDegrees(), 0.0f);
        assertEquals(5.0f, snapshot.leftYawDegrees() - snapshot.rightYawDegrees(), 0.0f);
    }

    @Test
    public void commonHorizontalOffsetMovesConfiguredPairTogether() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.create(
                80, 50, 50, false, true, 50, true, 50,
                true, 50, true, 50,
                true, 0, true, 0, 7, -3, 5, 0, 0,
                0, 0, 0, 0, 0, 0);

        assertEquals(4.0f, snapshot.leftHorizontalCenterPercentage(), 0.0f);
        assertEquals(12.0f, snapshot.rightHorizontalCenterPercentage(), 0.0f);
        assertEquals(-8.0f, snapshot.leftHorizontalCenterPercentage() -
                snapshot.rightHorizontalCenterPercentage(), 0.0f);
    }
}
