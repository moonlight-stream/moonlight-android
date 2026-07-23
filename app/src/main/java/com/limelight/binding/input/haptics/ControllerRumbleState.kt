package com.limelight.binding.input.haptics

/**
 * Platform-neutral controller rumble amplitudes.
 *
 * Values are normalized to [0, 1]. Invalid floating-point values are treated as zero. Trigger
 * motors stay on the host-authoritative path and are intentionally not part of this base mixer.
 */
internal class ControllerRumbleState(
    lowFrequency: Float = 0f,
    highFrequency: Float = 0f
) {
    val lowFrequency: Float = normalizeAmplitude(lowFrequency)
    val highFrequency: Float = normalizeAmplitude(highFrequency)

    val isZero: Boolean
        get() = lowFrequency == 0f && highFrequency == 0f

    internal fun scaled(gain: Float): ControllerRumbleState {
        val normalizedGain = normalizeAmplitude(gain)
        return ControllerRumbleState(
            lowFrequency * normalizedGain,
            highFrequency * normalizedGain
        )
    }

    internal fun maxWith(other: ControllerRumbleState): ControllerRumbleState =
        ControllerRumbleState(
            maxOf(lowFrequency, other.lowFrequency),
            maxOf(highFrequency, other.highFrequency)
        )

    override fun equals(other: Any?): Boolean =
        other is ControllerRumbleState &&
            lowFrequency == other.lowFrequency &&
            highFrequency == other.highFrequency

    override fun hashCode(): Int {
        var result = lowFrequency.hashCode()
        result = 31 * result + highFrequency.hashCode()
        return result
    }

    override fun toString(): String =
        "ControllerRumbleState(lowFrequency=$lowFrequency, highFrequency=$highFrequency)"

    companion object {
        val ZERO = ControllerRumbleState()

        private fun normalizeAmplitude(value: Float): Float = when {
            !value.isFinite() || value <= 0f -> 0f
            value >= 1f -> 1f
            else -> value
        }
    }
}
