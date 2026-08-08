package com.limelight.binding.input

/** Tracks a controller button chord and emits once until any required button is released. */
internal class ControllerButtonChordState(private val chordFlags: Int) {
    private var pressedChordFlags = 0
    private var latched = false

    fun updateSnapshot(buttonFlags: Int): Boolean {
        pressedChordFlags = buttonFlags and chordFlags
        val chordPressed = pressedChordFlags == chordFlags
        if (chordPressed && !latched) {
            latched = true
            return true
        }
        if (!chordPressed) {
            latched = false
        }
        return false
    }

    fun updateButton(buttonFlag: Int, pressed: Boolean): Boolean {
        val buttonFlags = if (pressed) {
            pressedChordFlags or buttonFlag
        } else {
            pressedChordFlags and buttonFlag.inv()
        }
        return updateSnapshot(buttonFlags)
    }
}
