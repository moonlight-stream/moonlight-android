package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;

public class PreferenceConfiguration {
    private static final String LEGACY_RES_FPS_PREF_STRING = "list_resolution_fps";


    static final String RESOLUTION_PREF_STRING = "list_resolution";
    static final String FPS_PREF_STRING = "list_fps";
    static final String BITRATE_PREF_STRING = "seekbar_bitrate_kbps";
    private static final String BITRATE_PREF_OLD_STRING = "seekbar_bitrate";
    private static final String STRETCH_PREF_STRING = "checkbox_stretch_video";
    private static final String SOPS_PREF_STRING = "checkbox_enable_sops";
    private static final String DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings";
    private static final String HOST_AUDIO_PREF_STRING = "checkbox_host_audio";
    private static final String DEADZONE_PREF_STRING = "seekbar_deadzone";
    private static final String LANGUAGE_PREF_STRING = "list_languages";
    private static final String LIST_MODE_PREF_STRING = "checkbox_list_mode";
    private static final String SMALL_ICONS_PREF_STRING = "checkbox_small_icon_mode";
    private static final String MULTI_CONTROLLER_PREF_STRING = "checkbox_multi_controller";
    private static final String ENABLE_51_SURROUND_PREF_STRING = "checkbox_51_surround";
    private static final String USB_DRIVER_PREF_SRING = "checkbox_usb_driver";
    private static final String VIDEO_FORMAT_PREF_STRING = "video_format";
    private static final String ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls";
    private static final String ONLY_L3_R3_PREF_STRING = "checkbox_only_show_L3R3";
    private static final String DISABLE_FRAME_DROP_PREF_STRING = "checkbox_disable_frame_drop";
    private static final String ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr";
    private static final String ENABLE_PIP_PREF_STRING = "checkbox_enable_pip";
    private static final String ENABLE_PERF_OVERLAY_STRING = "checkbox_enable_perf_overlay";
    private static final String BIND_ALL_USB_STRING = "checkbox_usb_bind_all";
    private static final String MOUSE_EMULATION_STRING = "checkbox_mouse_emulation";
    private static final String MOUSE_NAV_BUTTONS_STRING = "checkbox_mouse_nav_buttons";
    static final String UNLOCK_FPS_STRING = "checkbox_unlock_fps";
    private static final String VIBRATE_OSC_PREF_STRING = "checkbox_vibrate_osc";
    private static final String VIBRATE_FALLBACK_PREF_STRING = "checkbox_vibrate_fallback";

    static final String DEFAULT_RESOLUTION = "720p";
    static final String DEFAULT_FPS = "60";
    private static final boolean DEFAULT_STRETCH = false;
    private static final boolean DEFAULT_SOPS = true;
    private static final boolean DEFAULT_DISABLE_TOASTS = false;
    private static final boolean DEFAULT_HOST_AUDIO = false;
    private static final int DEFAULT_DEADZONE = 15;
    public static final String DEFAULT_LANGUAGE = "default";
    private static final boolean DEFAULT_LIST_MODE = false;
    private static final boolean DEFAULT_MULTI_CONTROLLER = true;
    private static final boolean DEFAULT_ENABLE_51_SURROUND = false;
    private static final boolean DEFAULT_USB_DRIVER = true;
    private static final String DEFAULT_VIDEO_FORMAT = "auto";
    private static final boolean ONSCREEN_CONTROLLER_DEFAULT = false;
    private static final boolean ONLY_L3_R3_DEFAULT = false;
    private static final boolean DEFAULT_DISABLE_FRAME_DROP = false;
    private static final boolean DEFAULT_ENABLE_HDR = false;
    private static final boolean DEFAULT_ENABLE_PIP = false;
    private static final boolean DEFAULT_ENABLE_PERF_OVERLAY = false;
    private static final boolean DEFAULT_BIND_ALL_USB = false;
    private static final boolean DEFAULT_MOUSE_EMULATION = true;
    private static final boolean DEFAULT_MOUSE_NAV_BUTTONS = false;
    private static final boolean DEFAULT_UNLOCK_FPS = false;
    private static final boolean DEFAULT_VIBRATE_OSC = true;
    private static final boolean DEFAULT_VIBRATE_FALLBACK = false;

    public static final int FORCE_H265_ON = -1;
    public static final int AUTOSELECT_H265 = 0;
    public static final int FORCE_H265_OFF = 1;

