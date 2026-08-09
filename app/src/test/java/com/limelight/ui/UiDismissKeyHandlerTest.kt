package com.limelight.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDismissKeyHandlerTest {
    @Test
    fun remoteBackIsConsumedFromDownAndDismissesOnceOnUp() {
        assertDismissKeyPress(KeyEvent.KEYCODE_BACK)
    }

    @Test
    fun keyboardEscapeIsConsumedFromDownAndDismissesOnceOnUp() {
        assertDismissKeyPress(KeyEvent.KEYCODE_ESCAPE)
    }

    @Test
    fun gamepadButtonBIsConsumedFromDownAndDismissesOnceOnUp() {
        assertDismissKeyPress(KeyEvent.KEYCODE_BUTTON_B)
    }

    @Test
    fun unrelatedAndUnsupportedEventsAreNotConsumed() {
        var dismissCount = 0
        val dismiss = { dismissCount += 1 }

        assertFalse(UiDismissKeyHandler.handle(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, dismiss))
        assertFalse(UiDismissKeyHandler.handle(UNSUPPORTED_ACTION, KeyEvent.KEYCODE_BACK, dismiss))
        assertEquals(0, dismissCount)
    }

    @Test
    fun backCanBeLeftToNativeDialogHandling() {
        var dismissCount = 0

        assertFalse(
            UiDismissKeyHandler.handle(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BACK,
                { dismissCount += 1 },
                dismissOnBack = false
            )
        )
        assertEquals(0, dismissCount)
    }

    private fun assertDismissKeyPress(keyCode: Int) {
        var dismissCount = 0
        val dismiss = { dismissCount += 1 }

        assertTrue(UiDismissKeyHandler.handle(KeyEvent.ACTION_DOWN, keyCode, dismiss))
        assertEquals(0, dismissCount)
        assertTrue(UiDismissKeyHandler.handle(KeyEvent.ACTION_UP, keyCode, dismiss))
        assertEquals(1, dismissCount)
    }

    private companion object {
        const val UNSUPPORTED_ACTION = -1
    }
}
