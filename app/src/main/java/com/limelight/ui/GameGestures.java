package com.limelight.ui;

import com.limelight.binding.input.GameInputDevice;

public interface GameGestures {
    void toggleKeyboard();

    default void showGameMenu(GameInputDevice device){};
}
