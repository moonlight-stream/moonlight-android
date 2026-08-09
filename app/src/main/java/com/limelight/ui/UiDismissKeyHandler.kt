package com.limelight.ui

import android.view.KeyEvent

/**
 * Shared key contract for user-dismissible UI such as dialogs, menus and guides.
 *
 * The key-down half is consumed immediately so it cannot escape to the owning
 * Activity. The dismissal runs once, on key-up.
 */
internal object UiDismissKeyHandler {
    fun handle(action: Int, keyCode: Int, onDismiss: () -> Unit): Boolean {
        return handle(action, keyCode, onDismiss, dismissOnBack = true)
    }

    fun handle(
        action: Int,
        keyCode: Int,
        onDismiss: () -> Unit,
        dismissOnBack: Boolean
    ): Boolean {
        if (!isDismissKey(keyCode, dismissOnBack)) return false

        return when (action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> {
                onDismiss()
                true
            }
            else -> false
        }
    }

    private fun isDismissKey(keyCode: Int, dismissOnBack: Boolean): Boolean {
        return (dismissOnBack && keyCode == KeyEvent.KEYCODE_BACK) ||
            keyCode == KeyEvent.KEYCODE_ESCAPE ||
            keyCode == KeyEvent.KEYCODE_BUTTON_B
    }
}