    public int width, height, fps;
    public int bitrate;
    public int videoFormat;
    public int deadzonePercentage;
    public boolean stretchVideo, enableSops, playHostAudio, disableWarnings;
    public String language;
    public boolean listMode, smallIconMode, multiController, enable51Surround, usbDriver;
    public boolean onscreenController;
    public boolean onlyL3R3;
    public boolean disableFrameDrop;
    public boolean enableHdr;
    public boolean enablePip;
    public boolean enablePerfOverlay;
    public boolean bindAllUsb;
    public boolean mouseEmulation;
    public boolean mouseNavButtons;
    public boolean unlockFps;
    public boolean vibrateOsc;
    public boolean vibrateFallbackToDevice;

    private static int getHeightFromResolutionString(String resString) {
        if (resString.equalsIgnoreCase("360p")) {
            return 360;
        }
        else if (resString.equalsIgnoreCase("480p")) {
            return 480;
        }
        else if (resString.equalsIgnoreCase("720p")) {
            return 720;
        }
        else if (resString.equalsIgnoreCase("1080p")) {
            return 1080;
        }
        else if (resString.equalsIgnoreCase("1440p")) {
            return 1440;
        }
        else if (resString.equalsIgnoreCase("4K")) {
            return 2160;
        }
        else {
            // Should be unreachable
            return 720;
        }
    }

    private static int getWidthFromResolutionString(String resString) {
        int height = getHeightFromResolutionString(resString);
        if (height == 480) {
            // This isn't an exact 16:9 resolution
            return 854;
        }
        else {
            return (height * 16) / 9;
        }
    }

    private static String getResolutionString(int width, int height) {
        switch (height) {
            case 360:
                return "360p";
            case 480:
                return "480p";
            default:
            case 720:
                return "720p";
            case 1080:
                return "1080p";
            case 1440:
                return "1440p";
            case 2160:
                return "4K";

        }
    }

    public static int getDefaultBitrate(String resString, String fpsString) {
        int width = getWidthFromResolutionString(resString);
        int height = getHeightFromResolutionString(resString);
        int fps = Integer.parseInt(fpsString);

        // This table prefers 16:10 resolutions because they are
        // only slightly more pixels than the 16:9 equivalents, so
        // we don't want to bump those 16:10 resolutions up to the
        // next 16:9 slot.
        //
        // This logic is shamelessly stolen from Moonlight Qt:
        // https://github.com/moonlight-stream/moonlight-qt/blob/master/app/settings/streamingpreferences.cpp

        if (width * height <= 640 * 360) {
            return (int)(1000 * (fps / 30.0));
        }
        else if (width * height <= 854 * 480) {
            return (int)(1500 * (fps / 30.0));
        }
        // This covers 1280x720 and 1280x800 too
        else if (width * height <= 1366 * 768) {
            return (int)(5000 * (fps / 30.0));
        }
        else if (width * height <= 1920 * 1200) {
            return (int)(10000 * (fps / 30.0));
        }
        else if (width * height <= 2560 * 1600) {
            return (int)(20000 * (fps / 30.0));
        }
        else /* if (width * height <= 3840 * 2160) */ {
            return (int)(40000 * (fps / 30.0));
        }
    }

