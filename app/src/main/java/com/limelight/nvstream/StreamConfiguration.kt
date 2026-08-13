package com.limelight.nvstream

import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge

class StreamConfiguration private constructor() {

    var app: NvApp = NvApp("Steam")
        private set
    var width: Int = 1280
        private set
    var height: Int = 720
        private set
    var refreshRate: Int = 60
        private set
    var launchRefreshRate: Int = 60
        private set
    var clientRefreshRateX100: Int = 0
        private set
    var bitrate: Int = 10000
    private var hostResolutionScaleX100: Int = 100
    var sops: Boolean = true
        private set
    private var enableAdaptiveResolution: Boolean = false
    private var playLocalAudio: Boolean = false
    var maxPacketSize: Int = 1024
        private set
    var remote: Int = STREAM_CFG_AUTO
        private set
    var audioConfiguration: MoonBridge.AudioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO
        private set
    var supportedVideoFormats: Int = MoonBridge.VIDEO_FORMAT_H264
        private set
    var attachedGamepadMask: Int = 0
        private set
    private var encryptionFlags: Int = 0
    var colorRange: Int = 0
        private set
    var colorSpace: Int = 0
        private set
    var hdrMode: Int = 0
        private set
    var hdrBrightnessOverride: Boolean = false
        private set
    var hdrPeakBrightnessNits: Int = 1000
        private set
    private var persistGamepadsAfterDisconnect: Boolean = false
    private var enableMic: Boolean = false
    private var useVdd: Boolean? = null
    private var controlOnly: Boolean = false
    /** Requested audio codec — see [MoonBridge.AUDIO_CODEC_OPUS]/AC3/EAC3. Default = OPUS. */
    var audioCodec: Int = MoonBridge.AUDIO_CODEC_OPUS
        private set
    /** Requested AC3/E-AC3 bitrate in bits/sec. 0 = use server default. */
    var audioBitrate: Int = 0
        private set
    var customScreenMode: Int = -1
        private set

    val reqWidth: Int get() = width * hostResolutionScaleX100 / 100
    val reqHeight: Int get() = height * hostResolutionScaleX100 / 100
    val resolutionScale: Int get() = hostResolutionScaleX100
    val adaptiveResolutionEnabled: Boolean get() = enableAdaptiveResolution
    fun getPlayLocalAudio(): Boolean = playLocalAudio
    fun getPersistGamepadsAfterDisconnect(): Boolean = persistGamepadsAfterDisconnect
    fun getEnableMic(): Boolean = enableMic
    fun getUseVdd(): Boolean? = useVdd
    fun getControlOnly(): Boolean = controlOnly

    class Builder {
        private val config = StreamConfiguration()

        fun setApp(app: NvApp): Builder = apply { config.app = app }
        fun setRemoteConfiguration(remote: Int): Builder = apply { config.remote = remote }
        fun setResolution(width: Int, height: Int): Builder = apply { config.width = width; config.height = height }
        fun setRefreshRate(refreshRate: Int): Builder = apply { config.refreshRate = refreshRate }
        fun setLaunchRefreshRate(refreshRate: Int): Builder = apply { config.launchRefreshRate = refreshRate }
        fun setBitrate(bitrate: Int): Builder = apply { config.bitrate = bitrate }
        fun setResolutionScale(scale: Int): Builder = apply { config.hostResolutionScaleX100 = scale }
        fun setEnableSops(enable: Boolean): Builder = apply { config.sops = enable }
        fun enableAdaptiveResolution(enable: Boolean): Builder = apply { config.enableAdaptiveResolution = enable }
        fun enableLocalAudioPlayback(enable: Boolean): Builder = apply { config.playLocalAudio = enable }
        fun setMaxPacketSize(maxPacketSize: Int): Builder = apply { config.maxPacketSize = maxPacketSize }
        fun setAttachedGamepadMask(attachedGamepadMask: Int): Builder = apply { config.attachedGamepadMask = attachedGamepadMask }

        fun setAttachedGamepadMaskByCount(gamepadCount: Int): Builder = apply {
            config.attachedGamepadMask = 0
            for (i in 0 until 4) {
                if (gamepadCount > i) {
                    config.attachedGamepadMask = config.attachedGamepadMask or (1 shl i)
                }
            }
        }

        fun setPersistGamepadsAfterDisconnect(value: Boolean): Builder = apply { config.persistGamepadsAfterDisconnect = value }
        fun setClientRefreshRateX100(refreshRateX100: Int): Builder = apply { config.clientRefreshRateX100 = refreshRateX100 }
        fun setAudioConfiguration(audioConfig: MoonBridge.AudioConfiguration): Builder = apply { config.audioConfiguration = audioConfig }
        fun setSupportedVideoFormats(supportedVideoFormats: Int): Builder = apply { config.supportedVideoFormats = supportedVideoFormats }
        fun setColorRange(colorRange: Int): Builder = apply { config.colorRange = colorRange }
        fun setColorSpace(colorSpace: Int): Builder = apply { config.colorSpace = colorSpace }

        /**
         * Sets the HDR mode for the video stream.
         * @param hdrMode Client HDR selection. HDR10+ is mapped to the HDR10/PQ host protocol value.
         */
        fun setHdrMode(hdrMode: Int): Builder = apply {
            config.hdrMode = HdrModePolicy.toProtocolMode(hdrMode)
        }
        fun setHdrBrightnessOverride(enabled: Boolean, peakBrightnessNits: Int): Builder = apply {
            config.hdrBrightnessOverride = enabled
            config.hdrPeakBrightnessNits = peakBrightnessNits.coerceIn(300, 4000)
        }
        fun setUseVdd(value: Boolean?): Builder = apply { config.useVdd = value }
        fun setEnableMic(enable: Boolean): Builder = apply { config.enableMic = enable }
        fun setControlOnly(controlOnly: Boolean): Builder = apply { config.controlOnly = controlOnly }
        fun setAudioCodec(codec: Int): Builder = apply { config.audioCodec = codec }
        fun setAudioBitrate(bitrate: Int): Builder = apply { config.audioBitrate = bitrate }
        fun setCustomScreenMode(customScreenMode: Int): Builder = apply { config.customScreenMode = customScreenMode }

        fun build(): StreamConfiguration = config
    }

    companion object {
        const val INVALID_APP_ID = 0
        const val STREAM_CFG_LOCAL = 0
        const val STREAM_CFG_REMOTE = 1
        const val STREAM_CFG_AUTO = 2
    }
}
