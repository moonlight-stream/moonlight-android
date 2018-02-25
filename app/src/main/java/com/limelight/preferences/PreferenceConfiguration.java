package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;

public class PreferenceConfiguration {
    static final String RES_FPS_PREF_STRING = "list_resolution_fps";
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
    private static final String BATTERY_SAVER_PREF_STRING = "checkbox_battery_saver";
    private static final String DISABLE_FRAME_DROP_PREF_STRING = "checkbox_disable_frame_drop";
    private static final String ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr";
    private static final String ENABLE_PIP_PREF_STRING = "checkbox_enable_pip";
    private static final String BIND_ALL_USB_STRING = "checkbox_usb_bind_all";
    private static final String MOUSE_EMULATION_STRING = "checkbox_mouse_emulation";

    private static final int BITRATE_DEFAULT_360_30 = 1000;
    private static final int BITRATE_DEFAULT_360_60 = 2000;
    private static final int BITRATE_DEFAULT_720_30 = 5000;
    private static final int BITRATE_DEFAULT_720_60 = 10000;
    private static final int BITRATE_DEFAULT_1080_30 = 10000;
    private static final int BITRATE_DEFAULT_1080_60 = 20000;
    private static final int BITRATE_DEFAULT_4K_30 = 40000;
    private static final int BITRATE_DEFAULT_4K_60 = 80000;

    private static final String DEFAULT_RES_FPS = "720p60";
    private static final int DEFAULT_BITRATE = BITRATE_DEFAULT_720_60;
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
    private static final boolean DEFAULT_BATTERY_SAVER = false;
    private static final boolean DEFAULT_DISABLE_FRAME_DROP = false;
    private static final boolean DEFAULT_ENABLE_HDR = false;
    private static final boolean DEFAULT_ENABLE_PIP = false;
    private static final boolean DEFAULT_BIND_ALL_USB = false;
    private static final boolean DEFAULT_MOUSE_EMULATION = true;

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
    public boolean batterySaver;
    public boolean disableFrameDrop;
    public boolean enableHdr;
    public boolean enablePip;
    public boolean bindAllUsb;
    public boolean mouseEmulation;

    public static int getDefaultBitrate(String resFpsString) {
        if (resFpsString.equals("360p30")) {
            return BITRATE_DEFAULT_360_30;
        }
        else if (resFpsString.equals("360p60")) {
            return BITRATE_DEFAULT_360_60;
        }
        else if (resFpsString.equals("720p30")) {
            return BITRATE_DEFAULT_720_30;
        }
        else if (resFpsString.equals("720p60")) {
            return BITRATE_DEFAULT_720_60;
        }
        else if (resFpsString.equals("1080p30")) {
            return BITRATE_DEFAULT_1080_30;
        }
        else if (resFpsString.equals("1080p60")) {
            return BITRATE_DEFAULT_1080_60;
        }
        else if (resFpsString.equals("4K30")) {
            return BITRATE_DEFAULT_4K_30;
        }
        else if (resFpsString.equals("4K60")) {
            return BITRATE_DEFAULT_4K_60;
        }
        else {
            // Should never get here
            return DEFAULT_BITRATE;
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
        return getDefaultBitrate(prefs.getString(RES_FPS_PREF_STRING, DEFAULT_RES_FPS));
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
                .remove(RES_FPS_PREF_STRING)
                .remove(VIDEO_FORMAT_PREF_STRING)
                .remove(ENABLE_HDR_PREF_STRING)
                .apply();
    }

    public static PreferenceConfiguration readPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration config = new PreferenceConfiguration();

        config.bitrate = prefs.getInt(BITRATE_PREF_STRING, prefs.getInt(BITRATE_PREF_OLD_STRING, 0) * 1000);
        if (config.bitrate == 0) {
            config.bitrate = getDefaultBitrate(context);
        }

        String str = prefs.getString(RES_FPS_PREF_STRING, DEFAULT_RES_FPS);
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
        config.batterySaver = prefs.getBoolean(BATTERY_SAVER_PREF_STRING, DEFAULT_BATTERY_SAVER);
        config.disableFrameDrop = prefs.getBoolean(DISABLE_FRAME_DROP_PREF_STRING, DEFAULT_DISABLE_FRAME_DROP);
        config.enableHdr = prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR);
        config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP);
        config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB);
        config.mouseEmulation = prefs.getBoolean(MOUSE_EMULATION_STRING, DEFAULT_MOUSE_EMULATION);

        return config;
    }
}
