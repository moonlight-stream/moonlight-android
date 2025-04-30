package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Display;

import androidx.preference.PreferenceManager;

import com.limelight.nvstream.jni.MoonBridge;

public class PreferenceConfiguration {
    public enum ScaleMode {
        FIT,
        FILL,
        STRETCH
    }

    public enum FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264,
    };

    public enum AnalogStickForScrolling {
        NONE,
        RIGHT,
        LEFT
    }

    public static final String CUSTOM_BITRATE_PREF_STRING = "edit_diy_bitrate";
    public static final String CUSTOM_REFRESH_RATE_PREF_STRING = "custom_refresh_rate";
    public static final String CUSTOM_RESOLUTION_PREF_STRING = "edit_diy_w_h";

    private static final String LEGACY_RES_FPS_PREF_STRING = "list_resolution_fps";
    private static final String LEGACY_ENABLE_51_SURROUND_PREF_STRING = "checkbox_51_surround";
    private static final String LEGACY_STRETCH_PREF_STRING = "checkbox_stretch_video";
    private static final String LEGACY_ENFORCE_REFRESH_RATE_STRING = "checkbox_enforce_refresh_rate";

    static final String RESOLUTION_PREF_STRING = "list_resolution";
    static final String FPS_PREF_STRING = "list_fps";
    static final String BITRATE_PREF_STRING = "seekbar_bitrate_kbps";
    private static final String BITRATE_PREF_OLD_STRING = "seekbar_bitrate";
    private static final String METERED_BITRATE_PREF_STRING = "seekbar_metered_bitrate_kbps";
    private static final String ENABLE_ULTRA_LOW_LATENCY_PREF_STRING = "checkbox_ultra_low_latency";
    private static final String ENFORCE_DISPLAY_MODE_PREF_STRING = "checkbox_enforce_display_mode";
    private static final String USE_VIRTUAL_DISPLAY_PREF_STRING = "checkbox_use_virtual_display";
    private static final String AUTO_INVERT_VIDEO_RESOLUTION_PREF_STRING = "checkbox_auto_invert_video_resolution";
    private static final String RESOLUTION_SCALE_FACTOR_PREF_STRING = "seekbar_resolution_scale_factor";
    private static final String RESUME_WITHOUT_CONFIRM_PREF_STRING = "checkbox_resume_without_confirm";
    private static final String VIDEO_SCALE_MODE_PREF_STRING = "list_video_scale_mode";
    private static final String SOPS_PREF_STRING = "checkbox_enable_sops";
    private static final String DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings";
    private static final String HOST_AUDIO_PREF_STRING = "checkbox_host_audio";
    private static final String DEADZONE_PREF_STRING = "seekbar_deadzone";
    private static final String OSC_OPACITY_PREF_STRING = "seekbar_osc_opacity";
    private static final String LANGUAGE_PREF_STRING = "list_languages";
    private static final String SMALL_ICONS_PREF_STRING = "checkbox_small_icon_mode";
    private static final String MULTI_CONTROLLER_PREF_STRING = "checkbox_multi_controller";
    static final String AUDIO_CONFIG_PREF_STRING = "list_audio_config";
    private static final String USB_DRIVER_PREF_SRING = "checkbox_usb_driver";
    private static final String VIDEO_FORMAT_PREF_STRING = "video_format";
    private static final String ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls";
    private static final String CHECKBOX_HIDE_OSC_WHEN_HAS_GAMEPAD = "checkbox_hide_osc_when_has_gamepad";
    private static final String ONLY_L3_R3_PREF_STRING = "checkbox_only_show_L3R3";
    private static final String SHOW_GUIDE_BUTTON_PREF_STRING = "checkbox_show_guide_button";
    private static final String LEGACY_DISABLE_FRAME_DROP_PREF_STRING = "checkbox_disable_frame_drop";
    private static final String ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr";
    private static final String ENABLE_PIP_PREF_STRING = "checkbox_enable_pip";
    private static final String ENABLE_PERF_OVERLAY_STRING = "checkbox_enable_perf_overlay";
    private static final String BIND_ALL_USB_STRING = "checkbox_usb_bind_all";
    private static final String MOUSE_EMULATION_STRING = "checkbox_mouse_emulation";
    private static final String ANALOG_SCROLLING_PREF_STRING = "analog_scrolling";
    private static final String MOUSE_NAV_BUTTONS_STRING = "checkbox_mouse_nav_buttons";
    static final String UNLOCK_FPS_STRING = "checkbox_unlock_fps";
    private static final String VIBRATE_OSC_PREF_STRING = "checkbox_vibrate_osc";
    private static final String VIBRATE_FALLBACK_PREF_STRING = "checkbox_vibrate_fallback";
    private static final String VIBRATE_FALLBACK_STRENGTH_PREF_STRING = "seekbar_vibrate_fallback_strength";
    private static final String FLIP_FACE_BUTTONS_PREF_STRING = "checkbox_flip_face_buttons";
