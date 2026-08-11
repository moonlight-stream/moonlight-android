package com.limelight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.preferences.PreferenceConfiguration

import org.json.JSONException
import org.json.JSONObject

/**
 * App Settings Manager
 * Used to store and retrieve the last streaming settings for each app
 */
class AppSettingsManager(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Save the last settings for an app
     */
    fun saveAppLastSettings(computerUuid: String, app: NvApp?, settings: PreferenceConfiguration?) {
        if (app == null || settings == null) return

        val key = generateKey(computerUuid, app.appId)

        try {
            val settingsJson = settingsToJson(settings)
            preferences.edit()
                    .putString(key, settingsJson.toString())
                    .commit()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieve the last settings for an app
     */
    fun getAppLastSettings(computerUuid: String, app: NvApp?): PreferenceConfiguration? {
        if (app == null) return null

        val key = generateKey(computerUuid, app.appId)
        val settingsJsonString = preferences.getString(key, null) ?: return null

        return try {
            val settingsJson = JSONObject(settingsJsonString)
            jsonToSettings(settingsJson)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get whether the "use last settings" toggle is enabled
     */
    val isUseLastSettingsEnabled: Boolean
        get() = preferences.getBoolean(KEY_USE_LAST_SETTINGS, false)

    /**
     * Set whether the "use last settings" toggle is enabled
     */
    fun setUseLastSettingsEnabled(enabled: Boolean) {
        preferences.edit()
                .putBoolean(KEY_USE_LAST_SETTINGS, enabled)
                .apply()
    }

    /**
     * Get the timestamp of the last settings for an app
     */
    fun getAppLastSettingsTimestamp(computerUuid: String, app: NvApp?): Long {
        if (app == null) return 0

        val key = generateKey(computerUuid, app.appId)
        val settingsJsonString = preferences.getString(key, null) ?: return 0

        return try {
            val settingsJson = JSONObject(settingsJsonString)
            settingsJson.optLong("timestamp", 0)
        } catch (e: JSONException) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Clear the last settings for an app
     */
    fun clearAppLastSettings(computerUuid: String, app: NvApp?) {
        if (app == null) return

        val key = generateKey(computerUuid, app.appId)
        preferences.edit()
                .remove(key)
                .apply()
    }

    /**
     * Clear all last settings for all apps
     */
    fun clearAllAppLastSettings() {
        preferences.edit()
                .clear()
                .apply()
    }

    private fun generateKey(computerUuid: String, appId: Int): String {
        return "${computerUuid}_${appId}"
    }

    /**
     * Get settings summary information
     */
    fun getSettingsSummary(computerUuid: String, app: NvApp): String {
        val settings = getAppLastSettings(computerUuid, app)
                ?: return context.getString(R.string.app_last_settings_none)

        val timestamp = getAppLastSettingsTimestamp(computerUuid, app)
        val timeStr = formatTimestamp(timestamp)

        val summary = StringBuilder()

        // Basic video settings
        summary.append(context.getString(R.string.setting_resolution, settings.width, settings.height))
        summary.append(" | ").append(context.getString(R.string.setting_fps, settings.fps))
        summary.append(" | ").append(context.getString(R.string.setting_bitrate, settings.bitrate))

        // Resolution scale
        if (settings.resolutionScale != 100) {
            summary.append(" | ").append(context.getString(R.string.setting_scale, settings.resolutionScale))
        }

        // Video format
        summary.append(" | ").append(context.getString(R.string.setting_format, settings.videoFormat.toString()))

        // HDR settings
        if (settings.enableHdr) {
            summary.append(" | ").append(context.getString(R.string.setting_hdr_enabled))
        }

        // Microphone settings
        if (settings.enableMic) {
            summary.append(" | ").append(context.getString(R.string.setting_mic_enabled, settings.micBitrate))
        }

        // Native mouse pointer
        if (settings.enableNativeMousePointer) {
            summary.append(" | ").append(context.getString(R.string.setting_native_mouse_enabled))
        }

        // Card configuration
        if (settings.showBitrateCard) {
            summary.append(" | ").append(context.getString(R.string.setting_bitrate_card_enabled))
        }
        if (settings.showAudioHapticsCard) {
            summary.append(" | ").append(context.getString(R.string.setting_audio_haptics_card_enabled))
        }
        if (settings.showGyroCard) {
            summary.append(" | ").append(context.getString(R.string.setting_gyro_card_enabled))
        }
        if (settings.showQuickKeyCard) {
            summary.append(" | ").append(context.getString(R.string.setting_QuickKey_card_enabled))
        }

        // Add time information
        summary.append(" (").append(timeStr).append(")")

        return summary.toString()
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) {
            return context.getString(R.string.time_unknown)
        }

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> context.getString(R.string.time_just_now)
            diff < 3600000 -> context.getString(R.string.time_minutes_ago, diff / 60000)
            diff < 86400000 -> context.getString(R.string.time_hours_ago, diff / 3600000)
            else -> context.getString(R.string.time_days_ago, diff / 86400000)
        }
    }

    private fun getVideoFormatPreferenceString(format: PreferenceConfiguration.FormatOption): String {
        return when (format) {
            PreferenceConfiguration.FormatOption.AUTO -> "auto"
            PreferenceConfiguration.FormatOption.FORCE_H264 -> "h264"
            PreferenceConfiguration.FormatOption.FORCE_HEVC -> "hevc"
            PreferenceConfiguration.FormatOption.FORCE_AV1 -> "av1"
        }
    }

    private fun parseVideoFormat(videoFormatStr: String?): PreferenceConfiguration.FormatOption {
        if (videoFormatStr == null) return PreferenceConfiguration.FormatOption.AUTO

        return when (videoFormatStr.lowercase()) {
            "h264", "force_h264" -> PreferenceConfiguration.FormatOption.FORCE_H264
            "hevc", "force_hevc" -> PreferenceConfiguration.FormatOption.FORCE_HEVC
            "av1", "force_av1" -> PreferenceConfiguration.FormatOption.FORCE_AV1
            else -> PreferenceConfiguration.FormatOption.AUTO
        }
    }

    @Throws(JSONException::class)
    private fun settingsToJson(settings: PreferenceConfiguration): JSONObject {
        return JSONObject().apply {
            put("resolution", "${settings.width}x${settings.height}")
            put("fps", settings.fps.toString())
            put("bitrate", settings.bitrate)
            put("resolutionScale", settings.resolutionScale)
            put("videoFormat", getVideoFormatPreferenceString(settings.videoFormat))
            put("enableHdr", settings.enableHdr)
            put("enableHdrHighBrightness", settings.enableHdrHighBrightness)
            put("enableMic", settings.enableMic)
            put("micBitrate", settings.micBitrate)
            put("micVolumeProcessingEnabled", settings.micVolumeProcessingEnabled)
            put("micGainEnabled", settings.micGainEnabled)
            put("micGainDb", settings.micGainDb)
            put("micBalanceEnabled", settings.micBalanceEnabled)
            put("micBalanceTargetPercent", settings.micBalanceTargetPercent)
            put("micVoiceEnhancementEnabled", settings.micVoiceEnhancementEnabled)
            put("enableNativeMousePointer", settings.enableNativeMousePointer)
            put("gyroSensitivityMultiplier", settings.gyroSensitivityMultiplier.toDouble())
            put("gyroInvertXAxis", settings.gyroInvertXAxis)
            put("gyroInvertYAxis", settings.gyroInvertYAxis)
            put("gyroActivationKeyCode", settings.gyroActivationKeyCode)
            put("showBitrateCard", settings.showBitrateCard)
            put("showAudioHapticsCard", settings.showAudioHapticsCard)
            put("showGyroCard", settings.showGyroCard)
            put("showQuickKeyCard", settings.showQuickKeyCard)
            put("timestamp", System.currentTimeMillis())
        }
    }

    @Throws(JSONException::class)
    private fun jsonToSettings(settingsJson: JSONObject): PreferenceConfiguration {
        val settings = PreferenceConfiguration()

        // Initialize all necessary fields to avoid NullPointerException
        settings.screenPosition = PreferenceConfiguration.ScreenPosition.CENTER
        settings.screenOffsetX = 0
        settings.screenOffsetY = 0
        settings.useExternalDisplay = false
        settings.enablePerfOverlay = false
        settings.reverseResolution = false
        settings.rotableScreen = false
        settings.showBitrateCard = false
        settings.showAudioHapticsCard = false
        settings.showGyroCard = false
        settings.showQuickKeyCard = false

        // Parse resolution string format "1920x1080"
        val resolutionStr = settingsJson.optString("resolution", "1920x1080")
        val resolutionParts = resolutionStr.split("x")
        if (resolutionParts.size == 2) {
            settings.width = resolutionParts[0].toInt()
            settings.height = resolutionParts[1].toInt()
        } else {
            settings.width = 1920
            settings.height = 1080
        }

        settings.fps = settingsJson.optString("fps", "60").toInt()
        settings.bitrate = settingsJson.optInt("bitrate", PreferenceConfiguration.getDefaultBitrate(context))
        settings.resolutionScale = settingsJson.optInt("resolutionScale", 100)
        settings.videoFormat = parseVideoFormat(settingsJson.optString("videoFormat", "auto"))

        settings.enableHdr = settingsJson.optBoolean("enableHdr", false)
        settings.enableHdrHighBrightness = settingsJson.optBoolean("enableHdrHighBrightness", false)
        settings.enableMic = settingsJson.optBoolean("enableMic", false)
        settings.micBitrate = settingsJson.optInt("micBitrate", 96)
        settings.micVolumeProcessingEnabled = settingsJson.optBoolean("micVolumeProcessingEnabled", false)
        settings.micGainEnabled = settingsJson.optBoolean("micGainEnabled", false)
        settings.micGainDb = settingsJson.optInt("micGainDb", 0).coerceIn(-20, 20)
        settings.micBalanceEnabled = settingsJson.optBoolean("micBalanceEnabled", false) &&
                !settings.micGainEnabled
        settings.micBalanceTargetPercent = settingsJson.optInt("micBalanceTargetPercent", 50).coerceIn(1, 100)
        settings.micVoiceEnhancementEnabled = settingsJson.optBoolean("micVoiceEnhancementEnabled", true)
        settings.enableNativeMousePointer = settingsJson.optBoolean("enableNativeMousePointer", false)
        settings.gyroSensitivityMultiplier = settingsJson.optDouble("gyroSensitivityMultiplier", 1.0).toFloat()
        settings.gyroInvertXAxis = settingsJson.optBoolean("gyroInvertXAxis", false)
        settings.gyroInvertYAxis = settingsJson.optBoolean("gyroInvertYAxis", false)
        settings.gyroActivationKeyCode = settingsJson.optInt("gyroActivationKeyCode", KeyEvent.KEYCODE_BUTTON_L2)
        settings.showBitrateCard = settingsJson.optBoolean("showBitrateCard", true)
        settings.showAudioHapticsCard = settingsJson.optBoolean("showAudioHapticsCard", false)
        settings.showGyroCard = settingsJson.optBoolean("showGyroCard", true)
        settings.showQuickKeyCard = settingsJson.optBoolean("showQuickKeyCard", true)

        return settings
    }

    /**
     * Create start Intent with last settings if enabled
     */
    fun createStartIntentWithLastSettingsIfEnabled(
            parent: Activity, app: NvApp,
            computer: ComputerDetails?,
            managerBinder: ComputerManagerService.ComputerManagerBinder,
            useVdd: Boolean? = null,
            forceResumeCurrentSession: Boolean = false
    ): Intent {
        val useLastSettingsEnabled = isUseLastSettingsEnabled

        if (useLastSettingsEnabled && computer != null) {
            val lastSettings = getAppLastSettings(computer.uuid!!, app)
            if (lastSettings != null) {
                return ServerHelper.createStartIntent(
                    parent,
                    app,
                    computer,
                    managerBinder,
                    lastSettings,
                    useVdd = useVdd,
                    forceResumeCurrentSession = forceResumeCurrentSession
                )
            }
        }

        return ServerHelper.createStartIntent(
            parent,
            app,
            computer!!,
            managerBinder,
            useVdd = useVdd,
            forceResumeCurrentSession = forceResumeCurrentSession
        )
    }

    /**
     * Read last settings from Intent and apply to PreferenceConfiguration
     */
    fun applyLastSettingsFromIntent(intent: Intent?, prefConfig: PreferenceConfiguration?): Boolean {
        if (intent == null || prefConfig == null) return false

        return try {
            val useLastSettings = intent.getBooleanExtra(INTENT_USE_LAST_SETTINGS, false)
            if (!useLastSettings) return false

            prefConfig.width = intent.getIntExtra(INTENT_LAST_SETTINGS_WIDTH, prefConfig.width)
            prefConfig.height = intent.getIntExtra(INTENT_LAST_SETTINGS_HEIGHT, prefConfig.height)
            prefConfig.fps = intent.getIntExtra(INTENT_LAST_SETTINGS_FPS, prefConfig.fps)
            prefConfig.bitrate = intent.getIntExtra(INTENT_LAST_SETTINGS_BITRATE, prefConfig.bitrate)
            prefConfig.resolutionScale = intent.getIntExtra(INTENT_LAST_SETTINGS_RESOLUTION_SCALE, prefConfig.resolutionScale)
            prefConfig.enableHdr = intent.getBooleanExtra(INTENT_LAST_SETTINGS_ENABLE_HDR, prefConfig.enableHdr)
            prefConfig.enableHdrHighBrightness = intent.getBooleanExtra(INTENT_LAST_SETTINGS_ENABLE_HDR_HIGH_BRIGHTNESS, prefConfig.enableHdrHighBrightness)
            prefConfig.enableMic = intent.getBooleanExtra(INTENT_LAST_SETTINGS_ENABLE_MIC, prefConfig.enableMic)
            prefConfig.micBitrate = intent.getIntExtra(INTENT_LAST_SETTINGS_MIC_BITRATE, prefConfig.micBitrate)
            prefConfig.micVolumeProcessingEnabled = intent.getBooleanExtra(INTENT_LAST_SETTINGS_MIC_VOLUME_PROCESSING, prefConfig.micVolumeProcessingEnabled)
            prefConfig.micGainEnabled = intent.getBooleanExtra(INTENT_LAST_SETTINGS_MIC_GAIN_ENABLED, prefConfig.micGainEnabled)
            prefConfig.micGainDb = intent.getIntExtra(INTENT_LAST_SETTINGS_MIC_GAIN_DB, prefConfig.micGainDb).coerceIn(-20, 20)
            prefConfig.micBalanceEnabled = intent.getBooleanExtra(INTENT_LAST_SETTINGS_MIC_BALANCE_ENABLED, prefConfig.micBalanceEnabled) &&
                    !prefConfig.micGainEnabled
            prefConfig.micBalanceTargetPercent = intent.getIntExtra(INTENT_LAST_SETTINGS_MIC_BALANCE_TARGET, prefConfig.micBalanceTargetPercent).coerceIn(1, 100)
            prefConfig.micVoiceEnhancementEnabled = intent.getBooleanExtra(INTENT_LAST_SETTINGS_MIC_VOICE_ENHANCEMENT, prefConfig.micVoiceEnhancementEnabled)
            prefConfig.enableNativeMousePointer = intent.getBooleanExtra(INTENT_LAST_SETTINGS_ENABLE_NATIVE_MOUSE, prefConfig.enableNativeMousePointer)
            prefConfig.gyroSensitivityMultiplier = intent.getFloatExtra(INTENT_LAST_SETTINGS_GYRO_SENSITIVITY, prefConfig.gyroSensitivityMultiplier)
            prefConfig.gyroInvertXAxis = intent.getBooleanExtra(INTENT_LAST_SETTINGS_GYRO_INVERT_X, prefConfig.gyroInvertXAxis)
            prefConfig.gyroInvertYAxis = intent.getBooleanExtra(INTENT_LAST_SETTINGS_GYRO_INVERT_Y, prefConfig.gyroInvertYAxis)
            prefConfig.gyroActivationKeyCode = intent.getIntExtra(INTENT_LAST_SETTINGS_GYRO_ACTIVATION_KEY, prefConfig.gyroActivationKeyCode)
            prefConfig.showBitrateCard = intent.getBooleanExtra(INTENT_LAST_SETTINGS_SHOW_BITRATE_CARD, prefConfig.showBitrateCard)
            prefConfig.showAudioHapticsCard = intent.getBooleanExtra(INTENT_LAST_SETTINGS_SHOW_AUDIO_HAPTICS_CARD, prefConfig.showAudioHapticsCard)
            prefConfig.showGyroCard = intent.getBooleanExtra(INTENT_LAST_SETTINGS_SHOW_GYRO_CARD, prefConfig.showGyroCard)
            prefConfig.showQuickKeyCard = intent.getBooleanExtra(INTENT_LAST_SETTINGS_SHOW_QuickKeyCard, prefConfig.showQuickKeyCard)

            prefConfig.videoFormat = parseVideoFormat(intent.getStringExtra(INTENT_LAST_SETTINGS_VIDEO_FORMAT))

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if the specified app has last settings
     */
    fun hasLastSettings(computerUuid: String, app: NvApp?): Boolean {
        if (app == null) return false

        val key = generateKey(computerUuid, app.appId)
        val settingsJsonString = preferences.getString(key, null)

        return !settingsJsonString.isNullOrEmpty()
    }

    companion object {
        private const val PREF_FILE_NAME = "app_last_settings"
        private const val KEY_USE_LAST_SETTINGS = "use_last_settings"

        // Intent constants
        private const val INTENT_USE_LAST_SETTINGS = "UseLastSettings"
        private const val INTENT_LAST_SETTINGS_WIDTH = "LastSettingsWidth"
        private const val INTENT_LAST_SETTINGS_HEIGHT = "LastSettingsHeight"
        private const val INTENT_LAST_SETTINGS_FPS = "LastSettingsFps"
        private const val INTENT_LAST_SETTINGS_BITRATE = "LastSettingsBitrate"
        private const val INTENT_LAST_SETTINGS_RESOLUTION_SCALE = "LastSettingsResolutionScale"
        private const val INTENT_LAST_SETTINGS_VIDEO_FORMAT = "LastSettingsVideoFormat"
        private const val INTENT_LAST_SETTINGS_ENABLE_HDR = "LastSettingsEnableHdr"
        private const val INTENT_LAST_SETTINGS_ENABLE_HDR_HIGH_BRIGHTNESS = "LastSettingsEnableHdrHighBrightness"
        private const val INTENT_LAST_SETTINGS_ENABLE_MIC = "LastSettingsEnableMic"
        private const val INTENT_LAST_SETTINGS_MIC_BITRATE = "LastSettingsMicBitrate"
        private const val INTENT_LAST_SETTINGS_MIC_VOLUME_PROCESSING = "LastSettingsMicVolumeProcessing"
        private const val INTENT_LAST_SETTINGS_MIC_GAIN_ENABLED = "LastSettingsMicGainEnabled"
        private const val INTENT_LAST_SETTINGS_MIC_GAIN_DB = "LastSettingsMicGainDb"
        private const val INTENT_LAST_SETTINGS_MIC_BALANCE_ENABLED = "LastSettingsMicBalanceEnabled"
        private const val INTENT_LAST_SETTINGS_MIC_BALANCE_TARGET = "LastSettingsMicBalanceTarget"
        private const val INTENT_LAST_SETTINGS_MIC_VOICE_ENHANCEMENT = "LastSettingsMicVoiceEnhancement"
        private const val INTENT_LAST_SETTINGS_ENABLE_NATIVE_MOUSE = "LastSettingsEnableNativeMouse"
        private const val INTENT_LAST_SETTINGS_GYRO_SENSITIVITY = "LastSettingsGyroSensitivity"
        private const val INTENT_LAST_SETTINGS_GYRO_INVERT_X = "LastSettingsGyroInvertX"
        private const val INTENT_LAST_SETTINGS_GYRO_INVERT_Y = "LastSettingsGyroInvertY"
        private const val INTENT_LAST_SETTINGS_GYRO_ACTIVATION_KEY = "LastSettingsGyroActivationKey"
        private const val INTENT_LAST_SETTINGS_SHOW_BITRATE_CARD = "LastSettingsShowBitrateCard"
        private const val INTENT_LAST_SETTINGS_SHOW_AUDIO_HAPTICS_CARD = "LastSettingsShowAudioHapticsCard"
        private const val INTENT_LAST_SETTINGS_SHOW_GYRO_CARD = "LastSettingsShowGyroCard"
        private const val INTENT_LAST_SETTINGS_SHOW_QuickKeyCard = "LastSettingsshowQuickKeyCard"

        /**
         * Add last settings to Intent
         */
        fun addLastSettingsToIntent(intent: Intent?, lastSettings: PreferenceConfiguration?) {
            if (intent == null || lastSettings == null) return

            intent.putExtra(INTENT_USE_LAST_SETTINGS, true)
            intent.putExtra(INTENT_LAST_SETTINGS_WIDTH, lastSettings.width)
            intent.putExtra(INTENT_LAST_SETTINGS_HEIGHT, lastSettings.height)
            intent.putExtra(INTENT_LAST_SETTINGS_FPS, lastSettings.fps)
            intent.putExtra(INTENT_LAST_SETTINGS_BITRATE, lastSettings.bitrate)
            intent.putExtra(INTENT_LAST_SETTINGS_RESOLUTION_SCALE, lastSettings.resolutionScale)
            intent.putExtra(INTENT_LAST_SETTINGS_VIDEO_FORMAT, lastSettings.videoFormat.toString())
            intent.putExtra(INTENT_LAST_SETTINGS_ENABLE_HDR, lastSettings.enableHdr)
            intent.putExtra(INTENT_LAST_SETTINGS_ENABLE_HDR_HIGH_BRIGHTNESS, lastSettings.enableHdrHighBrightness)
            intent.putExtra(INTENT_LAST_SETTINGS_ENABLE_MIC, lastSettings.enableMic)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_BITRATE, lastSettings.micBitrate)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_VOLUME_PROCESSING, lastSettings.micVolumeProcessingEnabled)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_GAIN_ENABLED, lastSettings.micGainEnabled)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_GAIN_DB, lastSettings.micGainDb)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_BALANCE_ENABLED, lastSettings.micBalanceEnabled)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_BALANCE_TARGET, lastSettings.micBalanceTargetPercent)
            intent.putExtra(INTENT_LAST_SETTINGS_MIC_VOICE_ENHANCEMENT, lastSettings.micVoiceEnhancementEnabled)
            intent.putExtra(INTENT_LAST_SETTINGS_ENABLE_NATIVE_MOUSE, lastSettings.enableNativeMousePointer)
            intent.putExtra(INTENT_LAST_SETTINGS_GYRO_SENSITIVITY, lastSettings.gyroSensitivityMultiplier)
            intent.putExtra(INTENT_LAST_SETTINGS_GYRO_INVERT_X, lastSettings.gyroInvertXAxis)
            intent.putExtra(INTENT_LAST_SETTINGS_GYRO_INVERT_Y, lastSettings.gyroInvertYAxis)
            intent.putExtra(INTENT_LAST_SETTINGS_GYRO_ACTIVATION_KEY, lastSettings.gyroActivationKeyCode)
            intent.putExtra(INTENT_LAST_SETTINGS_SHOW_BITRATE_CARD, lastSettings.showBitrateCard)
            intent.putExtra(INTENT_LAST_SETTINGS_SHOW_AUDIO_HAPTICS_CARD, lastSettings.showAudioHapticsCard)
            intent.putExtra(INTENT_LAST_SETTINGS_SHOW_GYRO_CARD, lastSettings.showGyroCard)
            intent.putExtra(INTENT_LAST_SETTINGS_SHOW_QuickKeyCard, lastSettings.showQuickKeyCard)
        }
    }
}
