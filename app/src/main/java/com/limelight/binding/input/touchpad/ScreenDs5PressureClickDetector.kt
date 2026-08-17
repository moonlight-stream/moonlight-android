package com.limelight.binding.input.touchpad

import kotlin.math.max

/**
 * Converts a firm screen press into a clickpad button state.
 *
 * Hardware pressure is preferred. Touchscreens that expose a fixed pressure value can still use
 * normalized contact size, which grows as a fingertip flattens under a firmer press. Both signals
 * are calibrated for every gesture and use hysteresis to prevent noisy toggles.
 */
internal class ScreenDs5PressureClickDetector {
    private enum class Source { PRESSURE, CONTACT_SIZE, DEEP_PRESS }

    private var pressureBaseline = Float.NaN
    private var contactSizeBaseline = Float.NaN
    private var pressed = false
    private var pressedSource: Source? = null

    fun begin(pressure: Float, contactSize: Float, deepPress: Boolean = false): Boolean? {
        pressureBaseline = validSignal(pressure) ?: Float.NaN
        contactSizeBaseline = validSignal(contactSize) ?: Float.NaN
        pressed = false
        pressedSource = null
        return update(pressure, contactSize, deepPress)
    }

    /** Returns the new button state only when it changes. */
    fun update(pressure: Float, contactSize: Float, deepPress: Boolean = false): Boolean? {
        val pressureSample = validSignal(pressure)
        val contactSizeSample = validSignal(contactSize)
        if (deepPress && !pressed) {
            pressed = true
            pressedSource = Source.DEEP_PRESS
            return true
        }

        if (!pressed) {
            // Let lighter early samples refine the per-gesture resting values.
            if (pressureSample != null && (pressureBaseline.isNaN() || pressureSample < pressureBaseline)) {
                pressureBaseline = pressureSample
            }
            if (contactSizeSample != null &&
                (contactSizeBaseline.isNaN() || contactSizeSample < contactSizeBaseline)
            ) {
                contactSizeBaseline = contactSizeSample
            }

            if (pressureSample != null && !pressureBaseline.isNaN() &&
                pressureSample >= max(MIN_ABSOLUTE_PRESSURE, pressureBaseline + PRESS_DELTA)
            ) {
                pressed = true
                pressedSource = Source.PRESSURE
                return true
            }
            if (contactSizeSample != null && !contactSizeBaseline.isNaN() &&
                contactSizeSample >= max(
                    contactSizeBaseline + SIZE_DELTA,
                    contactSizeBaseline * SIZE_PRESS_RATIO,
                )
            ) {
                pressed = true
                pressedSource = Source.CONTACT_SIZE
                return true
            }
            return null
        }

        val shouldRelease = when (pressedSource) {
            Source.PRESSURE -> pressureSample != null &&
                pressureSample <= max(
                    MIN_ABSOLUTE_PRESSURE - PRESS_HYSTERESIS,
                    pressureBaseline + PRESS_RELEASE_DELTA,
                )
            Source.CONTACT_SIZE -> contactSizeSample != null &&
                contactSizeSample <= max(
                    contactSizeBaseline + SIZE_RELEASE_DELTA,
                    contactSizeBaseline * SIZE_RELEASE_RATIO,
                )
            Source.DEEP_PRESS -> !deepPress && pressureSample == null && contactSizeSample == null
            null -> false
        }
        return if (shouldRelease) {
            pressed = false
            pressedSource = null
            false
        } else {
            null
        }
    }

    fun end(): Boolean? {
        pressureBaseline = Float.NaN
        contactSizeBaseline = Float.NaN
        return if (pressed) {
            pressed = false
            pressedSource = null
            false
        } else {
            null
        }
    }

    private fun validSignal(value: Float): Float? =
        value.takeIf { it.isFinite() && it > 0f }

    private companion object {
        const val MIN_ABSOLUTE_PRESSURE = 0.35f
        const val PRESS_DELTA = 0.15f
        const val PRESS_RELEASE_DELTA = 0.07f
        const val PRESS_HYSTERESIS = 0.08f
        // PKJ110 reports roughly 13 -> 19 for a deliberate firm press. Android normalizes this
        // device despite its kernel declaring a zero max, so ratios are more stable than relying
        // on an advertised range.
        const val SIZE_DELTA = 0.015f
        const val SIZE_RELEASE_DELTA = 0.008f
        const val SIZE_PRESS_RATIO = 1.30f
        const val SIZE_RELEASE_RATIO = 1.15f
    }
}