    public static boolean getDefaultSmallMode(Context context) {
        PackageManager manager = context.getPackageManager();
        if (manager != null) {
            // TVs shouldn't use small mode by default
            if (manager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                return false;
            }

            // API 21 uses LEANBACK instead of TELEVISION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (manager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                    return false;
                }
            }
        }

        // Use small mode on anything smaller than a 7" tablet
        return context.getResources().getConfiguration().smallestScreenWidthDp < 500;
    }

    public static int getDefaultBitrate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return getDefaultBitrate(
                prefs.getString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION),
                prefs.getString(FPS_PREF_STRING, DEFAULT_FPS));
    }

    private static int getVideoFormatValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(VIDEO_FORMAT_PREF_STRING, DEFAULT_VIDEO_FORMAT);
        if (str.equals("auto")) {
            return AUTOSELECT_H265;
        }
        else if (str.equals("forceh265")) {
            return FORCE_H265_ON;
        }
        else if (str.equals("neverh265")) {
            return FORCE_H265_OFF;
        }
        else {
            // Should never get here
            return AUTOSELECT_H265;
        }
    }

    public static void resetStreamingSettings(Context context) {
        // We consider resolution, FPS, bitrate, HDR, and video format as "streaming settings" here
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .remove(BITRATE_PREF_STRING)
                .remove(BITRATE_PREF_OLD_STRING)
                .remove(LEGACY_RES_FPS_PREF_STRING)
                .remove(RESOLUTION_PREF_STRING)
                .remove(FPS_PREF_STRING)
                .remove(VIDEO_FORMAT_PREF_STRING)
                .remove(ENABLE_HDR_PREF_STRING)
                .remove(UNLOCK_FPS_STRING)
                .apply();
    }

    public static PreferenceConfiguration readPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration config = new PreferenceConfiguration();

        // Migrate legacy preferences to the new locations
        String str = prefs.getString(LEGACY_RES_FPS_PREF_STRING, null);
        if (str != null) {
            if (str.equals("360p30")) {
                config.width = 640;
                config.height = 360;
                config.fps = 30;
            }
            else if (str.equals("360p60")) {
                config.width = 640;
                config.height = 360;
                config.fps = 60;
            }
            else if (str.equals("720p30")) {
                config.width = 1280;
                config.height = 720;
                config.fps = 30;
            }
            else if (str.equals("720p60")) {
                config.width = 1280;
                config.height = 720;
                config.fps = 60;
            }
            else if (str.equals("1080p30")) {
                config.width = 1920;
                config.height = 1080;
                config.fps = 30;
            }
            else if (str.equals("1080p60")) {
                config.width = 1920;
                config.height = 1080;
                config.fps = 60;
            }
            else if (str.equals("4K30")) {
                config.width = 3840;
                config.height = 2160;
                config.fps = 30;
            }
            else if (str.equals("4K60")) {
                config.width = 3840;
                config.height = 2160;
                config.fps = 60;
            }
            else {
                // Should never get here
                config.width = 1280;
                config.height = 720;
                config.fps = 60;
            }

            prefs.edit()
                    .remove(LEGACY_RES_FPS_PREF_STRING)
                    .putString(RESOLUTION_PREF_STRING, getResolutionString(config.width, config.height))
                    .putString(FPS_PREF_STRING, ""+config.fps)
                    .apply();
        }
        else {
            // Use the new preference location
            String resStr = prefs.getString(RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            config.width = PreferenceConfiguration.getWidthFromResolutionString(resStr);
            config.height = PreferenceConfiguration.getHeightFromResolutionString(resStr);
            config.fps = Integer.parseInt(prefs.getString(FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS));
        }

        // This must happen after the preferences migration to ensure the preferences are populated
        config.bitrate = prefs.getInt(BITRATE_PREF_STRING, prefs.getInt(BITRATE_PREF_OLD_STRING, 0) * 1000);
        if (config.bitrate == 0) {
            config.bitrate = getDefaultBitrate(context);
        }

        config.videoFormat = getVideoFormatValue(context);

        config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE);

        config.language = prefs.getString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE);

        // Checkbox preferences
        config.disableWarnings = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS);
        config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS);
        config.stretchVideo = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH);
        config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO);
        config.listMode = prefs.getBoolean(LIST_MODE_PREF_STRING, DEFAULT_LIST_MODE);
        config.smallIconMode = prefs.getBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context));
        config.multiController = prefs.getBoolean(MULTI_CONTROLLER_PREF_STRING, DEFAULT_MULTI_CONTROLLER);
        config.enable51Surround = prefs.getBoolean(ENABLE_51_SURROUND_PREF_STRING, DEFAULT_ENABLE_51_SURROUND);
        config.usbDriver = prefs.getBoolean(USB_DRIVER_PREF_SRING, DEFAULT_USB_DRIVER);
        config.onscreenController = prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, ONSCREEN_CONTROLLER_DEFAULT);
        config.onlyL3R3 = prefs.getBoolean(ONLY_L3_R3_PREF_STRING, ONLY_L3_R3_DEFAULT);
        config.disableFrameDrop = prefs.getBoolean(DISABLE_FRAME_DROP_PREF_STRING, DEFAULT_DISABLE_FRAME_DROP);
        config.enableHdr = prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR);
        config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP);
        config.enablePerfOverlay = prefs.getBoolean(ENABLE_PERF_OVERLAY_STRING, DEFAULT_ENABLE_PERF_OVERLAY);
        config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB);
        config.mouseEmulation = prefs.getBoolean(MOUSE_EMULATION_STRING, DEFAULT_MOUSE_EMULATION);
        config.mouseNavButtons = prefs.getBoolean(MOUSE_NAV_BUTTONS_STRING, DEFAULT_MOUSE_NAV_BUTTONS);
        config.unlockFps = prefs.getBoolean(UNLOCK_FPS_STRING, DEFAULT_UNLOCK_FPS);
        config.vibrateOsc = prefs.getBoolean(VIBRATE_OSC_PREF_STRING, DEFAULT_VIBRATE_OSC);
        config.vibrateFallbackToDevice = prefs.getBoolean(VIBRATE_FALLBACK_PREF_STRING, DEFAULT_VIBRATE_FALLBACK);

        return config;
    }
}
