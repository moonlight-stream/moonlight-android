package com.limelight.preferences;


import android.content.Context;
import android.content.SharedPreferences;

public class GlPreferences {
    private static final String PREF_NAME = "GlPreferences";

    private static final String FINGERPRINT_PREF_STRING = "Fingerprint";
    private static final String GL_RENDERER_PREF_STRING = "Renderer";

    private SharedPreferences prefs;
    public String glRenderer;
    public String savedFingerprint;

    private GlPreferences(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static GlPreferences readPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, 0);
        GlPreferences glPrefs = new GlPreferences(prefs);

        glPrefs.glRenderer = prefs.getString(GL_RENDERER_PREF_STRING, "");
        glPrefs.savedFingerprint = prefs.getString(FINGERPRINT_PREF_STRING, "");

        return glPrefs;
    }

    public boolean writePreferences() {
        return prefs.edit()
                .putString(GL_RENDERER_PREF_STRING, glRenderer)
                .putString(FINGERPRINT_PREF_STRING, savedFingerprint)
                .commit();
    }
}
