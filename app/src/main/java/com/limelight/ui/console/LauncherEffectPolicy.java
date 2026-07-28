package com.limelight.ui.console;

public final class LauncherEffectPolicy {
    private LauncherEffectPolicy() {
    }

    public static long focusDuration(boolean reducedMotion) {
        return reducedMotion ? 90 : 160;
    }

    public static long heroDuration(boolean reducedMotion) {
        return reducedMotion ? 90 : 180;
    }

    public static long backdropDuration(boolean reducedMotion) {
        return reducedMotion ? 0 : 420;
    }

    public static boolean allowBlur(int apiLevel, boolean lowRam,
                                    boolean dynamicBackgrounds) {
        return dynamicBackgrounds && apiLevel >= 31 && !lowRam;
    }
}
