package com.limelight.binding.input.touchpad

import kotlin.math.max

/**
 * Converts hardware-reported touchscreen pressure into a clickpad button state.
 *
 * The baseline is calibrated for every gesture. This deliberately avoids treating a constant
 * pressure value (a common touchscreen implementation) as a click. Hysteresis prevents noisy
 * samples around the press threshold from rapidly toggling the button.
 */
internal class ScreenDs5PressureClickDetector {
    private var baseline = Float.NaN
    private var pressed = false

    fun begin(pressure: Float, deepPress: Boolean = false): Boolean? {
        baseline = validPressure(pressure) ?: Float.NaN
        pressed = false
        return update(pressure, deepPress)
    }

    /** Returns the new button state only when it changes. */
    fun update(pressure: Float, deepPress: Boolean = false): Boolean? {
        val sample = validPressure(pressure)
        if (deepPress && !pressed) {
            pressed = true
            return true
        }
        if (sample == null || baseline.isNaN()) return null

        // Let a lighter early sample refine the per-gesture resting pressure.
        if (!pressed && sample < baseline) baseline = sample

        val pressThreshold = max(MIN_ABSOLUTE_PRESSURE, baseline + PRESS_DELTA)
        val releaseThreshold = max(MIN_ABSOLUTE_PRESSURE - HYSTERESIS, baseline + RELEASE_DELTA)
        return when {
            !pressed && sample >= pressThreshold -> {
                pressed = true
                true
            }
            pressed && !deepPress && sample <= releaseThreshold -> {
                pressed = false
                false
            }
            else -> null
        }
    }

    fun end(): Boolean? {
        baseline = Float.NaN
        return if (pressed) {
            pressed = false
            false
        } else {
            null
        }
    }

    private fun validPressure(value: Float): Float? =
        value.takeIf { it.isFinite() && it > 0f }

    private companion object {
        const val MIN_ABSOLUTE_PRESSURE = 0.35f
        const val PRESS_DELTA = 0.15f
        const val RELEASE_DELTA = 0.07f
        const val HYSTERESIS = 0.08f
    }
}
