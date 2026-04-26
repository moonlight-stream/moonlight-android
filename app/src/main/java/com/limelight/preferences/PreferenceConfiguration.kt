@file:Suppress("DEPRECATION")
package com.limelight.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.limelight.nvstream.jni.MoonBridge
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PreferenceConfiguration {

    enum class FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264
    }

    enum class AnalogStickForScrolling {
        NONE,
        RIGHT,
        LEFT
    }

    enum class PerfOverlayOrientation {
        HORIZONTAL,
        VERTICAL
    }

    enum class PerfOverlayPosition {
        // 水平方向选项
        TOP,
        BOTTOM,
        // 垂直方向选项（四个角）
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    // 位置枚举
    enum class ScreenPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    // ---- Instance fields ----

    var enableDoubleClickDrag = false
    var doubleTapTimeThreshold = 0
    var enableLocalCursorRendering = false
    //自定义按键映射
    var enableCustomKeyMap = false
    //修复鼠标中键识别
    var fixMouseMiddle = false
    //修复本地鼠标滚轮识别
    var fixMouseWheel = false

    var width = 0
    var height = 0
    var fps = 0
    var resolutionScale = 0
    var bitrate = 0
    var enableAdaptiveBitrate = false
    var abrMode: String = "balanced"  // quality | balanced | lowLatency
    var longPressflatRegionPixels = 0 //Assigned to NativeTouchContext.INTIAL_ZONE_PIXELS
    var syncTouchEventWithDisplay = false // if true, view.requestUnbufferedDispatch(event) will be disabled
    var enableEnhancedTouch = false //Assigned to NativeTouchContext.ENABLE_ENHANCED_TOUCH
    var enhancedTouchOnWhichSide = false //Assigned to NativeTouchContext.ENHANCED_TOUCH_ON_RIGHT
    var enhanceTouchZoneDivider = 0 //Assigned to NativeTouchContext.ENHANCED_TOUCH_ZONE_DIVIDER
    var pointerVelocityFactor = 0f //Assigned to NativeTouchContext.POINTER_VELOCITY_FACTOR
    var nativeTouchFingersToToggleKeyboard = 0 // Number of fingers to tap to toggle local on-screen keyboard in native touch mode.

    var videoFormat: FormatOption = FormatOption.AUTO
    var deadzonePercentage = 0
    @JvmField var oscOpacity = 0
    var stretchVideo = false
    var enableSops = false
    var playHostAudio = false
    var disableWarnings = false
    var language: String = ""
    var smallIconMode = false
    var multiController = false
    var usbDriver = false
    @JvmField var flipFaceButtons = false
    var onscreenController = false
    var onscreenKeyboard = false
    @JvmField var onlyL3R3 = false
    @JvmField var showGuideButton = false
    @JvmField var halfHeightOscPortrait = false
    var enableHdr = false
    var enableHdrHighBrightness = false
    var hdrMode = 0 // 0=HDR disabled, 1=HDR10/PQ, 2=HLG
    var enablePip = false
    var enablePerfOverlay = false
    var perfOverlayLocked = false
    var perfOverlayBgOpacity = 0
    var perfOverlayOrientation: PerfOverlayOrientation = PerfOverlayOrientation.HORIZONTAL
    var perfOverlayPosition: PerfOverlayPosition = PerfOverlayPosition.TOP
    var enableSimplifyPerfOverlay = false
    var enableLatencyToast = false
    var enableStun = false
    var screenCombinationMode = 0
    var vddScreenCombinationMode = 0
    var lockScreenAfterDisconnect = false
    var swapQuitAndDisconnect = false
    var bindAllUsb = false
    var mouseEmulation = false
    var analogStickForScrolling: AnalogStickForScrolling = AnalogStickForScrolling.NONE
    var mouseNavButtons = false
    var unlockFps = false
    var vibrateOsc = false
    var vibrateFallbackToDevice = false
    var vibrateFallbackToDeviceStrength = 0
    var enableAudioVibration = false
    var audioVibrationStrength = 0
    var audioVibrationMode: String = ""
    var audioVibrationScene = 0
    var touchscreenTrackpad = false
    var audioConfiguration: MoonBridge.AudioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO
    var framePacing = 0
    var absoluteMouseMode = false
    var enableNativeMousePointer = false
    var enableAudioFx = false
    var enableSpatializer = false
    var reduceRefreshRate = false
    var fullRange = false
    var gamepadMotionSensors = false
    var gamepadTouchpadAsMouse = false
    var gamepadMotionSensorsFallbackToDevice = false
    var reverseResolution = false
    var rotableScreen = false
    // Runtime-only: enable mapping gyroscope motion to right analog stick
    var gyroToRightStick = false
    // Runtime-only: enable mapping gyroscope motion to relative mouse movement
    var gyroToMouse = false
    // Runtime-only: sensitivity in deg/s for full stick deflection
    var gyroFullDeflectionDps = 0f
    // Persistent: sensitivity multiplier (higher -> faster)
    var gyroSensitivityMultiplier = 0f
    // Persistent: activation keycode to hold (Android keycode); 0 means LT analog, 1 means RT analog, otherwise Android key
    var gyroActivationKeyCode = 0
    // Persistent: invert X-axis direction for gyro input
    var gyroInvertXAxis = false
    // Persistent: invert Y-axis direction for gyro input
    var gyroInvertYAxis = false
    // Card visibility
    var showBitrateCard = false
    var showGyroCard = false
    var showQuickKeyCard = false

    // 麦克风设置
    var enableMic = false
    var micBitrate = 0
    var micIconColor: String = ""

    // ESC菜单设置
    var enableEscMenu = false
    var escMenuKey = 0

    // Start键菜单设置
    var enableStartKeyMenu = false

    // 控制流only模式设置
    var controlOnly = false

    // 输出缓冲区队列大小
    var outputBufferQueueLimit = 0

    var screenPosition: ScreenPosition = ScreenPosition.CENTER
    var screenOffsetX = 0
    var screenOffsetY = 0

    var useExternalDisplay = false

    // 悬浮球设置
    var enableFloatBall = false
    var floatBallAutoHideDelay = 0

    // 悬浮球交互监听器设置
    var floatBallSingleClickAction: String = ""
    var floatBallDoubleClickAction: String = ""
    var floatBallLongClickAction: String = ""
    var floatBallSwipeUpAction: String = ""
    var floatBallSwipeDownAction: String = ""
    var floatBallSwipeLeftAction: String = ""
    var floatBallSwipeRightAction: String = ""

    // ---- Instance methods ----

    fun writePreferences(context: Context): Boolean {
        return writePreferences(context, false)
    }

    /**
     * 写入设置到SharedPreferences
     * @param context 上下文
     * @param synchronous 是否同步写入（true使用commit，false使用apply）
     * @return 是否成功
     */
    fun writePreferences(context: Context, synchronous: Boolean): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context) ?: return false

        return try {
            // 转换枚举为字符串
            val positionString = when (screenPosition) {
                ScreenPosition.TOP_LEFT -> "top_left"
                ScreenPosition.TOP_CENTER -> "top_center"
                ScreenPosition.TOP_RIGHT -> "top_right"
                ScreenPosition.CENTER_LEFT -> "center_left"
                ScreenPosition.CENTER_RIGHT -> "center_right"
                ScreenPosition.BOTTOM_LEFT -> "bottom_left"
                ScreenPosition.BOTTOM_CENTER -> "bottom_center"
                ScreenPosition.BOTTOM_RIGHT -> "bottom_right"
                ScreenPosition.CENTER -> "center"
            }

            val editor = prefs.edit()
                .putString(RESOLUTION_PREF_STRING, "${width}x${height}")
                .putString(FPS_PREF_STRING, fps.toString())
                .putInt(BITRATE_PREF_STRING, bitrate)
                .putString(VIDEO_FORMAT_PREF_STRING, getVideoFormatPreferenceString(videoFormat))
                .putBoolean(ENABLE_HDR_PREF_STRING, enableHdr)
                .putBoolean(ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING, enableHdrHighBrightness)
                .putBoolean(ENABLE_PERF_OVERLAY_STRING, enablePerfOverlay)
                .putBoolean(PERF_OVERLAY_LOCKED_STRING, perfOverlayLocked)
                .putInt(PERF_OVERLAY_BG_OPACITY_STRING, perfOverlayBgOpacity)
                .putBoolean(REVERSE_RESOLUTION_PREF_STRING, reverseResolution)
                .putBoolean(ROTABLE_SCREEN_PREF_STRING, rotableScreen)
                .putBoolean(SHOW_BITRATE_CARD_PREF_STRING, showBitrateCard)
                .putBoolean(SHOW_GYRO_CARD_PREF_STRING, showGyroCard)
                .putBoolean(SHOW_QuickKeyCard, showQuickKeyCard)
                .putString(SCREEN_POSITION_PREF_STRING, positionString)
                .putInt(SCREEN_OFFSET_X_PREF_STRING, screenOffsetX)
                .putInt(SCREEN_OFFSET_Y_PREF_STRING, screenOffsetY)
                .putBoolean("use_external_display", useExternalDisplay)
                .putBoolean(ENABLE_MIC_PREF_STRING, enableMic)
                .putInt(MIC_BITRATE_PREF_STRING, micBitrate)
                .putString(MIC_ICON_COLOR_PREF_STRING, micIconColor)
                .putBoolean(ENABLE_ESC_MENU_PREF_STRING, enableEscMenu)
                .putString(ESC_MENU_KEY_PREF_STRING, escMenuKey.toString())
                .putBoolean(ENABLE_START_KEY_MENU_PREF_STRING, enableStartKeyMenu)
                .putBoolean(CONTROL_ONLY_PREF_STRING, controlOnly)
                .putBoolean(ENABLE_NATIVE_MOUSE_POINTER_PREF_STRING, enableNativeMousePointer)
                .putBoolean(ENABLE_DOUBLE_CLICK_DRAG_PREF_STRING, enableDoubleClickDrag)
                .putBoolean(ENABLE_LOCAL_CURSOR_RENDERING_PREF_STRING, enableLocalCursorRendering)
                .putFloat(GYRO_SENSITIVITY_MULTIPLIER_PREF_STRING, gyroSensitivityMultiplier)
                .putBoolean(GYRO_INVERT_X_AXIS_PREF_STRING, gyroInvertXAxis)
                .putBoolean(GYRO_INVERT_Y_AXIS_PREF_STRING, gyroInvertYAxis)
                .putInt(GYRO_ACTIVATION_KEY_CODE_PREF_STRING, gyroActivationKeyCode)

            if (synchronous) {
                editor.commit()
            } else {
                editor.apply()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copy(): PreferenceConfiguration {
        val copy = PreferenceConfiguration()
        copy.width = this.width
        copy.height = this.height
        copy.fps = this.fps
        copy.bitrate = this.bitrate
        copy.videoFormat = this.videoFormat
        copy.enableHdr = this.enableHdr
        copy.enableHdrHighBrightness = this.enableHdrHighBrightness
        copy.hdrMode = this.hdrMode
        copy.enablePerfOverlay = this.enablePerfOverlay
        copy.perfOverlayLocked = this.perfOverlayLocked
        copy.perfOverlayBgOpacity = this.perfOverlayBgOpacity
        copy.perfOverlayOrientation = this.perfOverlayOrientation
        copy.perfOverlayPosition = this.perfOverlayPosition
        copy.reverseResolution = this.reverseResolution
        copy.rotableScreen = this.rotableScreen
        copy.screenPosition = this.screenPosition
        copy.screenOffsetX = this.screenOffsetX
        copy.screenOffsetY = this.screenOffsetY
        copy.useExternalDisplay = this.useExternalDisplay
        copy.enableMic = this.enableMic
        copy.controlOnly = this.controlOnly
        copy.outputBufferQueueLimit = this.outputBufferQueueLimit
        copy.micBitrate = this.micBitrate
        copy.micIconColor = this.micIconColor
        copy.enableEscMenu = this.enableEscMenu
        copy.escMenuKey = this.escMenuKey
        copy.enableStartKeyMenu = this.enableStartKeyMenu
        copy.enableNativeMousePointer = this.enableNativeMousePointer
        copy.enableDoubleClickDrag = this.enableDoubleClickDrag
        copy.enableLocalCursorRendering = this.enableLocalCursorRendering
        copy.gyroToRightStick = this.gyroToRightStick
        copy.gyroToMouse = this.gyroToMouse
        copy.gyroFullDeflectionDps = this.gyroFullDeflectionDps
        copy.gyroSensitivityMultiplier = this.gyroSensitivityMultiplier
        copy.gyroActivationKeyCode = this.gyroActivationKeyCode
        copy.gyroInvertXAxis = this.gyroInvertXAxis
        copy.gyroInvertYAxis = this.gyroInvertYAxis
        copy.showBitrateCard = this.showBitrateCard
        copy.showGyroCard = this.showGyroCard
        copy.showQuickKeyCard = this.showQuickKeyCard
        return copy
    }

    // ---- Companion (static members) ----

    companion object {
        // ---- Private pref key constants ----
        private const val ENABLE_DOUBLE_CLICK_DRAG_PREF_STRING = "pref_enable_double_click_drag"
        private const val DOUBLE_TAP_TIME_THRESHOLD_PREF_STRING = "seekbar_double_tap_time_threshold"
        private const val ENABLE_LOCAL_CURSOR_RENDERING_PREF_STRING = "pref_enable_local_cursor_rendering"

        private const val LEGACY_RES_FPS_PREF_STRING = "list_resolution_fps"
        private const val LEGACY_ENABLE_51_SURROUND_PREF_STRING = "checkbox_51_surround"

        private const val BITRATE_PREF_OLD_STRING = "seekbar_bitrate"
        private const val ADAPTIVE_BITRATE_PREF_STRING = "checkbox_adaptive_bitrate"
        private const val ABR_MODE_PREF_STRING = "list_abr_mode"
        private const val STRETCH_PREF_STRING = "checkbox_stretch_video"
        private const val SOPS_PREF_STRING = "checkbox_enable_sops"
        private const val DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings"
        private const val HOST_AUDIO_PREF_STRING = "checkbox_host_audio"
        private const val DEADZONE_PREF_STRING = "seekbar_deadzone"
        private const val OSC_OPACITY_PREF_STRING = "seekbar_osc_opacity"
        private const val LANGUAGE_PREF_STRING = "list_languages"
        private const val SMALL_ICONS_PREF_STRING = "checkbox_small_icon_mode"
        private const val MULTI_CONTROLLER_PREF_STRING = "checkbox_multi_controller"
        private const val USB_DRIVER_PREF_SRING = "checkbox_usb_driver"
        private const val VIDEO_FORMAT_PREF_STRING = "video_format"
        private const val ONSCREEN_KEYBOARD_PREF_STRING = "checkbox_show_onscreen_keyboard"
        private const val ONLY_L3_R3_PREF_STRING = "checkbox_only_show_L3R3"
        private const val SHOW_GUIDE_BUTTON_PREF_STRING = "checkbox_show_guide_button"
        private const val HALF_HEIGHT_OSC_PORTRAIT_PREF_STRING = "checkbox_half_height_osc_portrait"
        private const val LEGACY_DISABLE_FRAME_DROP_PREF_STRING = "checkbox_disable_frame_drop"
        private const val ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr"
        private const val ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING = "checkbox_enable_hdr_high_brightness"
        private const val HDR_MODE_PREF_STRING = "list_hdr_mode" // 0=SDR, 1=HDR10, 2=HLG
        private const val ENABLE_PIP_PREF_STRING = "checkbox_enable_pip"
        private const val ENABLE_PERF_OVERLAY_STRING = "checkbox_enable_perf_overlay"
        private const val PERF_OVERLAY_LOCKED_STRING = "perf_overlay_locked"
        private const val PERF_OVERLAY_BG_OPACITY_STRING = "seekbar_perf_overlay_bg_opacity"
        private const val PERF_OVERLAY_ORIENTATION_STRING = "list_perf_overlay_orientation"
        private const val PERF_OVERLAY_POSITION_STRING = "list_perf_overlay_position"
        private const val BIND_ALL_USB_STRING = "checkbox_usb_bind_all"
        private const val MOUSE_EMULATION_STRING = "checkbox_mouse_emulation"
        private const val ANALOG_SCROLLING_PREF_STRING = "analog_scrolling"
        private const val MOUSE_NAV_BUTTONS_STRING = "checkbox_mouse_nav_buttons"
        private const val VIBRATE_OSC_PREF_STRING = "checkbox_vibrate_osc"
        private const val VIBRATE_FALLBACK_PREF_STRING = "checkbox_vibrate_fallback"
        private const val VIBRATE_FALLBACK_STRENGTH_PREF_STRING = "seekbar_vibrate_fallback_strength"
        private const val AUDIO_VIBRATION_ENABLE_PREF_STRING = "checkbox_audio_vibration"
        private const val AUDIO_VIBRATION_STRENGTH_PREF_STRING = "seekbar_audio_vibration_strength"
        private const val AUDIO_VIBRATION_MODE_PREF_STRING = "list_audio_vibration_mode"
        private const val AUDIO_VIBRATION_SCENE_PREF_STRING = "list_audio_vibration_scene"
        private const val FLIP_FACE_BUTTONS_PREF_STRING = "checkbox_flip_face_buttons"
        private const val LATENCY_TOAST_PREF_STRING = "checkbox_enable_post_stream_toast"
        private const val ENABLE_STUN_PREF_STRING = "checkbox_enable_stun"
        private const val LOCK_SCREEN_AFTER_DISCONNECT_PREF_STRING = "checkbox_lock_screen_after_disconnect"
        private const val SWAP_QUIT_AND_DISCONNECT_PERF_STRING = "checkbox_swap_quit_and_disconnect"
        private const val SCREEN_COMBINATION_MODE_PREF_STRING = "list_screen_combination_mode"
        private const val FRAME_PACING_PREF_STRING = "frame_pacing"
        private const val ABSOLUTE_MOUSE_MODE_PREF_STRING = "checkbox_absolute_mouse_mode"
        // Card visibility preferences
        private const val SHOW_BITRATE_CARD_PREF_STRING = "checkbox_show_bitrate_card"
        private const val SHOW_GYRO_CARD_PREF_STRING = "checkbox_show_gyro_card"
        @Suppress("ConstPropertyName")
        private const val SHOW_QuickKeyCard = "checkbox_show_QuickKeyCard"

        private const val ENHANCED_TOUCH_ON_RIGHT_PREF_STRING = "checkbox_enhanced_touch_on_which_side"
        private const val ENHANCED_TOUCH_ZONE_DIVIDER_PREF_STRING = "enhanced_touch_zone_divider"
        private const val POINTER_VELOCITY_FACTOR_PREF_STRING = "pointer_velocity_factor"

        private const val ENABLE_AUDIO_FX_PREF_STRING = "checkbox_enable_audiofx"
        private const val ENABLE_SPATIALIZER_PREF_STRING = "checkbox_enable_spatializer"
        private const val REDUCE_REFRESH_RATE_PREF_STRING = "checkbox_reduce_refresh_rate"
        private const val FULL_RANGE_PREF_STRING = "checkbox_full_range"
        private const val GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING = "checkbox_gamepad_touchpad_as_mouse"
        private const val GAMEPAD_MOTION_SENSORS_PREF_STRING = "checkbox_gamepad_motion_sensors"
        private const val GAMEPAD_MOTION_FALLBACK_PREF_STRING = "checkbox_gamepad_motion_fallback"

        // 陀螺仪偏好设置
        private const val GYRO_SENSITIVITY_MULTIPLIER_PREF_STRING = "gyro_sensitivity_multiplier"
        private const val GYRO_INVERT_X_AXIS_PREF_STRING = "gyro_invert_x_axis"
        private const val GYRO_INVERT_Y_AXIS_PREF_STRING = "gyro_invert_y_axis"
        private const val GYRO_ACTIVATION_KEY_CODE_PREF_STRING = "gyro_activation_key_code"

        // 麦克风设置
        private const val ENABLE_MIC_PREF_STRING = "checkbox_enable_mic"
        private const val MIC_BITRATE_PREF_STRING = "seekbar_mic_bitrate_kbps"
        private const val MIC_ICON_COLOR_PREF_STRING = "list_mic_icon_color"

        private const val ENABLE_ESC_MENU_PREF_STRING = "checkbox_enable_esc_menu"
        private const val ESC_MENU_KEY_PREF_STRING = "list_esc_menu_key"
        private const val ENABLE_START_KEY_MENU_PREF_STRING = "checkbox_enable_start_key_menu"

        // 悬浮球设置
        private const val ENABLE_FLOAT_BALL_PREF_STRING = "checkbox_enable_float_ball"
        private const val FLOAT_BALL_AUTO_HIDE_DELAY_PREF_STRING = "seekbar_float_ball_auto_hide_delay"

        // 悬浮球交互监听器设置
        private const val FLOAT_BALL_SINGLE_CLICK_ACTION_PREF_STRING = "list_float_ball_single_click_action"
        private const val FLOAT_BALL_DOUBLE_CLICK_ACTION_PREF_STRING = "list_float_ball_double_click_action"
        private const val FLOAT_BALL_LONG_CLICK_ACTION_PREF_STRING = "list_float_ball_long_click_action"
        private const val FLOAT_BALL_SWIPE_UP_ACTION_PREF_STRING = "list_float_ball_swipe_up_action"
        private const val FLOAT_BALL_SWIPE_DOWN_ACTION_PREF_STRING = "list_float_ball_swipe_down_action"
        private const val FLOAT_BALL_SWIPE_LEFT_ACTION_PREF_STRING = "list_float_ball_swipe_left_action"
        private const val FLOAT_BALL_SWIPE_RIGHT_ACTION_PREF_STRING = "list_float_ball_swipe_right_action"

        // 控制流only模式设置
        private const val CONTROL_ONLY_PREF_STRING = "checkbox_control_only"

        // 输出缓冲区队列大小设置
        private const val OUTPUT_BUFFER_QUEUE_LIMIT_PREF_STRING = "seekbar_output_buffer_queue_limit"

        //wg
        private const val ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls"

        private const val REVERSE_RESOLUTION_PREF_STRING = "checkbox_reverse_resolution"
        private const val ROTABLE_SCREEN_PREF_STRING = "checkbox_rotable_screen"

        // 画面位置常量
        private const val SCREEN_POSITION_PREF_STRING = "list_screen_position"
        private const val SCREEN_OFFSET_X_PREF_STRING = "seekbar_screen_offset_x"
        private const val SCREEN_OFFSET_Y_PREF_STRING = "seekbar_screen_offset_y"

        // ---- Public pref key constants ----
        const val RESOLUTION_PREF_STRING = "list_resolution"
        const val TOUCHSCREEN_TRACKPAD_PREF_STRING = "checkbox_touchscreen_trackpad"
        const val ENABLE_NATIVE_MOUSE_POINTER_PREF_STRING = "checkbox_enable_native_mouse_pointer"
        const val NATIVE_MOUSE_MODE_PRESET_PREF_STRING = "list_native_mouse_mode_preset"
        const val ENABLE_ENHANCED_TOUCH_PREF_STRING = "checkbox_enable_enhanced_touch"

        // ---- Package-private pref key constants (promoted to public for Kotlin interop) ----
        const val FPS_PREF_STRING = "list_fps"
        const val BITRATE_PREF_STRING = "seekbar_bitrate_kbps"
        const val HOST_SCALE_PREF_STRING = "seekbar_resolutions_scale"
        const val LONG_PRESS_FLAT_REGION_PIXELS_PREF_STRING = "seekbar_flat_region_pixels"
        const val SYNC_TOUCH_EVENT_WITH_DISPLAY_PREF_STRING = "checkbox_sync_touch_event_with_display"
        const val ENABLE_KEYBOARD_TOGGLE_IN_NATIVE_TOUCH = "checkbox_enable_keyboard_toggle_in_native_touch"
        const val NATIVE_TOUCH_FINGERS_TO_TOGGLE_KEYBOARD_PREF_STRING = "seekbar_keyboard_toggle_fingers_native_touch"
        const val AUDIO_CONFIG_PREF_STRING = "list_audio_config"
        const val UNLOCK_FPS_STRING = "checkbox_unlock_fps"
        const val IMPORT_CONFIG_STRING = "import_super_config"
        const val EXPORT_CONFIG_STRING = "export_super_config"
        const val MERGE_CONFIG_STRING = "merge_super_config"
        const val ABOUT_AUTHOR = "about_author"

        // ---- Default values (package-private promoted to public) ----
        const val DEFAULT_RESOLUTION = "1920x1080"
        const val DEFAULT_FPS = "60"

        // ---- Default values (private) ----
        private const val DEFAULT_STRETCH = false
        private const val DEFAULT_SOPS = true
        private const val DEFAULT_DISABLE_TOASTS = false
        private const val DEFAULT_HOST_AUDIO = false
        private const val DEFAULT_DEADZONE = 7
        private const val DEFAULT_OPACITY = 90
        const val DEFAULT_LANGUAGE = "default"
        private const val DEFAULT_MULTI_CONTROLLER = true
        private const val DEFAULT_USB_DRIVER = true

        private const val ONSCREEN_CONTROLLER_DEFAULT = false
        private const val ONSCREEN_KEYBOARD_DEFAULT = false
        private const val ONLY_L3_R3_DEFAULT = false
        private const val SHOW_GUIDE_BUTTON_DEFAULT = true
        private const val HALF_HEIGHT_OSC_PORTRAIT_DEFAULT = true
        private const val DEFAULT_ENABLE_HDR = false
        private const val DEFAULT_ENABLE_HDR_HIGH_BRIGHTNESS = false
        private const val DEFAULT_HDR_MODE = 1 // 默认 HDR10/PQ 模式 (0=禁用自动HDR切换, 1=HDR10, 2=HLG)
        private const val DEFAULT_ENABLE_PIP = false
        private const val DEFAULT_ENABLE_PERF_OVERLAY = false
        private const val DEFAULT_PERF_OVERLAY_LOCKED = false
        private const val DEFAULT_PERF_OVERLAY_BG_OPACITY = 53
        private const val DEFAULT_PERF_OVERLAY_ORIENTATION = "horizontal"
        private const val DEFAULT_PERF_OVERLAY_POSITION = "top"
        private const val DEFAULT_BIND_ALL_USB = false
        private const val DEFAULT_MOUSE_EMULATION = true
        private const val DEFAULT_ANALOG_STICK_FOR_SCROLLING = "right"
        private const val DEFAULT_MOUSE_NAV_BUTTONS = false
        private const val DEFAULT_NATIVE_MOUSE_MODE_PRESET = "classic"
        private const val DEFAULT_UNLOCK_FPS = false
        private const val DEFAULT_VIBRATE_OSC = true
        private const val DEFAULT_VIBRATE_FALLBACK = false
        private const val DEFAULT_VIBRATE_FALLBACK_STRENGTH = 100
        private const val DEFAULT_AUDIO_VIBRATION = false
        private const val DEFAULT_AUDIO_VIBRATION_STRENGTH = 80
        private const val DEFAULT_AUDIO_VIBRATION_MODE = "auto"
        private const val DEFAULT_AUDIO_VIBRATION_SCENE = 0 // Game/Movie
        private const val DEFAULT_FLIP_FACE_BUTTONS = false
        private const val DEFAULT_TOUCHSCREEN_TRACKPAD = true
        private const val DEFAULT_AUDIO_CONFIG = "2" // Stereo
        private const val DEFAULT_LATENCY_TOAST = false
        private const val DEFAULT_ENABLE_STUN = false
        private const val DEFAULT_SCREEN_COMBINATION_MODE = "-1"
        private const val DEFAULT_FRAME_PACING = "latency"
        private const val DEFAULT_ABSOLUTE_MOUSE_MODE = false
        private const val DEFAULT_ENABLE_NATIVE_MOUSE_POINTER = false
        private const val DEFAULT_ENABLE_AUDIO_FX = false
        private const val DEFAULT_ENABLE_SPATIALIZER = false
        private const val DEFAULT_REDUCE_REFRESH_RATE = false
        private const val DEFAULT_FULL_RANGE = false
        private const val DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE = false
        private const val DEFAULT_GAMEPAD_MOTION_SENSORS = true
        private const val DEFAULT_GAMEPAD_MOTION_FALLBACK = false

        // 陀螺仪偏好默认值
        private const val DEFAULT_GYRO_SENSITIVITY_MULTIPLIER = 1.0f
        private const val DEFAULT_GYRO_INVERT_X_AXIS = false
        private const val DEFAULT_GYRO_INVERT_Y_AXIS = false
        private val DEFAULT_GYRO_ACTIVATION_KEY_CODE = KeyEvent.KEYCODE_BUTTON_L2

        // 麦克风设置默认值
        private const val DEFAULT_ENABLE_MIC = false
        private const val DEFAULT_MIC_BITRATE = 96 // 默认128 kbps
        private const val DEFAULT_MIC_ICON_COLOR = "solid_white" // 默认白
        private const val DEFAULT_ENABLE_ESC_MENU = true // 默认启用ESC菜单
        private val DEFAULT_ESC_MENU_KEY = KeyEvent.KEYCODE_ESCAPE
        private const val DEFAULT_ENABLE_START_KEY_MENU = true // 默认启用长按start键菜单

        // 悬浮球设置默认值
        private const val DEFAULT_ENABLE_FLOAT_BALL = true // 默认启用悬浮球
        private const val DEFAULT_FLOAT_BALL_AUTO_HIDE_DELAY = 2000 // 默认2000ms

        // 悬浮球交互监听器默认值
        private const val DEFAULT_FLOAT_BALL_SINGLE_CLICK_ACTION = "open_keyboard" // 单击打开键盘
        private const val DEFAULT_FLOAT_BALL_DOUBLE_CLICK_ACTION = "open_menu" // 双击打开菜单
        private const val DEFAULT_FLOAT_BALL_LONG_CLICK_ACTION = "toggle_visibility" // 长按切换可见性
        private const val DEFAULT_FLOAT_BALL_SWIPE_UP_ACTION = "none" // 向上滑动无操作
        private const val DEFAULT_FLOAT_BALL_SWIPE_DOWN_ACTION = "none" // 向下滑动无操作
        private const val DEFAULT_FLOAT_BALL_SWIPE_LEFT_ACTION = "none" // 向左滑动无操作
        private const val DEFAULT_FLOAT_BALL_SWIPE_RIGHT_ACTION = "none" // 向右滑动无操作

        // 控制流only模式默认值
        private const val DEFAULT_CONTROL_ONLY = false

        // 输出缓冲区队列大小默认值
        private const val DEFAULT_OUTPUT_BUFFER_QUEUE_LIMIT = 2

        private const val DEFAULT_ENABLE_DOUBLE_CLICK_DRAG = false
        private const val DEFAULT_DOUBLE_TAP_TIME_THRESHOLD = 125 // 默认125ms
        private const val DEFAULT_ENABLE_LOCAL_CURSOR_RENDERING = true

        private const val DEFAULT_REVERSE_RESOLUTION = false
        private const val DEFAULT_ROTABLE_SCREEN = false

        // 默认值
        private const val DEFAULT_SCREEN_POSITION = "center" // 居中
        private const val DEFAULT_SCREEN_OFFSET_X = 0
        private const val DEFAULT_SCREEN_OFFSET_Y = 0

        // ---- Public static final constants ----
        const val FRAME_PACING_MIN_LATENCY = 0
        const val FRAME_PACING_BALANCED = 1
        const val FRAME_PACING_CAP_FPS = 2
        const val FRAME_PACING_MAX_SMOOTHNESS = 3
        const val FRAME_PACING_EXPERIMENTAL_LOW_LATENCY = 4
        const val FRAME_PACING_PRECISE_SYNC = 5

        const val RES_360P = "640x360"
        const val RES_480P = "854x480"
        const val RES_720P = "1280x720"
        const val RES_1080P = "1920x1080"
        const val RES_1440P = "2560x1440"
        const val RES_4K = "3840x2160"
        const val RES_NATIVE = "Native"

        private const val VIDEO_FORMAT_AUTO = "auto"
        private const val VIDEO_FORMAT_AV1 = "forceav1"
        private const val VIDEO_FORMAT_HEVC = "forceh265"
        private const val VIDEO_FORMAT_H264 = "neverh265"

        val RESOLUTIONS = arrayOf(
            "640x360", "854x480", "1280x720", "1920x1080", "2560x1440", "3840x2160", "Native"
        )

        // ---- Public static methods ----

        fun isNativeResolution(width: Int, height: Int): Boolean {
            val resolutionSet = RESOLUTIONS.toHashSet()
            return !resolutionSet.contains("${width}x${height}")
        }

        // If we have a screen that has semi-square dimensions, we may want to change our behavior
        // to allow any orientation and vertical+horizontal resolutions.
        fun isSquarishScreen(width: Int, height: Int): Boolean {
            val longDim = max(width, height).toFloat()
            val shortDim = min(width, height).toFloat()

            // We just put the arbitrary cutoff for a square-ish screen at 1.3
            return longDim / shortDim < 1.3f
        }

        @Suppress("DEPRECATION")
        fun isSquarishScreen(display: Display): Boolean {
            val width: Int
            val height: Int

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                width = display.mode.physicalWidth
                height = display.mode.physicalHeight
            } else {
                width = display.width
                height = display.height
            }

            return isSquarishScreen(width, height)
        }

        private fun convertFromLegacyResolutionString(resString: String): String {
            return when {
                resString.equals("360p", ignoreCase = true) -> RES_360P
                resString.equals("480p", ignoreCase = true) -> RES_480P
                resString.equals("720p", ignoreCase = true) -> RES_720P
                resString.equals("1080p", ignoreCase = true) -> RES_1080P
                resString.equals("1440p", ignoreCase = true) -> RES_1440P
                resString.equals("4K", ignoreCase = true) -> RES_4K
                else -> RES_1080P // Should be unreachable
            }
        }

        private fun getWidthFromResolutionString(resString: String): Int {
            return try {
                resString.split("x")[0].toInt()
            } catch (e: Exception) {
                // 如果解析失败，返回默认宽度
                DEFAULT_RESOLUTION.split("x")[0].toInt()
            }
        }

        private fun getHeightFromResolutionString(resString: String): Int {
            return try {
                resString.split("x")[1].toInt()
            } catch (e: Exception) {
                // 如果解析失败，返回默认高度
                DEFAULT_RESOLUTION.split("x")[1].toInt()
            }
        }

        private fun getResolutionString(width: Int, height: Int): String {
            // 使用数组简化分辨率获取
            for (res in RESOLUTIONS) {
                val dimensions = res.split("x")
                if (height == dimensions[1].toInt()) {
                    return res
                }
            }
            return RES_1080P // 默认返回1080P
        }

        fun getDefaultBitrate(resString: String, fpsString: String): Int {
            val width = getWidthFromResolutionString(resString)
            val height = getHeightFromResolutionString(resString)
            val fps = fpsString.toInt()

            // This logic is shamelessly stolen from Moonlight Qt:
            // https://github.com/moonlight-stream/moonlight-qt/blob/master/app/settings/streamingpreferences.cpp

            // Don't scale bitrate linearly beyond 60 FPS. It's definitely not a linear
            // bitrate increase for frame rate once we get to values that high.
            val frameRateFactor = (if (fps <= 60) fps.toDouble() else sqrt(fps / 60.0) * 60.0) / 30.0

            // TODO: Collect some empirical data to see if these defaults make sense.
            // We're just using the values that the Shield used, as we have for years.
            val pixelVals = intArrayOf(
                640 * 360,
                854 * 480,
                1280 * 720,
                1920 * 1080,
                2560 * 1440,
                3840 * 2160,
                -1,
            )
            val factorVals = intArrayOf(
                1,
                2,
                5,
                10,
                20,
                40,
                -1
            )

            // Calculate the resolution factor by linear interpolation of the resolution table
            val resolutionFactor: Float
            val pixels = width * height
            var i = 0
            while (true) {
                if (pixels == pixelVals[i]) {
                    // We can bail immediately for exact matches
                    resolutionFactor = factorVals[i].toFloat()
                    break
                } else if (pixels < pixelVals[i]) {
                    if (i == 0) {
                        // Never go below the lowest resolution entry
                        resolutionFactor = factorVals[i].toFloat()
                    } else {
                        // Interpolate between the entry greater than the chosen resolution (i) and the entry less than the chosen resolution (i-1)
                        resolutionFactor = (pixels - pixelVals[i - 1]).toFloat() / (pixelVals[i] - pixelVals[i - 1]) * (factorVals[i] - factorVals[i - 1]) + factorVals[i - 1]
                    }
                    break
                } else if (pixelVals[i] == -1) {
                    // Never go above the highest resolution entry
                    resolutionFactor = factorVals[i - 1].toFloat()
                    break
                }
                i++
            }

            return (resolutionFactor * frameRateFactor).roundToInt() * 1000
        }

        fun getDefaultSmallMode(context: Context): Boolean {
            val manager = context.packageManager
            if (manager != null) {
                // TVs shouldn't use small mode by default
                if (manager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                    return false
                }

                // API 21 uses LEANBACK instead of TELEVISION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (manager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                        return false
                    }
                }
            }

            // Use small mode on anything smaller than a 7" tablet
            return context.resources.configuration.smallestScreenWidthDp < 500
        }

        fun getDefaultBitrate(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return getDefaultBitrate(
                prefs.getString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION,
                prefs.getString(FPS_PREF_STRING, DEFAULT_FPS) ?: DEFAULT_FPS
            )
        }

        private fun getVideoFormatValue(context: Context): FormatOption {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return when (prefs.getString(VIDEO_FORMAT_PREF_STRING, VIDEO_FORMAT_AUTO)) {
                VIDEO_FORMAT_AV1 -> FormatOption.FORCE_AV1
                VIDEO_FORMAT_HEVC -> FormatOption.FORCE_HEVC
                VIDEO_FORMAT_H264 -> FormatOption.FORCE_H264
                else -> FormatOption.AUTO
            }
        }

        private fun getVideoFormatPreferenceString(format: FormatOption): String {
            return when (format) {
                FormatOption.AUTO -> VIDEO_FORMAT_AUTO
                FormatOption.FORCE_AV1 -> VIDEO_FORMAT_AV1
                FormatOption.FORCE_HEVC -> VIDEO_FORMAT_HEVC
                FormatOption.FORCE_H264 -> VIDEO_FORMAT_H264
            }
        }

        private fun getFramePacingValue(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            // Migrate legacy never drop frames option to the new location
            if (prefs.contains(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)) {
                val legacyNeverDropFrames = prefs.getBoolean(LEGACY_DISABLE_FRAME_DROP_PREF_STRING, false)
                prefs.edit()
                    .remove(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)
                    .putString(FRAME_PACING_PREF_STRING, if (legacyNeverDropFrames) "balanced" else "latency")
                    .apply()
            }

            return when (prefs.getString(FRAME_PACING_PREF_STRING, DEFAULT_FRAME_PACING)) {
                "latency" -> FRAME_PACING_MIN_LATENCY
                "balanced" -> FRAME_PACING_BALANCED
                "cap-fps" -> FRAME_PACING_CAP_FPS
                "smoothness" -> FRAME_PACING_MAX_SMOOTHNESS
                "experimental-low-latency" -> FRAME_PACING_EXPERIMENTAL_LOW_LATENCY
                "precise-sync" -> FRAME_PACING_PRECISE_SYNC
                else -> FRAME_PACING_MIN_LATENCY // Should never get here
            }
        }

        private fun getAnalogStickForScrollingValue(context: Context): AnalogStickForScrolling {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            return when (prefs.getString(ANALOG_SCROLLING_PREF_STRING, DEFAULT_ANALOG_STICK_FOR_SCROLLING)) {
                "right" -> AnalogStickForScrolling.RIGHT
                "left" -> AnalogStickForScrolling.LEFT
                else -> AnalogStickForScrolling.NONE
            }
        }

        fun resetStreamingSettings(context: Context) {
            // We consider resolution, FPS, bitrate, HDR, and video format as "streaming settings" here
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .remove(BITRATE_PREF_STRING)
                .remove(BITRATE_PREF_OLD_STRING)
                .remove(HOST_SCALE_PREF_STRING)
                .remove(LEGACY_RES_FPS_PREF_STRING)
                .remove(RESOLUTION_PREF_STRING)
                .remove(FPS_PREF_STRING)
                .remove(VIDEO_FORMAT_PREF_STRING)
                .remove(ENABLE_HDR_PREF_STRING)
                .remove(ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING)
                .remove(UNLOCK_FPS_STRING)
                .remove(FULL_RANGE_PREF_STRING)
                .apply()
        }

        fun completeLanguagePreferenceMigration(context: Context) {
            // Put our language option back to default which tells us that we've already migrated it
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE).apply()
        }

        fun isShieldAtvFirmwareWithBrokenHdr(): Boolean {
            // This particular Shield TV firmware crashes when using HDR
            // https://www.nvidia.com/en-us/geforce/forums/notifications/comment/155192/
            return Build.MANUFACTURER.equals("NVIDIA", ignoreCase = true) &&
                    Build.FINGERPRINT.contains("PPR1.180610.011/4079208_2235.1395")
        }

        @Suppress("DEPRECATION")
        @JvmStatic
        fun readPreferences(context: Context): PreferenceConfiguration {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val config = PreferenceConfiguration()

            // Migrate legacy preferences to the new locations
            if (prefs.contains(LEGACY_ENABLE_51_SURROUND_PREF_STRING)) {
                if (prefs.getBoolean(LEGACY_ENABLE_51_SURROUND_PREF_STRING, false)) {
                    prefs.edit()
                        .remove(LEGACY_ENABLE_51_SURROUND_PREF_STRING)
                        .putString(AUDIO_CONFIG_PREF_STRING, "51")
                        .apply()
                }
            }

            val resStr = prefs.getString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION

            // 添加Native分辨率支持
            if (resStr == RES_NATIVE) {
                // 获取设备原生分辨率
                val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
                val size = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    display.getRealSize(size) // 需要API 17+
                } else {
                    display.getSize(size) // 兼容旧版本
                }
                config.width = size.x
                config.height = size.y
            } else {
                // 原有解析逻辑
                config.width = getWidthFromResolutionString(resStr)
                config.height = getHeightFromResolutionString(resStr)
            }

            // 处理新旧数据类型兼容
            val fpsValue = prefs.all[FPS_PREF_STRING]
            when (fpsValue) {
                is String -> config.fps = fpsValue.toInt()
                is Int -> {
                    // 迁移旧整型值为字符串
                    config.fps = fpsValue
                    prefs.edit().putString(FPS_PREF_STRING, config.fps.toString()).apply()
                }
                else -> {
                    // 默认值处理
                    config.fps = (prefs.getString(FPS_PREF_STRING, DEFAULT_FPS) ?: DEFAULT_FPS).toInt()
                }
            }

            if (!prefs.contains(SMALL_ICONS_PREF_STRING)) {
                // We need to write small icon mode's default to disk for the settings page to display
                // the current state of the option properly
                prefs.edit().putBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context)).apply()
            }

            if (!prefs.contains(GAMEPAD_MOTION_SENSORS_PREF_STRING) && Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
                // Android 12 has a nasty bug that causes crashes when the app touches the InputDevice's
                // associated InputDeviceSensorManager (just calling getSensorManager() is enough).
                // As a workaround, we will override the default value for the gamepad motion sensor
                // option to disabled on Android 12 to reduce the impact of this bug.
                // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
                prefs.edit().putBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, false).apply()
            }

            // This must happen after the preferences migration to ensure the preferences are populated
            config.bitrate = prefs.getInt(BITRATE_PREF_STRING, prefs.getInt(BITRATE_PREF_OLD_STRING, 0) * 1000)
            if (config.bitrate == 0) {
                config.bitrate = getDefaultBitrate(context)
            }

            config.enableAdaptiveBitrate = prefs.getBoolean(ADAPTIVE_BITRATE_PREF_STRING, false)
            config.abrMode = prefs.getString(ABR_MODE_PREF_STRING, "balanced") ?: "balanced"

            config.resolutionScale = prefs.getInt(HOST_SCALE_PREF_STRING, 100)
            config.longPressflatRegionPixels = prefs.getInt(LONG_PRESS_FLAT_REGION_PIXELS_PREF_STRING, 0) // define a flat region to suppress coordinates jitter.
            config.syncTouchEventWithDisplay = prefs.getBoolean(SYNC_TOUCH_EVENT_WITH_DISPLAY_PREF_STRING, false) // set true to disable "requestUnbufferedDispatch"
            if (prefs.getBoolean(ENABLE_KEYBOARD_TOGGLE_IN_NATIVE_TOUCH, true)) {
                config.nativeTouchFingersToToggleKeyboard = prefs.getInt(NATIVE_TOUCH_FINGERS_TO_TOGGLE_KEYBOARD_PREF_STRING, 3) // least fingers of tap to toggle local keyboard
            } else {
                config.nativeTouchFingersToToggleKeyboard = -1 // completely disable keyboard toggle in multi-point touch
            }

            // Enhance touch settings
            config.enableEnhancedTouch = prefs.getBoolean(ENABLE_ENHANCED_TOUCH_PREF_STRING, false)
            config.enhancedTouchOnWhichSide = prefs.getBoolean(ENHANCED_TOUCH_ON_RIGHT_PREF_STRING, true) // by default, enhanced touch zone is on the right side.
            config.enhanceTouchZoneDivider = prefs.getInt(ENHANCED_TOUCH_ZONE_DIVIDER_PREF_STRING, 50) // decides where to divide native touch zone & enhance touch zone
            config.pointerVelocityFactor = prefs.getInt(POINTER_VELOCITY_FACTOR_PREF_STRING, 100).toFloat() // set pointer velocity faster or slower

            val audioConfig = prefs.getString(AUDIO_CONFIG_PREF_STRING, DEFAULT_AUDIO_CONFIG) ?: DEFAULT_AUDIO_CONFIG
            config.audioConfiguration = when (audioConfig) {
                "714" -> MoonBridge.AUDIO_CONFIGURATION_714_SURROUND
                "71" -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
                "51" -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
                else -> MoonBridge.AUDIO_CONFIGURATION_STEREO
            }

            config.videoFormat = getVideoFormatValue(context)
            config.framePacing = getFramePacingValue(context)

            config.analogStickForScrolling = getAnalogStickForScrollingValue(context)

            config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE)

            config.oscOpacity = prefs.getInt(OSC_OPACITY_PREF_STRING, DEFAULT_OPACITY)

            config.language = prefs.getString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

            // Checkbox preferences
            config.disableWarnings = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS)
            config.enableDoubleClickDrag = prefs.getBoolean(ENABLE_DOUBLE_CLICK_DRAG_PREF_STRING, DEFAULT_ENABLE_DOUBLE_CLICK_DRAG)
            config.doubleTapTimeThreshold = prefs.getInt(DOUBLE_TAP_TIME_THRESHOLD_PREF_STRING, DEFAULT_DOUBLE_TAP_TIME_THRESHOLD)
            config.enableLocalCursorRendering = prefs.getBoolean(ENABLE_LOCAL_CURSOR_RENDERING_PREF_STRING, DEFAULT_ENABLE_LOCAL_CURSOR_RENDERING)
            config.enableCustomKeyMap = prefs.getBoolean("checkbox_special_key_map", false)
            config.fixMouseMiddle = prefs.getBoolean("checkbox_mouse_middle", false)
            config.fixMouseWheel = prefs.getBoolean("checkbox_mouse_wheel", false)
            config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS)
            config.stretchVideo = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH)
            config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO)
            config.smallIconMode = prefs.getBoolean(SMALL_ICONS_PREF_STRING, getDefaultSmallMode(context))
            config.multiController = prefs.getBoolean(MULTI_CONTROLLER_PREF_STRING, DEFAULT_MULTI_CONTROLLER)
            config.usbDriver = prefs.getBoolean(USB_DRIVER_PREF_SRING, DEFAULT_USB_DRIVER)
            config.onscreenController = prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, ONSCREEN_CONTROLLER_DEFAULT)
            config.onscreenKeyboard = prefs.getBoolean(ONSCREEN_KEYBOARD_PREF_STRING, ONSCREEN_KEYBOARD_DEFAULT)
            config.onlyL3R3 = prefs.getBoolean(ONLY_L3_R3_PREF_STRING, ONLY_L3_R3_DEFAULT)
            config.showGuideButton = prefs.getBoolean(SHOW_GUIDE_BUTTON_PREF_STRING, SHOW_GUIDE_BUTTON_DEFAULT)
            config.halfHeightOscPortrait = prefs.getBoolean(HALF_HEIGHT_OSC_PORTRAIT_PREF_STRING, HALF_HEIGHT_OSC_PORTRAIT_DEFAULT)
            config.enableHdr = prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR) && !isShieldAtvFirmwareWithBrokenHdr()
            config.enableHdrHighBrightness = prefs.getBoolean(ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING, DEFAULT_ENABLE_HDR_HIGH_BRIGHTNESS)
            // HDR mode is stored as a String from ListPreference, default to HDR10 (1)
            config.hdrMode = try {
                (prefs.getString(HDR_MODE_PREF_STRING, DEFAULT_HDR_MODE.toString()) ?: DEFAULT_HDR_MODE.toString()).toInt()
            } catch (e: NumberFormatException) {
                DEFAULT_HDR_MODE
            }
            config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP)
            config.enablePerfOverlay = prefs.getBoolean(ENABLE_PERF_OVERLAY_STRING, DEFAULT_ENABLE_PERF_OVERLAY)
            config.perfOverlayLocked = prefs.getBoolean(PERF_OVERLAY_LOCKED_STRING, DEFAULT_PERF_OVERLAY_LOCKED)
            config.perfOverlayBgOpacity = prefs.getInt(PERF_OVERLAY_BG_OPACITY_STRING, DEFAULT_PERF_OVERLAY_BG_OPACITY).coerceIn(0, 100)

            // 读取性能覆盖层方向和位置设置
            val perfOverlayOrientationStr = prefs.getString(PERF_OVERLAY_ORIENTATION_STRING, DEFAULT_PERF_OVERLAY_ORIENTATION)
            config.perfOverlayOrientation = if ("vertical" == perfOverlayOrientationStr) {
                PerfOverlayOrientation.VERTICAL
            } else {
                PerfOverlayOrientation.HORIZONTAL
            }

            val perfOverlayPositionStr = prefs.getString(PERF_OVERLAY_POSITION_STRING, DEFAULT_PERF_OVERLAY_POSITION)
            config.perfOverlayPosition = when (perfOverlayPositionStr) {
                "bottom" -> PerfOverlayPosition.BOTTOM
                "top_left" -> PerfOverlayPosition.TOP_LEFT
                "top_right" -> PerfOverlayPosition.TOP_RIGHT
                "bottom_left" -> PerfOverlayPosition.BOTTOM_LEFT
                "bottom_right" -> PerfOverlayPosition.BOTTOM_RIGHT
                else -> PerfOverlayPosition.TOP
            }

            config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB)
            config.mouseEmulation = prefs.getBoolean(MOUSE_EMULATION_STRING, DEFAULT_MOUSE_EMULATION)
            config.mouseNavButtons = prefs.getBoolean(MOUSE_NAV_BUTTONS_STRING, DEFAULT_MOUSE_NAV_BUTTONS)
            config.unlockFps = prefs.getBoolean(UNLOCK_FPS_STRING, DEFAULT_UNLOCK_FPS)
            config.vibrateOsc = prefs.getBoolean(VIBRATE_OSC_PREF_STRING, DEFAULT_VIBRATE_OSC)
            config.vibrateFallbackToDevice = prefs.getBoolean(VIBRATE_FALLBACK_PREF_STRING, DEFAULT_VIBRATE_FALLBACK)
            config.vibrateFallbackToDeviceStrength = prefs.getInt(VIBRATE_FALLBACK_STRENGTH_PREF_STRING, DEFAULT_VIBRATE_FALLBACK_STRENGTH)
            config.enableAudioVibration = prefs.getBoolean(AUDIO_VIBRATION_ENABLE_PREF_STRING, DEFAULT_AUDIO_VIBRATION)
            config.audioVibrationStrength = prefs.getInt(AUDIO_VIBRATION_STRENGTH_PREF_STRING, DEFAULT_AUDIO_VIBRATION_STRENGTH)
            config.audioVibrationMode = prefs.getString(AUDIO_VIBRATION_MODE_PREF_STRING, DEFAULT_AUDIO_VIBRATION_MODE) ?: DEFAULT_AUDIO_VIBRATION_MODE
            config.audioVibrationScene = (prefs.getString(AUDIO_VIBRATION_SCENE_PREF_STRING, DEFAULT_AUDIO_VIBRATION_SCENE.toString()) ?: DEFAULT_AUDIO_VIBRATION_SCENE.toString()).toInt()
            config.flipFaceButtons = prefs.getBoolean(FLIP_FACE_BUTTONS_PREF_STRING, DEFAULT_FLIP_FACE_BUTTONS)
            config.touchscreenTrackpad = prefs.getBoolean(TOUCHSCREEN_TRACKPAD_PREF_STRING, DEFAULT_TOUCHSCREEN_TRACKPAD)
            config.enableLatencyToast = prefs.getBoolean(LATENCY_TOAST_PREF_STRING, DEFAULT_LATENCY_TOAST)
            config.enableStun = prefs.getBoolean(ENABLE_STUN_PREF_STRING, DEFAULT_ENABLE_STUN)

            val screenModeString = prefs.getString(SCREEN_COMBINATION_MODE_PREF_STRING, DEFAULT_SCREEN_COMBINATION_MODE) ?: DEFAULT_SCREEN_COMBINATION_MODE
            config.screenCombinationMode = try {
                screenModeString.toInt()
            } catch (e: NumberFormatException) {
                -1
            }

            // VDD screen combination mode defaults to -1 (use host config)
            // This is set dynamically from AppView based on display selection
            config.vddScreenCombinationMode = -1

            config.lockScreenAfterDisconnect = prefs.getBoolean(LOCK_SCREEN_AFTER_DISCONNECT_PREF_STRING, DEFAULT_LATENCY_TOAST)
            config.swapQuitAndDisconnect = prefs.getBoolean(SWAP_QUIT_AND_DISCONNECT_PERF_STRING, DEFAULT_LATENCY_TOAST)
            config.absoluteMouseMode = prefs.getBoolean(ABSOLUTE_MOUSE_MODE_PREF_STRING, DEFAULT_ABSOLUTE_MOUSE_MODE)

            // 对于没有触摸屏的设备，默认启用本地鼠标指针
            val hasTouchscreen = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            val defaultNativeMouse = if (hasTouchscreen) DEFAULT_ENABLE_NATIVE_MOUSE_POINTER else true
            config.enableNativeMousePointer = prefs.getBoolean(ENABLE_NATIVE_MOUSE_POINTER_PREF_STRING, defaultNativeMouse)

            // 如果没有触摸屏，强制设置为本地鼠标指针模式
            if (!hasTouchscreen) {
                config.enableNativeMousePointer = true
                config.enableEnhancedTouch = false
                config.touchscreenTrackpad = false
            }
            config.enableAudioFx = prefs.getBoolean(ENABLE_AUDIO_FX_PREF_STRING, DEFAULT_ENABLE_AUDIO_FX)
            config.enableSpatializer = prefs.getBoolean(ENABLE_SPATIALIZER_PREF_STRING, DEFAULT_ENABLE_SPATIALIZER)
            config.reduceRefreshRate = prefs.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, DEFAULT_REDUCE_REFRESH_RATE)
            config.fullRange = prefs.getBoolean(FULL_RANGE_PREF_STRING, DEFAULT_FULL_RANGE)
            config.gamepadTouchpadAsMouse = prefs.getBoolean(GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING, DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE)
            config.gamepadMotionSensors = prefs.getBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, DEFAULT_GAMEPAD_MOTION_SENSORS)
            config.gamepadMotionSensorsFallbackToDevice = prefs.getBoolean(GAMEPAD_MOTION_FALLBACK_PREF_STRING, DEFAULT_GAMEPAD_MOTION_FALLBACK)
            config.enableSimplifyPerfOverlay = false

            // 加载陀螺仪偏好设置
            config.gyroSensitivityMultiplier = prefs.getFloat(GYRO_SENSITIVITY_MULTIPLIER_PREF_STRING, DEFAULT_GYRO_SENSITIVITY_MULTIPLIER)
            config.gyroInvertXAxis = prefs.getBoolean(GYRO_INVERT_X_AXIS_PREF_STRING, DEFAULT_GYRO_INVERT_X_AXIS)
            config.gyroInvertYAxis = prefs.getBoolean(GYRO_INVERT_Y_AXIS_PREF_STRING, DEFAULT_GYRO_INVERT_Y_AXIS)
            config.gyroActivationKeyCode = prefs.getInt(GYRO_ACTIVATION_KEY_CODE_PREF_STRING, DEFAULT_GYRO_ACTIVATION_KEY_CODE)

            // Cards visibility (defaults to true)
            config.showBitrateCard = prefs.getBoolean(SHOW_BITRATE_CARD_PREF_STRING, true)
            config.showGyroCard = prefs.getBoolean(SHOW_GYRO_CARD_PREF_STRING, true)
            // 横屏时快捷卡片默认不开启
            val defaultQuickKeyCard = config.width <= config.height
            config.showQuickKeyCard = prefs.getBoolean(SHOW_QuickKeyCard, defaultQuickKeyCard)

            // 读取麦克风设置
            config.enableMic = prefs.getBoolean(ENABLE_MIC_PREF_STRING, DEFAULT_ENABLE_MIC)
            config.micBitrate = prefs.getInt(MIC_BITRATE_PREF_STRING, DEFAULT_MIC_BITRATE)
            config.micIconColor = prefs.getString(MIC_ICON_COLOR_PREF_STRING, DEFAULT_MIC_ICON_COLOR) ?: DEFAULT_MIC_ICON_COLOR

            // 读取ESC菜单设置
            config.enableEscMenu = prefs.getBoolean(ENABLE_ESC_MENU_PREF_STRING, DEFAULT_ENABLE_ESC_MENU)

            val escMenuKeyStr = prefs.getString(ESC_MENU_KEY_PREF_STRING, DEFAULT_ESC_MENU_KEY.toString()) ?: DEFAULT_ESC_MENU_KEY.toString()

            // 读取Start键菜单设置
            config.enableStartKeyMenu = prefs.getBoolean(ENABLE_START_KEY_MENU_PREF_STRING, DEFAULT_ENABLE_START_KEY_MENU)
            config.escMenuKey = try {
                escMenuKeyStr.toInt()
            } catch (e: NumberFormatException) {
                DEFAULT_ESC_MENU_KEY
            }

            // 读取控制流only模式设置
            config.controlOnly = prefs.getBoolean(CONTROL_ONLY_PREF_STRING, DEFAULT_CONTROL_ONLY)

            // 读取输出缓冲区队列大小设置
            config.outputBufferQueueLimit = prefs.getInt(OUTPUT_BUFFER_QUEUE_LIMIT_PREF_STRING, DEFAULT_OUTPUT_BUFFER_QUEUE_LIMIT)
            // 确保值在合理范围内 (1-5)
            if (config.outputBufferQueueLimit < 1) {
                config.outputBufferQueueLimit = 1
            } else if (config.outputBufferQueueLimit > 5) {
                config.outputBufferQueueLimit = 5
            }

            config.reverseResolution = prefs.getBoolean(REVERSE_RESOLUTION_PREF_STRING, DEFAULT_REVERSE_RESOLUTION)
            config.rotableScreen = prefs.getBoolean(ROTABLE_SCREEN_PREF_STRING, DEFAULT_ROTABLE_SCREEN)

            // 如果启用了分辨率反转，则交换宽度和高度
            if (config.reverseResolution) {
                val temp = config.width
                config.width = config.height
                config.height = temp
            }

            // 读取画面位置设置
            val posString = prefs.getString(SCREEN_POSITION_PREF_STRING, DEFAULT_SCREEN_POSITION) ?: DEFAULT_SCREEN_POSITION
            config.screenPosition = when (posString) {
                "top_left" -> ScreenPosition.TOP_LEFT
                "top_center" -> ScreenPosition.TOP_CENTER
                "top_right" -> ScreenPosition.TOP_RIGHT
                "center_left" -> ScreenPosition.CENTER_LEFT
                "center_right" -> ScreenPosition.CENTER_RIGHT
                "bottom_left" -> ScreenPosition.BOTTOM_LEFT
                "bottom_center" -> ScreenPosition.BOTTOM_CENTER
                "bottom_right" -> ScreenPosition.BOTTOM_RIGHT
                else -> ScreenPosition.CENTER
            }

            // 读取偏移百分比
            config.screenOffsetX = prefs.getInt(SCREEN_OFFSET_X_PREF_STRING, DEFAULT_SCREEN_OFFSET_X)
            config.screenOffsetY = prefs.getInt(SCREEN_OFFSET_Y_PREF_STRING, DEFAULT_SCREEN_OFFSET_Y)

            config.useExternalDisplay = prefs.getBoolean("use_external_display", false)

            // 读取悬浮球设置
            config.enableFloatBall = prefs.getBoolean(ENABLE_FLOAT_BALL_PREF_STRING, DEFAULT_ENABLE_FLOAT_BALL)
            config.floatBallAutoHideDelay = prefs.getInt(FLOAT_BALL_AUTO_HIDE_DELAY_PREF_STRING, DEFAULT_FLOAT_BALL_AUTO_HIDE_DELAY)

            // 读取悬浮球交互监听器设置
            config.floatBallSingleClickAction = prefs.getString(FLOAT_BALL_SINGLE_CLICK_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_SINGLE_CLICK_ACTION) ?: DEFAULT_FLOAT_BALL_SINGLE_CLICK_ACTION
            config.floatBallDoubleClickAction = prefs.getString(FLOAT_BALL_DOUBLE_CLICK_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_DOUBLE_CLICK_ACTION) ?: DEFAULT_FLOAT_BALL_DOUBLE_CLICK_ACTION
            config.floatBallLongClickAction = prefs.getString(FLOAT_BALL_LONG_CLICK_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_LONG_CLICK_ACTION) ?: DEFAULT_FLOAT_BALL_LONG_CLICK_ACTION
            config.floatBallSwipeUpAction = prefs.getString(FLOAT_BALL_SWIPE_UP_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_SWIPE_UP_ACTION) ?: DEFAULT_FLOAT_BALL_SWIPE_UP_ACTION
            config.floatBallSwipeDownAction = prefs.getString(FLOAT_BALL_SWIPE_DOWN_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_SWIPE_DOWN_ACTION) ?: DEFAULT_FLOAT_BALL_SWIPE_DOWN_ACTION
            config.floatBallSwipeLeftAction = prefs.getString(FLOAT_BALL_SWIPE_LEFT_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_SWIPE_LEFT_ACTION) ?: DEFAULT_FLOAT_BALL_SWIPE_LEFT_ACTION
            config.floatBallSwipeRightAction = prefs.getString(FLOAT_BALL_SWIPE_RIGHT_ACTION_PREF_STRING, DEFAULT_FLOAT_BALL_SWIPE_RIGHT_ACTION) ?: DEFAULT_FLOAT_BALL_SWIPE_RIGHT_ACTION

            // Runtime-only defaults; controlled via in-stream GameMenu
            config.gyroToRightStick = false
            config.gyroToMouse = false
            config.gyroFullDeflectionDps = 180.0f

            return config
        }
    }
}
