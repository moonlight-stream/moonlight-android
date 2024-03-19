package com.limelight.binding.input;

import com.limelight.GameMenu;

import java.util.List;

/**
 * Generic Input Device
 */
public interface GameInputDevice {

    /**
     * @return list of device specific game menu options, e.g. configure a controller's mouse mode
     */
    List<GameMenu.MenuOption> getGameMenuOptions();
}
