package com.limelight.binding.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.os.Build

import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge

class AndroidAudioRenderer(
    private val context: Context,
    private val enableAudioFx: Boolean,
    private val enableSpatializer: Boolean,
    enableSystemAudioHaptics: Boolean = false,
    onSystemAudioHapticsActiveChanged: (Boolean) -> Unit = {},
    private val onAudioPresentationClock: (framePosition: Long, systemNanoTime: Long, sampleRate: Int) -> Unit = { _, _, _ -> }
) : AudioRenderer {

    private var track: AudioTrack? = null
    private var spatializer: Spatializer? = null
    private val audioTimestamp = AudioTimestamp()
    private val systemAudioHaptics = SystemAudioHapticsSession(
        enableSystemAudioHaptics,
        onSystemAudioHapticsActiveChanged
    )

    // 保存当前的静音状态
    private var isMuted = false
    // 保存目标音量增益。默认 1.0f (100%)
    private var mTargetVolume = 1.0f
    // 标记是否处于暂停丢包状态
    @Volatile
    private var isProcessingPaused = false

    // 保存初始化的配置参数，用于重建
    private var savedAudioConfig: MoonBridge.AudioConfiguration? = null
    private var savedSampleRate = 0
    private var savedSamplesPerFrame = 0
    private var savedChannelCount = 0
    private var decodedStreamFramePosition = 0L
    private var trackStreamFrameOffset = 0L

    private fun createAudioTrack(channelConfig: Int, sampleRate: Int, bufferSize: Int, lowLatency: Boolean): AudioTrack {
        val attributesBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
        systemAudioHaptics.configureAudioAttributes(attributesBuilder)

        // Enable spatialization attribute if supported and requested
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enableSpatializer) {
            attributesBuilder.setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO)
        }

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Use FLAG_LOW_LATENCY on L through N
            if (lowLatency) {
                @Suppress("DEPRECATION")
                attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackBuilder = AudioTrack.Builder()
                .setAudioFormat(format)
                .setAudioAttributes(attributesBuilder.build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)

            // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
            if (lowLatency) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            trackBuilder.build()
        } else {
            AudioTrack(
                attributesBuilder.build(),
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }
    }

    private fun initializeAudioTrackInternal(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int
    ): Int {
        // AudioTrack frame positions restart from zero whenever the track is
        // rebuilt. Keep them in the native IR stream's cumulative time origin.
        trackStreamFrameOffset = decodedStreamFramePosition
        val channelConfig: Int
        val bytesPerFrame: Int

        when (audioConfiguration.channelCount) {
            2 -> channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            4 -> channelConfig = AudioFormat.CHANNEL_OUT_QUAD
            6 -> channelConfig = AudioFormat.CHANNEL_OUT_5POINT1
            8 -> channelConfig = 0x000018fc // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            12 -> channelConfig = 0x0003d8fc // 7.1.4 surround
            else -> {
                LimeLog.severe("Decoder returned unhandled channel count")
                return -1
            }
        }

        LimeLog.info("Audio channel config: " + String.format("0x%X", channelConfig))

        bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2

        for (i in 0 until 4) {
            val lowLatency: Boolean
            val bufferSize: Int

            when (i) {
                0, 1 -> lowLatency = true
                else -> lowLatency = false
            }

            when (i) {
                0, 2 -> bufferSize = bytesPerFrame * 2
                else -> {
                    val minBuf = AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val rawSize = maxOf(minBuf, bytesPerFrame * 2)
                    // Round to next frame
                    bufferSize = ((rawSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame
                }
            }

            // Skip low latency options if hardware sample rate doesn't match the content
            if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate && lowLatency) {
                continue
            }

            // Skip low latency options when using audio effects or spatializer
            if ((enableAudioFx || enableSpatializer) && lowLatency) {
                continue
            }

            try {
                track = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency)
                systemAudioHaptics.attach(track!!)
                track!!.play()

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: ${bufferSize} lowLatency=${lowLatency} spatializer=${enableSpatializer}")
                break
            } catch (e: Exception) {
                e.printStackTrace()
                systemAudioHaptics.detach()
                try {
                    track?.release()
                    track = null
                } catch (_: Exception) {}
            }
        }

        if (track == null) {
            return -2
        }

        // Initialize Spatializer if supported and enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enableSpatializer) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                spatializer = audioManager.spatializer

                if (spatializer != null && spatializer!!.isAvailable) {
                    val attributes = track!!.audioAttributes
                    val trackFormat = track!!.format

                    if (spatializer!!.canBeSpatialized(attributes, trackFormat)) {
                        LimeLog.info("Spatializer is available and track can be spatialized")
                        LimeLog.info("Spatializer enabled: " + spatializer!!.isEnabled)
                        LimeLog.info("Spatializer level: " + spatializer!!.immersiveAudioLevel)
                    } else {
                        LimeLog.warning("Spatializer is available but track cannot be spatialized")
                        spatializer = null
                    }
                } else {
                    LimeLog.info("Spatializer is not available on this device")
                    spatializer = null
                }
            } catch (e: Exception) {
                LimeLog.warning("Failed to initialize Spatializer: " + e.message)
                e.printStackTrace()
                spatializer = null
            }
        }

        return 0
    }

    override fun setup(audioConfiguration: MoonBridge.AudioConfiguration, sampleRate: Int, samplesPerFrame: Int, codec: Int, bitrate: Int): Int {
        clearAudioPresentationClock()
        // This renderer only handles Opus PCM output. Non-Opus codecs (AC3 / E-AC3)
        // are routed through a dedicated passthrough renderer; refuse setup so the
        // caller can fall back appropriately.
        if (codec != MoonBridge.AUDIO_CODEC_OPUS) {
            LimeLog.warning("AndroidAudioRenderer: refusing non-Opus codec=$codec (use Ac3PassthroughRenderer)")
            return -1
        }
        // 保存配置，供 resume 时使用
        this.savedAudioConfig = audioConfiguration
        this.savedSampleRate = sampleRate
        this.savedSamplesPerFrame = samplesPerFrame
        this.savedChannelCount = audioConfiguration.channelCount
        decodedStreamFramePosition = 0L
        trackStreamFrameOffset = 0L

        return initializeAudioTrackInternal(audioConfiguration, sampleRate, samplesPerFrame)
    }

    // --- 暂停处理 ---
    fun pauseProcessing() {
        LimeLog.info("Audio: Pausing processing (releasing AudioTrack)")
        isProcessingPaused = true
        clearAudioPresentationClock()

        // 释放 Spatializer
        spatializer = null

        // 释放 AudioTrack
        val currentTrack = track
        if (currentTrack != null) {
            try {
                // 如果开启了 AudioFx，先关闭 session
                if (enableAudioFx) {
                    val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentTrack.audioSessionId)
                    i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    context.sendBroadcast(i)
                }

                systemAudioHaptics.detach()
                currentTrack.pause()
                currentTrack.flush()
                currentTrack.release()
            } catch (e: Exception) {
                LimeLog.warning("Error releasing audio track: " + e.message)
            }
            track = null
        }
    }

    // --- 恢复处理 ---
    fun resumeProcessing() {
        val config = savedAudioConfig
        if (config == null) {
            LimeLog.warning("Cannot resume audio: no saved configuration")
            return
        }

        LimeLog.info("Audio: Resuming processing...")
        clearAudioPresentationClock()

        // 1. 重建 AudioTrack
        val res = initializeAudioTrackInternal(config, savedSampleRate, savedSamplesPerFrame)
        if (res != 0) {
            LimeLog.severe("Failed to recreate AudioTrack: $res")
            return
        }

        // 2. 恢复 AudioFx (如果开启)
        val currentTrack = track
        if (currentTrack != null && enableAudioFx) {
            try {
                val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentTrack.audioSessionId)
                i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME)
                context.sendBroadcast(i)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. 恢复音量
        if (currentTrack != null) {
            try {
                currentTrack.play()

                val vol = if (isMuted) 0.0f else mTargetVolume
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    currentTrack.setVolume(vol)
                } else {
                    @Suppress("DEPRECATION")
                    currentTrack.setStereoVolume(vol, vol)
                }
            } catch (e: Exception) {
                LimeLog.warning("Error restoring audio state: " + e.message)
            }
        }

        // 4. 最后一步：解除暂停标志，允许数据写入
        isProcessingPaused = false
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        val frameCount = if (savedChannelCount > 0) {
            audioData.size.toLong() / savedChannelCount
        } else {
            0L
        }
        if (isProcessingPaused) {
            decodedStreamFramePosition += frameCount
            return // 丢弃数据
        }

        val currentTrack = track
        if (currentTrack == null) {
            decodedStreamFramePosition += frameCount
            return
        }

        // Backlog shedding happens in the native decode callback before bass/haptic
        // analysis, so every PCM frame reaching this method must also reach AudioTrack.
        val writtenSamples = currentTrack.write(audioData, 0, audioData.size)
        if (writtenSamples > 0 && savedSampleRate > 0 && currentTrack.getTimestamp(audioTimestamp)) {
            onAudioPresentationClock(
                trackStreamFrameOffset + audioTimestamp.framePosition,
                audioTimestamp.nanoTime,
                savedSampleRate
            )
        }
        decodedStreamFramePosition += frameCount
    }

    override fun start() {
        val currentTrack = track
        if (currentTrack != null && enableAudioFx) {
            val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentTrack.audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME)
            context.sendBroadcast(i)
        }
    }

    override fun stop() {
        clearAudioPresentationClock()
        systemAudioHaptics.detach()
        val currentTrack = track
        if (currentTrack != null && enableAudioFx) {
            val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentTrack.audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(i)
        }
    }

    override fun cleanup() {
        clearAudioPresentationClock()
        systemAudioHaptics.close()
        spatializer = null
        track?.let {
            it.pause()
            it.flush()
            it.release()
        }
        track = null
    }

    private fun clearAudioPresentationClock() {
        onAudioPresentationClock(-1L, -1L, 0)
    }

    /**
     * 设置是否静音
     * @param muted true=静音 (增益设为0), false=恢复 (恢复到 mTargetVolume)
     */
    fun setMuted(muted: Boolean) {
        if (this.isMuted == muted) return
        this.isMuted = muted

        if (isProcessingPaused) return
        val currentTrack = track ?: return

        try {
            val vol = if (muted) 0.0f else mTargetVolume
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                currentTrack.setVolume(vol)
            } else {
                @Suppress("DEPRECATION")
                currentTrack.setStereoVolume(vol, vol)
            }
        } catch (e: Exception) {
            LimeLog.warning("Failed to set volume: " + e.message)
        }
    }
}
