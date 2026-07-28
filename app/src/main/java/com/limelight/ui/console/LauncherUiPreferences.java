package com.limelight.ui.console;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class LauncherUiPreferences {
    public static final String KEY_INTERFACE_SOUNDS = "checkbox_interface_sounds";
    public static final String KEY_NAVIGATION_HAPTICS = "checkbox_navigation_haptics";
    public static final String KEY_REDUCED_MOTION = "checkbox_reduced_motion";
    public static final String KEY_DYNAMIC_BACKGROUNDS = "checkbox_dynamic_backgrounds";

    public final boolean interfaceSounds;
    public final boolean navigationHaptics;
    public final boolean reducedMotion;
    public final boolean dynamicBackgrounds;

    private LauncherUiPreferences(SharedPreferences preferences) {
        interfaceSounds = preferences.getBoolean(KEY_INTERFACE_SOUNDS, true);
        navigationHaptics = preferences.getBoolean(KEY_NAVIGATION_HAPTICS, true);
        reducedMotion = preferences.getBoolean(KEY_REDUCED_MOTION, false);
        dynamicBackgrounds = preferences.getBoolean(KEY_DYNAMIC_BACKGROUNDS, true);
    }

    public static LauncherUiPreferences read(Context context) {
        return new LauncherUiPreferences(PreferenceManager.getDefaultSharedPreferences(context));
    }
}