//    static final String TOUCHSCREEN_TRACKPAD_PREF_STRING = "checkbox_touchscreen_trackpad";
    private static final String LATENCY_TOAST_PREF_STRING = "checkbox_enable_post_stream_toast";
    private static final String FRAME_PACING_PREF_STRING = "frame_pacing";
    private static final String ABSOLUTE_MOUSE_MODE_PREF_STRING = "checkbox_absolute_mouse_mode";
    private static final String ENABLE_AUDIO_FX_PREF_STRING = "checkbox_enable_audiofx";
    private static final String REDUCE_REFRESH_RATE_PREF_STRING = "checkbox_reduce_refresh_rate";
    private static final String FULL_RANGE_PREF_STRING = "checkbox_full_range";
    private static final String GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING = "checkbox_gamepad_touchpad_as_mouse";
    private static final String GAMEPAD_MOTION_SENSORS_PREF_STRING = "checkbox_gamepad_motion_sensors";
    private static final String GAMEPAD_MOTION_FALLBACK_PREF_STRING = "checkbox_gamepad_motion_fallback";
    private static final String FORCE_MOTION_SENSORS_FALLBACK_PREF_STRING = "checkbox_force_device_motion";

    private static final String LIST_ONSCREEN_KEYBOARD_ALIGN_MODE = "list_onscreen_keyboard_align_mode";

    private static final String CHECKBOX_ENABLE_BATTERY_REPORT = "checkbox_gamepad_enable_battery_report";
    private static final String CHECKBOX_FORCE_QWERTY = "checkbox_force_qwerty";
    private static final String CHECKBOX_BACK_AS_META = "checkbox_back_as_meta";
    private static final String CHECKBOX_IGNORE_SYNTH_EVENTS = "checkbox_ignore_synth_events";
    private static final String CHECKBOX_BACK_AS_GUIDE = "checkbox_back_as_guide";
    private static final String CHECKBOX_SMART_CLIPBOARD_SYNC = "checkbox_smart_clipboard_sync";
    private static final String CHECKBOX_SMART_CLIPBOARD_SYNC_TOAST = "checkbox_smart_clipboard_sync_toast";
    private static final String CHECKBOX_HIDE_CLIPBOARD_CONTENT = "checkbox_hide_clipboard_content";

    private static final String CHECKBOX_ENABLE_STICKY_MODIFIER_KEY_VIRTUAL_KEYBOARD = "checkbox_enable_sticky_modifier_key_virtual_keyboard";

    //是否弹出软键盘
    private static final String CHECKBOX_ENABLE_QUIT_DIALOG = "checkbox_enable_quit_dialog";

    //竖屏模式
    private static final String CHECKBOX_AUTO_ORIENTATION = "checkbox_auto_orientation";
    //屏幕特殊按键
    private static final String CHECKBOX_ENABLE_KEYBOARD = "checkbox_enable_keyboard";

    //屏幕特殊按键 震动
    private static final String CHECKBOX_ENABLE_KEYBOARD_VIBRATE = "checkbox_vibrate_keyboard";

    //自动摇杆
    private static final String CHECKBOX_CHECKBOX_ENABLE_ANALOG_STICK_NEW = "checkbox_enable_analog_stick_new";

    //触控屏幕灵敏度
    private static final String SEEKBAR_TOUCH_SENSITIVITY = "seekbar_touch_sensitivity_opacity_x";
    private static final String SEEKBAR_TRACKPAD_SENSITIVITY_X = "seekbar_trackpad_sensitivity_x";
    private static final String SEEKBAR_TRACKPAD_SENSITIVITY_Y = "seekbar_trackpad_sensitivity_y";
    private static final String CHECKBOX_TRACKPAD_DRAG_DROP_VIBRATION = "checkbox_trackpad_drag_drop_vibration";
    private static final String SEEKBAR_TRACKPAD_DRAG_DROP_THRESHOLD = "seekbar_trackpad_drag_drop_threshold";
    private static final String CHECKBOX_TRACKPAD_SWAP_AXIS = "checkbox_trackpad_swap_axis";

    static final String DEFAULT_RESOLUTION = "1280x720";
    static final String DEFAULT_FPS = "60";
    private static final boolean DEFAULT_ENABLE_ULTRA_LOW_LATENCY = false;
    private static final boolean DEFAULT_ENFORCE_DISPLAY_MODE = false;
    private static final boolean DEFAULT_USE_VIRTUAL_DISPLAY = false;
    private static final String DEFAULT_VIDEO_SCALE_MODE = "fit";
    private static final boolean DEFAULT_AUTO_INVERT_VIDEO_RESOLUTION = true;
    private static final int DEFAULT_RESOLUTION_SCALE_FACTOR = 100;
    private static final boolean DEFAULT_RESUME_WITHOUT_CONFIRM = false;
    private static final boolean DEFAULT_SOPS = true;
    private static final boolean DEFAULT_DISABLE_TOASTS = false;
    private static final boolean DEFAULT_HOST_AUDIO = false;
    private static final int DEFAULT_DEADZONE = 5;
    private static final int DEFAULT_OPACITY = 90;
    public static final String DEFAULT_LANGUAGE = "default";
    private static final boolean DEFAULT_MULTI_CONTROLLER = true;
    private static final boolean DEFAULT_USB_DRIVER = true;
    private static final String DEFAULT_VIDEO_FORMAT = "auto";

    private static final boolean DEFAULT_ONSCREEN_CONTROLLER = false;
    private static final boolean DEFAULT_HIDE_OSC_WHEN_HAS_GAMEPAD = true;
    private static final boolean ONLY_L3_R3_DEFAULT = false;
    private static final boolean SHOW_GUIDE_BUTTON_DEFAULT = true;
    private static final boolean DEFAULT_ENABLE_HDR = false;
    private static final boolean DEFAULT_ENABLE_PIP = false;
    private static final boolean DEFAULT_ENABLE_PERF_OVERLAY = false;
    private static final boolean DEFAULT_BIND_ALL_USB = false;
    private static final boolean DEFAULT_MOUSE_EMULATION = true;
    private static final String DEFAULT_ANALOG_STICK_FOR_SCROLLING = "right";
    private static final boolean DEFAULT_MOUSE_NAV_BUTTONS = false;
    private static final boolean DEFAULT_UNLOCK_FPS = false;
    private static final boolean DEFAULT_VIBRATE_OSC = true;
    private static final boolean DEFAULT_VIBRATE_FALLBACK = false;
    private static final int DEFAULT_VIBRATE_FALLBACK_STRENGTH = 100;
    private static final boolean DEFAULT_FLIP_FACE_BUTTONS = false;
    private static final boolean DEFAULT_TOUCHSCREEN_TRACKPAD = true;
    private static final String DEFAULT_AUDIO_CONFIG = "2"; // Stereo
    private static final boolean DEFAULT_LATENCY_TOAST = false;
    private static final String DEFAULT_FRAME_PACING = "latency";
    private static final boolean DEFAULT_ABSOLUTE_MOUSE_MODE = false;
    private static final boolean DEFAULT_ENABLE_AUDIO_FX = false;
    private static final boolean DEFAULT_REDUCE_REFRESH_RATE = false;
    private static final boolean DEFAULT_FULL_RANGE = false;
    private static final boolean DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE = false;
    private static final boolean DEFAULT_GAMEPAD_MOTION_SENSORS = true;
    private static final boolean DEFAULT_GAMEPAD_MOTION_FALLBACK = false;
    private static final boolean DEFAULT_FORCE_MOTION_SENSORS_FALLBACK = false;
    private static final boolean DEFAULT_GAMEPAD_ENABLE_BATTERY_REPORT = true;
    private static final boolean DEFAULT_FORCE_QWERTY = true;
    private static final boolean DEFAULT_SEND_META_ON_PHYSICAL_BACK = false;
    private static final boolean DEFAULT_IGNORE_SYNTH_EVENTS = false;
    private static final boolean DEFAULT_BACK_AS_GUIDE = false;
    private static final boolean DEFAULT_SMART_CLIPBOARD_SYNC = false;
    private static final boolean DEFAULT_SMART_CLIPBOARD_SYNC_TOAST = true;
    private static final boolean DEFAULT_HIDE_CLIPBOARD_CONTENT = true;
    private static final boolean DEFAULT_ENABLE_STICKY_MODIFIER_KEY_VIRTUAL_KEYBOARD = true;
    private static final int DEFAULT_TRACKPAD_SENSITIVITY_X = 100;
    private static final int DEFAULT_TRACKPAD_SENSITIVITY_Y = 100;
    private static final boolean DEFAULT_TRACKPAD_DRAG_DROP_VIBRATION = false;
    private static final int DEFAULT_TRACKPAD_DRAG_DROP_THRESHOLD = 250;
    private static final boolean DEFAULT_TRACKPAD_SWAP_AXIS = false;
    private static final String DEFAULT_ONSCREEN_KEYBOARD_ALIGN_MODE = "center";

    public static final int FRAME_PACING_MIN_LATENCY = 0;
    public static final int FRAME_PACING_BALANCED = 1;
    public static final int FRAME_PACING_CAP_FPS = 2;
    public static final int FRAME_PACING_MAX_SMOOTHNESS = 3;

    public static final String RES_360P = "640x360";
    public static final String RES_480P = "854x480";
    public static final String RES_720P = "1280x720";
    public static final String RES_1080P = "1920x1080";
    public static final String RES_1440P = "2560x1440";
    public static final String RES_4K = "3840x2160";
    public static final String RES_NATIVE = "Native";

    public int width, height, bitrate;
    public float fps;
