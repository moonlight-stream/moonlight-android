package com.limelight.binding.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge

/**
 * Raw PCM_S16 (LPCM) passthrough renderer.
 *
 * Hands little-endian interleaved 16-bit PCM directly to the platform mixer
 * via [AudioFormat.ENCODING_PCM_16BIT]. No codec block delay, no AVR decoder,
 * lowest possible audio latency at the cost of bandwidth (~1.5 Mbps stereo,
 * ~4.6 Mbps 5.1).
 *
 * Server side guarantees 5 ms framing (240 samples / channel @ 48 kHz).
 */
class PcmPassthroughRenderer(
    private val context: Context,
    private val bufferBytes: Int = 8 * 1024,
    enableSystemAudioHaptics: Boolean = false,
    onSystemAudioHapticsActiveChanged: (Boolean) -> Unit = {}
) : AudioRenderer {

    private var track: AudioTrack? = null
    private val systemAudioHaptics = SystemAudioHapticsSession(
        enableSystemAudioHaptics,
        onSystemAudioHapticsActiveChanged
    )

    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
        codec: Int,
        bitrate: Int
    ): Int {
        if (codec != MoonBridge.AUDIO_CODEC_PCM_S16) {
            return -1
        }

        val channelMask = when (audioConfiguration.channelCount) {
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            else -> {
                LimeLog.severe("PcmPassthroughRenderer: unsupported channels=${audioConfiguration.channelCount}")
                return -1
            }
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .also { systemAudioHaptics.configureAudioAttributes(it) }
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        try {
            // Use minBufferSize as floor; user-configurable cap on top to
            // bound jitter buffer / latency.
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuf, bufferBytes)

            val builder = AudioTrack.Builder()
                .setAudioFormat(format)
                .setAudioAttributes(attributes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)

            // FLAG_LOW_LATENCY hint (API 26+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                } catch (_: Throwable) {
                    // Optional hint; ignore if rejected.
                }
            }

            track = builder.build()
            systemAudioHaptics.attach(track!!)
            track!!.play()
            LimeLog.info("PcmPassthroughRenderer: PCM_S16 @${sampleRate} Hz, ${audioConfiguration.channelCount}ch, buffer=$bufferSize B")
            return 0
        } catch (e: Exception) {
            LimeLog.severe("PcmPassthroughRenderer: AudioTrack create failed: ${e.message}")
            systemAudioHaptics.detach()
            try {
                track?.release()
            } catch (_: Exception) {}
            track = null
            return -2
        }
    }

    override fun start() {
        // play() already in setup().
    }

    override fun stop() {
        try {
            systemAudioHaptics.detach()
            track?.pause()
            track?.flush()
        } catch (_: Exception) {}
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        // Unused.
    }

    override fun playEncodedAudio(audioData: ByteArray, length: Int) {
        val t = track ?: return
        if (length <= 0) return
        try {
            var offset = 0
            while (offset < length) {
                val written = t.write(audioData, offset, length - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    LimeLog.warning("PcmPassthroughRenderer.write returned $written")
                    break
                }
                offset += written
            }
        } catch (e: Exception) {
            LimeLog.warning("PcmPassthroughRenderer.write failed: ${e.message}")
        }
    }

    override fun cleanup() {
        try {
            systemAudioHaptics.close()
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
        track = null
    }
}
