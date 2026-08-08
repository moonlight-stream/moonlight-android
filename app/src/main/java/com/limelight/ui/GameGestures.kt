package com.limelight.ui

import android.view.KeyEvent
import com.limelight.binding.input.GameInputDevice

interface GameGestures {
    fun toggleKeyboard()
    fun showGameMenu(device: GameInputDevice?)
    fun showGameMenuFromUsb(device: GameInputDevice): Boolean
    fun dispatchUsbControllerMenuKey(event: KeyEvent): Boolean
    fun showUsbControllerShortcutHint()
    fun hideUsbControllerShortcutHint()
}
