package com.limelight.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDialogKeyHandlerTest {
    @Test
    fun allSupportedConfirmKeysClickOnceOnKeyUp() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_SPACE
        ).forEach { keyCode ->
            var confirmCount = 0

            assertTrue(handle(KeyEvent.ACTION_DOWN, keyCode, onConfirm = { confirmCount += 1 }))
            assertEquals(0, confirmCount)
            assertTrue(handle(KeyEvent.ACTION_UP, keyCode, onConfirm = { confirmCount += 1 }))
            assertEquals(1, confirmCount)
        }
    }

    @Test
    fun dismissKeysRetainSharedDismissBehavior() {
        listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B
        ).forEach { keyCode ->
            var dismissCount = 0

            assertTrue(handle(KeyEvent.ACTION_DOWN, keyCode, onDismiss = { dismissCount += 1 }))
            assertEquals(0, dismissCount)
            assertTrue(handle(KeyEvent.ACTION_UP, keyCode, onDismiss = { dismissCount += 1 }))
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun directionalKeysRemainAvailableForFocusNavigation() {
        assertFalse(handle(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(handle(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    private fun handle(
        action: Int,
        keyCode: Int,
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit = {}
    ): Boolean {
        return UiDialogKeyHandler.handle(action, keyCode, onDismiss, onConfirm)
    }
}
