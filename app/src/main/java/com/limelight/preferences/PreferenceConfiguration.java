package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class PreferenceConfiguration {
    static final String RESX_PREF_STRING = "resolution_x";
    static final String RESY_PREF_STRING = "resolution_y";
    private static final String FPS_PREF_STRING = "fps";
    private static final String DECODER_PREF_STRING = "list_decoders";
    static final String BITRATE_PREF_STRING = "seekbar_bitrate";
    private static final String STRETCH_PREF_STRING = "checkbox_stretch_video";
    private static final String SOPS_PREF_STRING = "checkbox_enable_sops";
    private static final String DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings";
    private static final String HOST_AUDIO_PREF_STRING = "checkbox_host_audio";
    private static final String DEADZONE_PREF_STRING = "seekbar_deadzone";
    private static final String LANGUAGE_PREF_STRING = "list_languages";
    private static final String LIST_MODE_PREF_STRING = "checkbox_list_mode";
    private static final String SMALL_ICONS_PREF_STRING = "checkbox_small_icon_mode";

    private static final int BITRATE_DEFAULT_720_30 = 5;
    private static final int BITRATE_DEFAULT_720_60 = 10;
    private static final int BITRATE_DEFAULT_1080_30 = 10;
    private static final int BITRATE_DEFAULT_1080_60 = 20;
    private static final int BITRATE_DEFAULT_MAX = 35;

    private static final int DEFAULT_RESX = 1280;
    private static final int DEFAULT_RESY = 720;
    private static final String DEFAULT_FPS = "60";
    private static final String DEFAULT_DECODER = "auto";
    private static final boolean DEFAULT_STRETCH = false;
    private static final boolean DEFAULT_SOPS = true;
    private static final boolean DEFAULT_DISABLE_TOASTS = false;
    private static final boolean DEFAULT_HOST_AUDIO = false;
    private static final int DEFAULT_DEADZONE = 15;
    public static final String DEFAULT_LANGUAGE = "default";
    private static final boolean DEFAULT_LIST_MODE = false;

    public static final int FORCE_HARDWARE_DECODER = -1;
    public static final int AUTOSELECT_DECODER = 0;
    public static final int FORCE_SOFTWARE_DECODER = 1;

    public int width, height, fps;
    public int bitrate;
    public int decoder;
    public int deadzonePercentage;
    public boolean stretchVideo, enableSops, playHostAudio, disableWarnings;
    public String language;
    public boolean listMode, smallIconMode;

    public static boolean getDefaultSmallMode(Context context) {
        // Use small mode on anything smaller than a 7" tablet
        return context.getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private static int getFpsInt(SharedPreferences prefs) {
        try {
            return Integer.parseInt(prefs.getString(FPS_PREF_STRING, DEFAULT_FPS));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_FPS);
        }
    }

    public static int getDefaultBitrate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long width = prefs.getInt(RESX_PREF_STRING, DEFAULT_RESX);
        long height = prefs.getInt(RESY_PREF_STRING, DEFAULT_RESY);

        long product = width * height * getFpsInt(prefs);
        if (product <= 1280 * 720 * 30) {
            return BITRATE_DEFAULT_720_30;
        }
        else if (product <= 1280 * 720 * 60) {
            return BITRATE_DEFAULT_720_60;
        }
        else if (product <= 1920 * 1080 * 30) {
            return BITRATE_DEFAULT_1080_30;
        }
        else if (product <= 1920 * 1080 * 60) {
            return BITRATE_DEFAULT_1080_60;
        }
        else {
            return BITRATE_DEFAULT_MAX;
        }
    }

    private static int getDecoderValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(DECODER_PREF_STRING, DEFAULT_DECODER);
        if (str.equals("auto")) {
            return AUTOSELECT_DECODER;
        }
        else if (str.equals("software")) {
            return FORCE_SOFTWARE_DECODER;
        }
        else if (str.equals("hardware")) {
            return FORCE_HARDWARE_DECODER;
        }
        else {
            // Should never get here
            return AUTOSELECT_DECODER;
        }
    }

    public static PreferenceConfiguration readPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration config = new PreferenceConfiguration();

        config.bitrate = prefs.getInt(BITRATE_PREF_STRING, getDefaultBitrate(context));
        config.width = prefs.getInt(RESX_PREF_STRING, DEFAULT_RESX);
        config.height = prefs.getInt(RESY_PREF_STRING, DEFAULT_RESY);
        config.fps = getFpsInt(prefs);

        config.decoder = getDecoderValue(context);

        config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE);

        config.language = prefs.getString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE);

        // Checkbox preferences
        config.disableWarnings = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS);
        config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS);
        config.stretchVideo = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH);
        config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO);
        config.listMode = prefs.getBoolean(LIST_MODE_PREF_STRING, DEFAULT_LIST_MODE);
        config.smallIconMode = prefs.getBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context));

        return config;
    }
}
