package com.limelight.binding.input

import com.limelight.GameMenu

/**
 * Generic Input Device
 */
interface GameInputDevice {
    /**
     * @return device-specific actions that should remain immediately reachable in the game menu
     */
    fun getGameMenuQuickOptions(): List<GameMenu.MenuOption>
}