//    public String customBitrate;
    public boolean enableUltraLowLatency;
    public String customResolution;
    public String customRefreshRate;
    public int meteredBitrate;
    public FormatOption videoFormat;
    public int framePacingWarpFactor = 0;
    public int deadzonePercentage;
    public int oscOpacity;
    public int oscKeyboardOpacity;
    public int onscreenKeyboardHeight;
    public int onscreenKeyboardWidth;
    public String onscreenKeyboardAlignMode;
    public boolean enforceDisplayMode, useVirtualDisplay, enableSops, playHostAudio, disableWarnings;
    public ScaleMode videoScaleMode;
    public String language;
    public boolean smallIconMode, multiController, usbDriver, flipFaceButtons;
    public boolean onscreenController;
    public boolean hideOSCWhenHasGamepad;
    public boolean enableBatteryReport;
    public boolean forceQwerty;
    public boolean backAsMeta;
    public boolean ignoreSynthEvents;
    public boolean backAsGuide;
    public boolean smartClipboardSync;
    public boolean smartClipboardSyncToast;
    public boolean hideClipboardContent;
    public boolean stickyModifierKey;
    public boolean onlyL3R3;
    public boolean showGuideButton;
    public boolean enableHdr;
    public boolean enablePip;
    public boolean enablePerfOverlay;
    //简化版性能信息
    public boolean enablePerfOverlayLite;

    public boolean enablePerfOverlayLiteDialog;

    public boolean enableLatencyToast;
    //软键盘
    public boolean enableBackMenu;
    //Invert video width/height
    public boolean autoInvertVideoResolution;
    public int resolutionScaleFactor;
    public boolean resumeWithoutConfirm;
    //竖屏模式
    public boolean autoOrientation;
    //虚拟屏幕键盘按键
    public boolean enableKeyboard;
    //修复JoyCon十字键
    public boolean enableJoyConFix;

    //自由摇杆啊
    public boolean enableNewAnalogStick;

    public boolean enableExDisplay;

    //串流画面顶部居中显示
    public boolean alignDisplayTopCenter;

    //触控屏幕灵敏度
    public int touchSensitivityX;
    public int touchSensitivityY;
    //超出边界自动回中心点
    public boolean touchSensitivityRotationAuto;

    //触控灵敏度调节范围
    public boolean touchSensitivityGlobal;

    //多点触控灵敏度调节
    public boolean enableTouchSensitivity;

    //触控板模式灵敏度
    public int touchPadSensitivity;

    public int touchPadYSensitity;

    //多点触控模式
    public boolean enableMultiTouchScreen;

    //物理光标捕获
    public boolean enableMouseLocalCursor;

    //禁用内置的特殊指令
    public boolean enableClearDefaultSpecial;

    //强制使用设备自身的震动马达
    public boolean enableDeviceRumble;

    public boolean enableKeyboardVibrate;

    public boolean enableKeyboardSquare;

    //官方虚拟按钮风格
    public boolean enableOnScreenStyleOfficial;

    //自由摇杆背景透明度
    public int enableNewAnalogStickOpacity;

    public int trackpadSensitivityX;
    public int trackpadSensitivityY;
    public boolean trackpadDragDropVibration;
    public int trackpadDragDropThreshold;
    public boolean trackpadSwapAxis;

    public boolean bindAllUsb;
    public boolean mouseEmulation;
    public AnalogStickForScrolling analogStickForScrolling;
    public boolean mouseNavButtons;
    public boolean unlockFps;
    public boolean vibrateOsc;
    public boolean vibrateFallbackToDevice;
    public int vibrateFallbackToDeviceStrength;
    public boolean touchscreenTrackpad;
    public MoonBridge.AudioConfiguration audioConfiguration;
    public int framePacing;
    public boolean absoluteMouseMode;
    public boolean enableAudioFx;
    public boolean reduceRefreshRate;
    public boolean fullRange;
    public boolean gamepadMotionSensors;
    public boolean gamepadTouchpadAsMouse;
    public boolean gamepadMotionSensorsFallbackToDevice;
    public boolean forceMotionSensorsFallbackToDevice;

    public static boolean isNativeResolution(int width, int height) {
        // It's not a native resolution if it matches an existing resolution option
        if (width == 640 && height == 360) {
            return false;
        }
        else if (width == 854 && height == 480) {
            return false;
        }
        else if (width == 1280 && height == 720) {
            return false;
        }
        else if (width == 1920 && height == 1080) {
            return false;
        }
        else if (width == 2560 && height == 1440) {
            return false;
        }
        else if (width == 3840 && height == 2160) {
            return false;
        }

        return true;
    }

    // If we have a screen that has semi-square dimensions, we may want to change our behavior
    // to allow any orientation and vertical+horizontal resolutions.
    public static boolean isSquarishScreen(int width, int height) {
        float longDim = Math.max(width, height);
        float shortDim = Math.min(width, height);

        // We just put the arbitrary cutoff for a square-ish screen at 1.3
        return longDim / shortDim < 1.3f;
    }

    public static boolean isSquarishScreen(Display display) {
        int width, height;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            width = display.getMode().getPhysicalWidth();
            height = display.getMode().getPhysicalHeight();
        }
        else {
            width = display.getWidth();
            height = display.getHeight();
        }

        return isSquarishScreen(width, height);
    }

    private static String convertFromLegacyResolutionString(String resString) {
        if (resString.equalsIgnoreCase("360p")) {
            return RES_360P;
        }
        else if (resString.equalsIgnoreCase("480p")) {
            return RES_480P;
        }
        else if (resString.equalsIgnoreCase("720p")) {
            return RES_720P;
        }
        else if (resString.equalsIgnoreCase("1080p")) {
            return RES_1080P;
        }
        else if (resString.equalsIgnoreCase("1440p")) {
            return RES_1440P;
        }
        else if (resString.equalsIgnoreCase("4K")) {
            return RES_4K;
        }
        else {
            // Should be unreachable
            return RES_720P;
        }
    }

    private static int getWidthFromResolutionString(String resString) {
        return Integer.parseInt(resString.split("x")[0]);
    }

    private static int getHeightFromResolutionString(String resString) {
        return Integer.parseInt(resString.split("x")[1]);
    }

    private static String getResolutionString(int width, int height) {
        switch (height) {
            case 360:
                return RES_360P;
            case 480:
                return RES_480P;
            default:
            case 720:
                return RES_720P;
            case 1080:
                return RES_1080P;
            case 1440:
                return RES_1440P;
            case 2160:
                return RES_4K;
        }
    }

    public static int getDefaultBitrate(String resString, String fpsString) {
        int width = getWidthFromResolutionString(resString);
        int height = getHeightFromResolutionString(resString);
        int fps = Math.round(Float.parseFloat(fpsString));

        // This logic is shamelessly stolen from Moonlight Qt:
        // https://github.com/moonlight-stream/moonlight-qt/blob/master/app/settings/streamingpreferences.cpp

        // Don't scale bitrate linearly beyond 60 FPS. It's definitely not a linear
        // bitrate increase for frame rate once we get to values that high.
        double frameRateFactor = (fps <= 60 ? fps : (Math.sqrt(fps / 60.f) * 60.f)) / 30.f;

        // TODO: Collect some empirical data to see if these defaults make sense.
        // We're just using the values that the Shield used, as we have for years.
        int[] pixelVals = {
            640 * 360,
            854 * 480,
            1280 * 720,
            1920 * 1080,
            2560 * 1440,
            3840 * 2160,
            -1,
        };
        int[] factorVals = {
            1,
            2,
            5,
            10,
            20,
            40,
            -1
        };

        // Calculate the resolution factor by linear interpolation of the resolution table
        float resolutionFactor;
        int pixels = width * height;
        for (int i = 0; ; i++) {
            if (pixels == pixelVals[i]) {
                // We can bail immediately for exact matches
                resolutionFactor = factorVals[i];
                break;
            }
            else if (pixels < pixelVals[i]) {
                if (i == 0) {
                    // Never go below the lowest resolution entry
                    resolutionFactor = factorVals[i];
                }
                else {
                    // Interpolate between the entry greater than the chosen resolution (i) and the entry less than the chosen resolution (i-1)
                    resolutionFactor = ((float)(pixels - pixelVals[i-1]) / (pixelVals[i] - pixelVals[i-1])) * (factorVals[i] - factorVals[i-1]) + factorVals[i-1];
                }
                break;
            }
            else if (pixelVals[i] == -1) {
                // Never go above the highest resolution entry
                resolutionFactor = factorVals[i-1];
                break;
            }
        }

        return (int)Math.round(resolutionFactor * frameRateFactor) * 1000;
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

    private static FormatOption getVideoFormatValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(VIDEO_FORMAT_PREF_STRING, DEFAULT_VIDEO_FORMAT);
        if (str.equals("auto")) {
            return FormatOption.AUTO;
        }
        else if (str.equals("forceav1")) {
            return FormatOption.FORCE_AV1;
        }
        else if (str.equals("forceh265")) {
            return FormatOption.FORCE_HEVC;
        }
        else if (str.equals("neverh265")) {
            return FormatOption.FORCE_H264;
        }
        else {
            // Should never get here
            return FormatOption.AUTO;
        }
    }

    private static ScaleMode getVideoScaleMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(VIDEO_SCALE_MODE_PREF_STRING, DEFAULT_VIDEO_SCALE_MODE);
        if (str.equals("fit")) {
            return ScaleMode.FIT;
        }
        else if (str.equals("fill")) {
            return ScaleMode.FILL;
        }
        else if (str.equals("stretch")) {
            return ScaleMode.STRETCH;
        }
        else {
            // Should never get here
            return ScaleMode.FIT;
        }
    }

    private static int getFramePacingValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Migrate legacy never drop frames option to the new location
        if (prefs.contains(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)) {
            boolean legacyNeverDropFrames = prefs.getBoolean(LEGACY_DISABLE_FRAME_DROP_PREF_STRING, false);
            prefs.edit()
                    .remove(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)
                    .putString(FRAME_PACING_PREF_STRING, legacyNeverDropFrames ? "balanced" : "latency")
                    .apply();
        }

        String str = prefs.getString(FRAME_PACING_PREF_STRING, DEFAULT_FRAME_PACING);
        if (str.equals("latency")) {
            return FRAME_PACING_MIN_LATENCY;
        }
        else if (str.equals("balanced")) {
            return FRAME_PACING_BALANCED;
        }
        else if (str.equals("cap-fps")) {
            return FRAME_PACING_CAP_FPS;
        }
        else if (str.equals("smoothness")) {
            return FRAME_PACING_MAX_SMOOTHNESS;
        }
        else {
            // Should never get here
            return FRAME_PACING_MIN_LATENCY;
        }
    }

    private static AnalogStickForScrolling getAnalogStickForScrollingValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(ANALOG_SCROLLING_PREF_STRING, DEFAULT_ANALOG_STICK_FOR_SCROLLING);
        if (str.equals("right")) {
            return AnalogStickForScrolling.RIGHT;
        }
        else if (str.equals("left")) {
            return AnalogStickForScrolling.LEFT;
        }
        else {
            return AnalogStickForScrolling.NONE;
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
                .remove(FULL_RANGE_PREF_STRING)
                .apply();
    }

