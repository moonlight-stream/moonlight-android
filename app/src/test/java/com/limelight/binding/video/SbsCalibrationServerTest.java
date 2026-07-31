package com.limelight.binding.video;

import com.limelight.preferences.SbsCalibrationSnapshot;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SbsCalibrationServerTest {
    @Test
    public void formParserDecodesBrowserEncoding() {
        Map<String, String> values = SbsCalibrationServer.parseForm(
                "commonYaw=-2.5&label=left+eye&encoded=a%2Bb");

        assertEquals("-2.5", values.get("commonYaw"));
        assertEquals("left eye", values.get("label"));
        assertEquals("a+b", values.get("encoded"));
    }

    @Test
    public void snapshotParserClampsHttpValues() {
        String body = "scale=200&separation=50&verticalPosition=50" +
                "&headTrackingEnabled=true" +
                "&headTrackingHorizontalEnabled=false" +
                "&headTrackingHorizontalSensitivity=500" +
                "&headTrackingVerticalEnabled=true" +
                "&headTrackingVerticalSensitivity=-500" +
                "&lensHorizontalEnabled=true&lensHorizontalCorrection=500" +
                "&lensVerticalEnabled=false&lensVerticalCorrection=-500" +
                "&chromaticHorizontalEnabled=true&chromaticHorizontalCorrection=500" +
                "&chromaticVerticalEnabled=false&chromaticVerticalCorrection=-500" +
                "&commonHorizontalOffset=-99" +
                "&leftHorizontalOffset=-99&rightHorizontalOffset=0" +
                "&leftVerticalOffset=0&rightVerticalOffset=0" +
                "&commonYaw=0&commonPitch=0&leftYawCorrection=0" +
                "&rightYawCorrection=0&leftPitchCorrection=0&rightPitchCorrection=0";

        SbsCalibrationSnapshot snapshot =
                SbsCalibrationServer.parseSnapshot(SbsCalibrationServer.parseForm(body));

        assertEquals(100, snapshot.scalePercentage);
        assertEquals(true, snapshot.headTrackingEnabled);
        assertEquals(false, snapshot.headTrackingHorizontalEnabled);
        assertEquals(100, snapshot.headTrackingHorizontalSensitivityPercentage);
        assertEquals(true, snapshot.headTrackingVerticalEnabled);
        assertEquals(0, snapshot.headTrackingVerticalSensitivityPercentage);
        assertEquals(true, snapshot.lensHorizontalEnabled);
        assertEquals(100, snapshot.lensHorizontalCorrectionPercentage);
        assertEquals(false, snapshot.lensVerticalEnabled);
        assertEquals(0, snapshot.lensVerticalCorrectionPercentage);
        assertEquals(true, snapshot.chromaticHorizontalEnabled);
        assertEquals(100, snapshot.chromaticHorizontalCorrectionPercentage);
        assertEquals(false, snapshot.chromaticVerticalEnabled);
        assertEquals(-100, snapshot.chromaticVerticalCorrectionPercentage);
        assertEquals(-25.0f, snapshot.commonHorizontalOffsetPercentage, 0.0f);
        assertEquals(-25.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
    }

    @Test
    public void jsonIncludesNewLiveCalibrationFields() {
        String json = SbsCalibrationServer.toJson(SbsCalibrationSnapshot.defaults());

        assertTrue(json.contains("\"lensHorizontalEnabled\":true"));
        assertTrue(json.contains("\"headTrackingEnabled\":false"));
        assertTrue(json.contains("\"headTrackingHorizontalEnabled\":true"));
        assertTrue(json.contains("\"headTrackingHorizontalSensitivity\":50"));
        assertTrue(json.contains("\"headTrackingVerticalEnabled\":true"));
        assertTrue(json.contains("\"headTrackingVerticalSensitivity\":50"));
        assertTrue(json.contains("\"lensHorizontalCorrection\":50"));
        assertTrue(json.contains("\"lensVerticalEnabled\":true"));
        assertTrue(json.contains("\"lensVerticalCorrection\":50"));
        assertTrue(json.contains("\"chromaticHorizontalEnabled\":true"));
        assertTrue(json.contains("\"chromaticHorizontalCorrection\":0"));
        assertTrue(json.contains("\"chromaticVerticalEnabled\":true"));
        assertTrue(json.contains("\"chromaticVerticalCorrection\":0"));
        assertTrue(json.contains("\"commonHorizontalOffset\":0.000"));
    }

    @Test
    public void portValidationRejectsPrivilegedAndOverflowPorts() {
        assertFalse(SbsCalibrationServer.isValidPort(80));
        assertTrue(SbsCalibrationServer.isValidPort(48180));
        assertFalse(SbsCalibrationServer.isValidPort(65536));
    }

    @Test(expected = IllegalArgumentException.class)
    public void snapshotParserRejectsMissingFields() {
        SbsCalibrationServer.parseSnapshot(SbsCalibrationServer.parseForm("scale=80"));
    }
}
