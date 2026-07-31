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
        String body = "scale=200&separation=50&verticalPosition=50&lensCorrection=50" +
                "&leftHorizontalOffset=-99&rightHorizontalOffset=0" +
                "&leftVerticalOffset=0&rightVerticalOffset=0" +
                "&commonYaw=0&commonPitch=0&leftYawCorrection=0" +
                "&rightYawCorrection=0&leftPitchCorrection=0&rightPitchCorrection=0";

        SbsCalibrationSnapshot snapshot =
                SbsCalibrationServer.parseSnapshot(SbsCalibrationServer.parseForm(body));

        assertEquals(100, snapshot.scalePercentage);
        assertEquals(-25.0f, snapshot.leftHorizontalOffsetPercentage, 0.0f);
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
