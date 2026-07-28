package com.limelight.ui.console;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherEffectPolicyTest {
    @Test
    public void reducedMotionUsesShortOrInstantTransitions() {
        assertEquals(90, LauncherEffectPolicy.focusDuration(true));
        assertEquals(0, LauncherEffectPolicy.backdropDuration(true));
        assertEquals(420, LauncherEffectPolicy.backdropDuration(false));
    }

    @Test
    public void blurRequiresApi31DynamicArtAndNonLowRamDevice() {
        assertFalse(LauncherEffectPolicy.allowBlur(30, false, true));
        assertFalse(LauncherEffectPolicy.allowBlur(35, true, true));
        assertFalse(LauncherEffectPolicy.allowBlur(35, false, false));
        assertTrue(LauncherEffectPolicy.allowBlur(35, false, true));
    }
}