//    public static void completeLanguagePreferenceMigration(Context context) {
//        // Put our language option back to default which tells us that we've already migrated it
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
//        prefs.edit().putString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE).apply();
//    }

    public static boolean isShieldAtvFirmwareWithBrokenHdr() {
        // This particular Shield TV firmware crashes when using HDR
        // https://www.nvidia.com/en-us/geforce/forums/notifications/comment/155192/
        return Build.MANUFACTURER.equalsIgnoreCase("NVIDIA") &&
                Build.FINGERPRINT.contains("PPR1.180610.011/4079208_2235.1395");
    }

    public static PreferenceConfiguration readPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration config = new PreferenceConfiguration();

        // Migrate legacy preferences to the new locations
        if (prefs.contains(LEGACY_ENABLE_51_SURROUND_PREF_STRING)) {
            if (prefs.getBoolean(LEGACY_ENABLE_51_SURROUND_PREF_STRING, false)) {
                prefs.edit()
                        .remove(LEGACY_ENABLE_51_SURROUND_PREF_STRING)
                        .putString(AUDIO_CONFIG_PREF_STRING, "51")
                        .apply();
            }
        }

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

            // Convert legacy resolution strings to the new style
            if (!resStr.contains("x")) {
                resStr = PreferenceConfiguration.convertFromLegacyResolutionString(resStr);
                prefs.edit().putString(RESOLUTION_PREF_STRING, resStr).apply();
            }

            config.width = PreferenceConfiguration.getWidthFromResolutionString(resStr);
            config.height = PreferenceConfiguration.getHeightFromResolutionString(resStr);
            config.fps = Float.parseFloat(prefs.getString(FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS));
        }

        if (prefs.contains(LEGACY_STRETCH_PREF_STRING)) {
            boolean stretch = prefs.getBoolean(LEGACY_STRETCH_PREF_STRING, false);
            prefs.edit()
                    .remove(LEGACY_STRETCH_PREF_STRING)
                    .putString(VIDEO_SCALE_MODE_PREF_STRING, stretch ? "stretch" : "fit")
                    .apply();
        }

        if (prefs.contains(LEGACY_ENFORCE_REFRESH_RATE_STRING)) {
            boolean enforce = prefs.getBoolean(LEGACY_ENFORCE_REFRESH_RATE_STRING, false);
            prefs.edit()
                    .remove(LEGACY_ENFORCE_REFRESH_RATE_STRING)
                    .putBoolean(ENFORCE_DISPLAY_MODE_PREF_STRING, enforce)
                    .apply();
        }

        if (!prefs.contains(SMALL_ICONS_PREF_STRING)) {
            // We need to write small icon mode's default to disk for the settings page to display
            // the current state of the option properly
            prefs.edit().putBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context)).apply();
        }

        if (!prefs.contains(GAMEPAD_MOTION_SENSORS_PREF_STRING) && Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            // Android 12 has a nasty bug that causes crashes when the app touches the InputDevice's
            // associated InputDeviceSensorManager (just calling getSensorManager() is enough).
            // As a workaround, we will override the default value for the gamepad motion sensor
            // option to disabled on Android 12 to reduce the impact of this bug.
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
            prefs.edit().putBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, false).apply();
        }

        // This must happen after the preferences migration to ensure the preferences are populated
        config.bitrate = prefs.getInt(BITRATE_PREF_STRING, prefs.getInt(BITRATE_PREF_OLD_STRING, 0) * 1000);
        if (config.bitrate == 0) {
            config.bitrate = getDefaultBitrate(context);
        }

        config.meteredBitrate = prefs.getInt((METERED_BITRATE_PREF_STRING), 0);
        if (config.meteredBitrate == 0) {
            config.meteredBitrate = config.bitrate / 4;
            prefs.edit().putInt(METERED_BITRATE_PREF_STRING, 0).apply();
        }

        String audioConfig = prefs.getString(AUDIO_CONFIG_PREF_STRING, DEFAULT_AUDIO_CONFIG);
        if (audioConfig.equals("71")) {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_71_SURROUND;
        }
        else if (audioConfig.equals("51")) {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_51_SURROUND;
        }
        else /* if (audioConfig.equals("2")) */ {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO;
        }

        config.videoScaleMode = getVideoScaleMode(context);

        config.videoFormat = getVideoFormatValue(context);
        config.framePacing = getFramePacingValue(context);

        String warpFactorStr = prefs.getString(FRAME_PACING_PREF_STRING, "");
        if (warpFactorStr.equals("warp")) {
            config.framePacingWarpFactor = 2;
        } else if (warpFactorStr.equals("warp2")) {
            config.framePacingWarpFactor = 4;
        }

        config.analogStickForScrolling = getAnalogStickForScrollingValue(context);

        config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE);

        config.oscOpacity = prefs.getInt(OSC_OPACITY_PREF_STRING, DEFAULT_OPACITY);

        config.language = prefs.getString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE);

        // Checkbox preferences
        config.disableWarnings = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS);
        config.enforceDisplayMode = prefs.getBoolean(ENFORCE_DISPLAY_MODE_PREF_STRING, DEFAULT_ENFORCE_DISPLAY_MODE);
        config.useVirtualDisplay = prefs.getBoolean(USE_VIRTUAL_DISPLAY_PREF_STRING, DEFAULT_USE_VIRTUAL_DISPLAY);
        config.enableUltraLowLatency = prefs.getBoolean(ENABLE_ULTRA_LOW_LATENCY_PREF_STRING, DEFAULT_ENABLE_ULTRA_LOW_LATENCY);
        config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS);
        config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO);
        config.smallIconMode = prefs.getBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context));
        config.multiController = prefs.getBoolean(MULTI_CONTROLLER_PREF_STRING, DEFAULT_MULTI_CONTROLLER);
        config.usbDriver = prefs.getBoolean(USB_DRIVER_PREF_SRING, DEFAULT_USB_DRIVER);
        config.onscreenController = prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, DEFAULT_ONSCREEN_CONTROLLER);
        config.hideOSCWhenHasGamepad = prefs.getBoolean(CHECKBOX_HIDE_OSC_WHEN_HAS_GAMEPAD, DEFAULT_HIDE_OSC_WHEN_HAS_GAMEPAD);
        config.onlyL3R3 = prefs.getBoolean(ONLY_L3_R3_PREF_STRING, ONLY_L3_R3_DEFAULT);
        config.showGuideButton = prefs.getBoolean(SHOW_GUIDE_BUTTON_PREF_STRING, SHOW_GUIDE_BUTTON_DEFAULT);
        config.enableHdr = prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR) && !isShieldAtvFirmwareWithBrokenHdr();
        config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP);
        config.enablePerfOverlay = prefs.getBoolean(ENABLE_PERF_OVERLAY_STRING, DEFAULT_ENABLE_PERF_OVERLAY);
        config.enablePerfOverlayLite=prefs.getBoolean("checkbox_enable_perf_overlay_lite",DEFAULT_ENABLE_PERF_OVERLAY);
        config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB);
        config.mouseEmulation = prefs.getBoolean(MOUSE_EMULATION_STRING, DEFAULT_MOUSE_EMULATION);
        config.mouseNavButtons = prefs.getBoolean(MOUSE_NAV_BUTTONS_STRING, DEFAULT_MOUSE_NAV_BUTTONS);
        config.unlockFps = prefs.getBoolean(UNLOCK_FPS_STRING, DEFAULT_UNLOCK_FPS);
        config.vibrateOsc = prefs.getBoolean(VIBRATE_OSC_PREF_STRING, DEFAULT_VIBRATE_OSC);
        config.vibrateFallbackToDevice = prefs.getBoolean(VIBRATE_FALLBACK_PREF_STRING, DEFAULT_VIBRATE_FALLBACK);
        config.vibrateFallbackToDeviceStrength = prefs.getInt(VIBRATE_FALLBACK_STRENGTH_PREF_STRING, DEFAULT_VIBRATE_FALLBACK_STRENGTH);
        config.flipFaceButtons = prefs.getBoolean(FLIP_FACE_BUTTONS_PREF_STRING, DEFAULT_FLIP_FACE_BUTTONS);
