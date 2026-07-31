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
        assertEquals(50, snapshot.lensCorrectionPercentage);
        assertEquals(0.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.rightHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.commonYawDegrees, 0.0f);
        assertEquals(0.0f, snapshot.commonPitchDegrees, 0.0f);
    }

    @Test
    public void valuesAreClampedToSafeRanges() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.create(
                1, 200, -1, 500, -100, 100, Float.NaN, Float.POSITIVE_INFINITY,
                -100, 100, -100, 100, -100, 100);

        assertEquals(50, snapshot.scalePercentage);
        assertEquals(100, snapshot.separationPercentage);
        assertEquals(0, snapshot.verticalPositionPercentage);
        assertEquals(100, snapshot.lensCorrectionPercentage);
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
                73, 42, 61, 22, -3.5f, 4.5f, 2.0f, -2.0f,
                8.0f, -6.0f, 1.5f, -1.5f, 2.5f, -2.5f);

        SbsCalibrationSnapshot restored =
                SbsCalibrationSnapshot.fromPreferenceMap(original.toPreferenceMap());

        assertEquals(original.scalePercentage, restored.scalePercentage);
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
        assertEquals(41, snapshot.lensCorrectionPercentage);
        assertEquals(0.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
        assertEquals(0.0f, snapshot.rightYawCorrectionDegrees, 0.0f);
    }

    @Test
    public void commonAndPerEyeAnglesAreAddedWithoutDestroyingDifference() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.create(
                80, 50, 50, 50, 0, 0, 0, 0,
                10, -4, 2, -3, 1, -2);

        assertEquals(12.0f, snapshot.leftYawDegrees(), 0.0f);
        assertEquals(7.0f, snapshot.rightYawDegrees(), 0.0f);
        assertEquals(-3.0f, snapshot.leftPitchDegrees(), 0.0f);
        assertEquals(-6.0f, snapshot.rightPitchDegrees(), 0.0f);
        assertEquals(5.0f, snapshot.leftYawDegrees() - snapshot.rightYawDegrees(), 0.0f);
    }
}
