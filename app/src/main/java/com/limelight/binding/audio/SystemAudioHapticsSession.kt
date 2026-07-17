package com.limelight.binding.audio

import android.media.AudioAttributes
import android.media.AudioTrack
import com.limelight.LimeLog
import com.moonlight.haptics.android.AndroidAudioCoupledHaptics
import java.io.Closeable

/** Host lifecycle glue for the SDK's Android audio-coupled haptics adapter. */
internal class SystemAudioHapticsSession(
    private val requested: Boolean,
    private val onActiveChanged: (Boolean) -> Unit = {}
) : Closeable {
    private val adapter = AndroidAudioCoupledHaptics(requested)
    private var active = false
    private var capabilityLogged = false

    fun configureAudioAttributes(builder: AudioAttributes.Builder) {
        val configured = adapter.configureAudioAttributes(builder)
        if (requested && !capabilityLogged) {
            capabilityLogged = true
            LimeLog.info(
                "AudioHaptics: system audio-coupled requested=true, " +
                    "available=${adapter.isAvailable}, attributesConfigured=$configured"
            )
        }
    }

    fun attach(track: AudioTrack): Boolean {
        val attached = adapter.attach(track)
        updateActive(attached)
        if (requested) {
            LimeLog.info(
                "AudioHaptics: system audio-coupled attached=$attached, " +
                    "audioSession=${track.audioSessionId}, status=${adapter.lastAttachStatus}"
            )
        }
        return attached
    }

    fun detach() {
        adapter.detach()
        updateActive(false)
    }

    override fun close() {
        adapter.close()
        updateActive(false)
    }

    private fun updateActive(value: Boolean) {
        if (active == value) return
        active = value
        onActiveChanged(value)
    }
}