//        config.touchscreenTrackpad = prefs.getBoolean(TOUCHSCREEN_TRACKPAD_PREF_STRING, DEFAULT_TOUCHSCREEN_TRACKPAD);
        config.enableLatencyToast = prefs.getBoolean(LATENCY_TOAST_PREF_STRING, DEFAULT_LATENCY_TOAST);
        //软键盘
        config.enableBackMenu = prefs.getBoolean(CHECKBOX_ENABLE_QUIT_DIALOG,false);
        config.autoOrientation = prefs.getBoolean(CHECKBOX_AUTO_ORIENTATION,false);
        config.autoInvertVideoResolution = prefs.getBoolean(AUTO_INVERT_VIDEO_RESOLUTION_PREF_STRING, DEFAULT_AUTO_INVERT_VIDEO_RESOLUTION);
        config.resolutionScaleFactor = prefs.getInt(RESOLUTION_SCALE_FACTOR_PREF_STRING, DEFAULT_RESOLUTION_SCALE_FACTOR);

        config.resumeWithoutConfirm = prefs.getBoolean(RESUME_WITHOUT_CONFIRM_PREF_STRING, DEFAULT_RESUME_WITHOUT_CONFIRM);

        config.enableKeyboard = prefs.getBoolean(CHECKBOX_ENABLE_KEYBOARD,false);

        config.enableKeyboardVibrate = prefs.getBoolean(CHECKBOX_ENABLE_KEYBOARD_VIBRATE,false);
        //兼容joycon手柄
        config.enableJoyConFix = prefs.getBoolean("checkbox_enable_joyconfix",false);
        //全键盘透明度
        config.oscKeyboardOpacity = prefs.getInt("seekbar_keyboard_axi_opacity",DEFAULT_OPACITY);

        config.enableOnScreenStyleOfficial = prefs.getBoolean("checkbox_onscreen_style_official",false);

        config.enableNewAnalogStickOpacity = prefs.getInt("seekbar_osc_free_analog_stick_opacity",20);

        config.onscreenKeyboardHeight = prefs.getInt("seekbar_onscreen_keyboard_height",200);
        config.onscreenKeyboardWidth = prefs.getInt("seekbar_onscreen_keyboard_width",1000);
        config.onscreenKeyboardAlignMode = prefs.getString(LIST_ONSCREEN_KEYBOARD_ALIGN_MODE, DEFAULT_ONSCREEN_KEYBOARD_ALIGN_MODE);

        config.enableNewAnalogStick=prefs.getBoolean(CHECKBOX_CHECKBOX_ENABLE_ANALOG_STICK_NEW,false);

        config.enableExDisplay=prefs.getBoolean("checkbox_enable_exdisplay",false);

        config.alignDisplayTopCenter =prefs.getBoolean("checkbox_enable_view_top_center",false);

        config.touchSensitivityX =prefs.getInt(SEEKBAR_TOUCH_SENSITIVITY,100);

        config.touchSensitivityY=prefs.getInt("seekbar_touch_sensitivity_opacity_y",100);

        config.touchSensitivityRotationAuto=prefs.getBoolean("checkbox_enable_touch_sensitivity_rotation_auto",true);

        config.touchSensitivityGlobal=prefs.getBoolean("checkbox_enable_global_touch_sensitivity",false);

        config.enableTouchSensitivity=prefs.getBoolean("checkbox_enable_touch_sensitivity",false);

        config.enableMouseLocalCursor=prefs.getBoolean("checkbox_mouse_local_cursor",false);

        config.enablePerfOverlayLiteDialog=prefs.getBoolean("checkbox_enable_perf_overlay_lite_dialog",false);

        config.enableClearDefaultSpecial=prefs.getBoolean("checkbox_enable_clear_default_special_button", false);

        config.enableDeviceRumble=prefs.getBoolean("checkbox_enable_device_rumble", false);

        config.enableKeyboardSquare=prefs.getBoolean("checkbox_enable_keyboard_square",false);

        config.touchPadSensitivity=prefs.getInt("seekbar_touchpad_sensitivity_opacity",100);

        config.touchPadYSensitity=prefs.getInt("seekbar_touchpad_sensitivity_y_opacity",100);

        config.trackpadSensitivityX = prefs.getInt(SEEKBAR_TRACKPAD_SENSITIVITY_X, DEFAULT_TRACKPAD_SENSITIVITY_X);
        config.trackpadSensitivityY = prefs.getInt(SEEKBAR_TRACKPAD_SENSITIVITY_Y, DEFAULT_TRACKPAD_SENSITIVITY_Y);
        config.trackpadDragDropVibration = prefs.getBoolean(CHECKBOX_TRACKPAD_DRAG_DROP_VIBRATION, DEFAULT_TRACKPAD_DRAG_DROP_VIBRATION);
        config.trackpadDragDropThreshold = prefs.getInt(SEEKBAR_TRACKPAD_DRAG_DROP_THRESHOLD, DEFAULT_TRACKPAD_DRAG_DROP_THRESHOLD);
        config.trackpadSwapAxis = prefs.getBoolean(CHECKBOX_TRACKPAD_SWAP_AXIS, DEFAULT_TRACKPAD_SWAP_AXIS);

        config.absoluteMouseMode = prefs.getBoolean(ABSOLUTE_MOUSE_MODE_PREF_STRING, DEFAULT_ABSOLUTE_MOUSE_MODE);
        config.enableBatteryReport = prefs.getBoolean(CHECKBOX_ENABLE_BATTERY_REPORT, DEFAULT_GAMEPAD_ENABLE_BATTERY_REPORT);
        config.forceQwerty = prefs.getBoolean(CHECKBOX_FORCE_QWERTY, DEFAULT_FORCE_QWERTY);
        config.backAsMeta = prefs.getBoolean(CHECKBOX_BACK_AS_META, DEFAULT_SEND_META_ON_PHYSICAL_BACK);
        config.ignoreSynthEvents = prefs.getBoolean(CHECKBOX_IGNORE_SYNTH_EVENTS, DEFAULT_IGNORE_SYNTH_EVENTS);
        config.backAsGuide = prefs.getBoolean(CHECKBOX_BACK_AS_GUIDE, DEFAULT_BACK_AS_GUIDE);
        config.smartClipboardSync = prefs.getBoolean(CHECKBOX_SMART_CLIPBOARD_SYNC, DEFAULT_SMART_CLIPBOARD_SYNC);
        config.smartClipboardSyncToast = prefs.getBoolean(CHECKBOX_SMART_CLIPBOARD_SYNC_TOAST, DEFAULT_SMART_CLIPBOARD_SYNC_TOAST);
        config.hideClipboardContent = prefs.getBoolean(CHECKBOX_HIDE_CLIPBOARD_CONTENT, DEFAULT_HIDE_CLIPBOARD_CONTENT);
        config.stickyModifierKey = prefs.getBoolean(CHECKBOX_ENABLE_STICKY_MODIFIER_KEY_VIRTUAL_KEYBOARD, DEFAULT_ENABLE_STICKY_MODIFIER_KEY_VIRTUAL_KEYBOARD);
        config.enableAudioFx = prefs.getBoolean(ENABLE_AUDIO_FX_PREF_STRING, DEFAULT_ENABLE_AUDIO_FX);
        config.reduceRefreshRate = prefs.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, DEFAULT_REDUCE_REFRESH_RATE);
        config.fullRange = prefs.getBoolean(FULL_RANGE_PREF_STRING, DEFAULT_FULL_RANGE);
        config.gamepadTouchpadAsMouse = prefs.getBoolean(GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING, DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE);
        config.gamepadMotionSensors = prefs.getBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, DEFAULT_GAMEPAD_MOTION_SENSORS);
        config.gamepadMotionSensorsFallbackToDevice = prefs.getBoolean(GAMEPAD_MOTION_FALLBACK_PREF_STRING, DEFAULT_GAMEPAD_MOTION_FALLBACK);
        config.forceMotionSensorsFallbackToDevice = prefs.getBoolean(FORCE_MOTION_SENSORS_FALLBACK_PREF_STRING, DEFAULT_FORCE_MOTION_SENSORS_FALLBACK);

        // Read custom values
        config.customResolution = prefs.getString(CUSTOM_RESOLUTION_PREF_STRING, null);
        config.customRefreshRate = prefs.getString(CUSTOM_REFRESH_RATE_PREF_STRING, null);
//        config.customBitrate = prefs.getString(CUSTOM_BITRATE_PREF_STRING, null);

        return config;
    }
}
