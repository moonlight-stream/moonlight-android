package com.limelight.ui

import android.view.KeyEvent

/** Shared key contract for focus-driven dialogs. */
internal object UiDialogKeyHandler {
    fun handle(
        action: Int,
        keyCode: Int,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        dismissOnBack: Boolean = true
    ): Boolean {
        if (UiDismissKeyHandler.handle(action, keyCode, onDismiss, dismissOnBack)) {
            return true
        }
        if (!isConfirmKey(keyCode)) return false

        return when (action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> {
                onConfirm()
                true
            }
            else -> false
        }
    }

    private fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_SPACE
    }
}
