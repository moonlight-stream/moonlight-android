package com.limelight.gamemenu

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMenuKeyEventTest {
    @Test
    fun standardGamepadAIsMappedToFocusedUiConfirmation() {
        assertEquals(
            KeyEvent.KEYCODE_DPAD_CENTER,
            mapGameMenuConfirmKeyCode(KeyEvent.KEYCODE_BUTTON_A)
        )
    }

    @Test
    fun unrelatedKeysAreNotChanged() {
        assertEquals(
            KeyEvent.KEYCODE_DPAD_LEFT,
            mapGameMenuConfirmKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
        )
    }
}
