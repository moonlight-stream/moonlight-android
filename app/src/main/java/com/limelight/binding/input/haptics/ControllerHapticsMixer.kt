package com.limelight.binding.input.haptics

/** Sources that may contribute to a controller's final rumble state. */
internal enum class RumbleSource {
    HOST,
    AUDIO,
    TEST
}

/** A mixed output together with metadata useful to the Android routing layer. */
internal data class MixedRumbleState(
    val controllerNumber: Short,
    val output: ControllerRumbleState
) {
    val isZero: Boolean
        get() = output.isZero
}

/**
 * Source-aware, platform-neutral controller rumble mixer.
 *
 * Time is supplied by callers so expiry behavior is deterministic and can share the Android
 * output scheduler's clock. [submit] uses the AUDIO continuous slot when its source is AUDIO;
 * transient audio pulses use [submitAudioTransient] and expire independently.
 *
 * This class is intentionally not synchronized. All calls should be made from the controller
 * output thread (or otherwise externally serialized).
 */
internal class ControllerHapticsMixer(
    hostActiveAudioGain: Float = DEFAULT_HOST_ACTIVE_AUDIO_GAIN
) {
    private data class TimedState(
        val state: ControllerRumbleState,
        val expiresAtMs: Long?
    ) {
        fun isExpired(nowMs: Long): Boolean = expiresAtMs != null && nowMs >= expiresAtMs
    }

    private data class SourceStates(
        var host: TimedState? = null,
        var audioContinuous: TimedState? = null,
        var audioTransient: TimedState? = null,
        var test: TimedState? = null
    )

    private val audioGainWhenHostActive =
        ControllerRumbleState(hostActiveAudioGain).lowFrequency
    private val controllers = linkedMapOf<Short, SourceStates>()

    /**
     * Replaces one source's current value and returns the newly mixed controller output.
     *
     * For AUDIO this updates only the continuous component. A null expiry means the value remains
     * active until it is replaced or cleared. An expiry at or before [nowMs] behaves as a clear.
     */
    fun submit(
        controllerNumber: Short,
        source: RumbleSource,
        state: ControllerRumbleState,
        nowMs: Long,
        expiresAtMs: Long? = null
    ): MixedRumbleState {
        val sources = controllers.getOrPut(controllerNumber) { SourceStates() }
        expire(sources, nowMs)
        val timedState = state.toTimedState(nowMs, expiresAtMs)

        when (source) {
            RumbleSource.HOST -> sources.host = timedState
            RumbleSource.AUDIO -> sources.audioContinuous = timedState
            RumbleSource.TEST -> sources.test = timedState
        }

        return mix(controllerNumber, sources)
    }

    fun submitAudioContinuous(
        controllerNumber: Short,
        state: ControllerRumbleState,
        nowMs: Long,
        expiresAtMs: Long? = null
    ): MixedRumbleState =
        submit(controllerNumber, RumbleSource.AUDIO, state, nowMs, expiresAtMs)

    /** Replaces only the transient AUDIO component, preserving the continuous component. */
    fun submitAudioTransient(
        controllerNumber: Short,
        state: ControllerRumbleState,
        nowMs: Long,
        expiresAtMs: Long
    ): MixedRumbleState {
        val sources = controllers.getOrPut(controllerNumber) { SourceStates() }
        expire(sources, nowMs)
        sources.audioTransient = state.toTimedState(nowMs, expiresAtMs)
        return mix(controllerNumber, sources)
    }

    /** Clears one source without changing any other source. */
    fun clearSource(
        controllerNumber: Short,
        source: RumbleSource,
        nowMs: Long
    ): MixedRumbleState {
        val sources = controllers.getOrPut(controllerNumber) { SourceStates() }
        expire(sources, nowMs)

        when (source) {
            RumbleSource.HOST -> sources.host = null
            RumbleSource.AUDIO -> {
                sources.audioContinuous = null
                sources.audioTransient = null
            }
            RumbleSource.TEST -> sources.test = null
        }

        return mix(controllerNumber, sources)
    }

    /** Returns the current mixed value after applying expiries at [nowMs]. */
    fun mixedState(controllerNumber: Short, nowMs: Long): MixedRumbleState {
        val sources = controllers.getOrPut(controllerNumber) { SourceStates() }
        expire(sources, nowMs)
        return mix(controllerNumber, sources)
    }

    /**
     * Applies expiries for all tracked controllers.
     *
     * Only controllers with at least one newly expired component are returned, allowing a sink to
     * emit the resulting fallback or stop value without resending unchanged controllers.
     */
    fun pruneExpired(nowMs: Long): List<MixedRumbleState> {
        val changed = ArrayList<MixedRumbleState>()
        controllers.forEach { (controllerNumber, sources) ->
            if (expire(sources, nowMs)) {
                changed += mix(controllerNumber, sources)
            }
        }
        return changed
    }

    /** Removes all controller state and returns one stop output for each previously tracked target. */
    fun clearAll(): List<MixedRumbleState> {
        val stopped = controllers.keys.map(::zeroOutput)
        controllers.clear()
        return stopped
    }

    /** Returns the earliest outstanding expiry, or null when no timed component is active. */
    fun nextExpiryAtMs(): Long? =
        controllers.values.asSequence()
            .flatMap { sources ->
                sequenceOf(
                    sources.host,
                    sources.audioContinuous,
                    sources.audioTransient,
                    sources.test
                )
            }
            .mapNotNull { it?.expiresAtMs }
            .minOrNull()

    private fun ControllerRumbleState.toTimedState(
        nowMs: Long,
        expiresAtMs: Long?
    ): TimedState? =
        if (isZero || expiresAtMs != null && expiresAtMs <= nowMs) {
            null
        } else {
            TimedState(this, expiresAtMs)
        }

    private fun expire(sources: SourceStates, nowMs: Long): Boolean {
        var changed = false

        if (sources.host?.isExpired(nowMs) == true) {
            sources.host = null
            changed = true
        }
        if (sources.audioContinuous?.isExpired(nowMs) == true) {
            sources.audioContinuous = null
            changed = true
        }
        if (sources.audioTransient?.isExpired(nowMs) == true) {
            sources.audioTransient = null
            changed = true
        }
        if (sources.test?.isExpired(nowMs) == true) {
            sources.test = null
            changed = true
        }

        return changed
    }

    private fun mix(controllerNumber: Short, sources: SourceStates): MixedRumbleState {
        val host = sources.host?.state ?: ControllerRumbleState.ZERO
        val audio = (sources.audioContinuous?.state ?: ControllerRumbleState.ZERO)
            .maxWith(sources.audioTransient?.state ?: ControllerRumbleState.ZERO)
        val test = sources.test?.state ?: ControllerRumbleState.ZERO
        val hostActive = !host.isZero
        val mixedAudio = if (hostActive) audio.scaled(audioGainWhenHostActive) else audio

        return MixedRumbleState(
            controllerNumber = controllerNumber,
            output = host.maxWith(mixedAudio).maxWith(test)
        )
    }

    private fun zeroOutput(controllerNumber: Short): MixedRumbleState =
        MixedRumbleState(
            controllerNumber = controllerNumber,
            output = ControllerRumbleState.ZERO
        )

    companion object {
        const val DEFAULT_HOST_ACTIVE_AUDIO_GAIN = 0.25f
    }
}
