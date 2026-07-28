package com.limelight.binding.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.jni.MoonBridge;

import org.junit.Test;

/**
 * Tests controller capability resolution for native and handheld rumble.
 */
public class ControllerCapabilitiesTest {
    /**
     * Verifies that native controller rumble is always advertised.
     */
    @Test
    public void advertisesNativeRumble() {
        assertTrue(ControllerCapabilities.shouldAdvertiseRumble(
                true, false, false, 1));
    }

    /**
     * Verifies that player one can advertise enabled handheld vibration fallback.
     */
    @Test
    public void advertisesPlayerOneFallback() {
        assertTrue(ControllerCapabilities.shouldAdvertiseRumble(
                false, true, true, 0));
    }

    /**
     * Verifies that disabled or unavailable fallback is not advertised.
     */
    @Test
    public void rejectsUnavailableFallback() {
        assertFalse(ControllerCapabilities.shouldAdvertiseRumble(
                false, false, true, 0));
        assertFalse(ControllerCapabilities.shouldAdvertiseRumble(
                false, true, false, 0));
    }

    /**
     * Verifies that the handheld vibrator is not advertised for other players.
     */
    @Test
    public void rejectsFallbackForOtherPlayers() {
        assertFalse(ControllerCapabilities.shouldAdvertiseRumble(
                false, true, true, 1));
    }

    /**
     * Verifies that a calibrated controller is announced when streaming begins.
     */
    @Test
    public void announcesCalibratedControllerOnConnection() {
        assertTrue(ControllerCapabilities.shouldAnnounceOnConnection(true, true));
    }

    /**
     * Verifies that unprofiled or non-controller devices are not announced.
     */
    @Test
    public void rejectsIneligibleConnectionAnnouncements() {
        assertFalse(ControllerCapabilities.shouldAnnounceOnConnection(false, true));
        assertFalse(ControllerCapabilities.shouldAnnounceOnConnection(true, false));
    }

    /**
     * Verifies automatic handheld gyro for a calibrated DualSense Edge.
     */
    @Test
    public void usesDeviceMotionForCalibratedDualSenseEdge() {
        assertTrue(ControllerCapabilities.shouldUseDeviceMotion(
                false, true, true, true, false));
    }

    /**
     * Verifies that motion fallback respects controller and player constraints.
     */
    @Test
    public void rejectsIneligibleDeviceMotionFallback() {
        assertFalse(ControllerCapabilities.shouldUseDeviceMotion(
                false, true, false, true, false));
        assertFalse(ControllerCapabilities.shouldUseDeviceMotion(
                true, false, true, false, false));
        assertFalse(ControllerCapabilities.shouldUseDeviceMotion(
                true, false, true, true, true));
    }

    /**
     * Verifies that calibrated rear controls report the PlayStation family.
     */
    @Test
    public void reportsCalibratedControllerAsPlayStation() {
        assertEquals(MoonBridge.LI_CTYPE_PS,
                ControllerCapabilities.resolveReportedType(
                        MoonBridge.LI_CTYPE_XBOX, true, true));
    }

    /**
     * Verifies automatic family selection for other motion-emulated controllers.
     */
    @Test
    public void preservesAutomaticMotionFamilySelection() {
        assertEquals(MoonBridge.LI_CTYPE_UNKNOWN,
                ControllerCapabilities.resolveReportedType(
                        MoonBridge.LI_CTYPE_XBOX, false, true));
        assertEquals(MoonBridge.LI_CTYPE_XBOX,
                ControllerCapabilities.resolveReportedType(
                        MoonBridge.LI_CTYPE_XBOX, false, false));
    }
}
