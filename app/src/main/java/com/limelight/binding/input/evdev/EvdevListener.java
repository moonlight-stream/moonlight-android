package com.limelight.binding.input.evdev;

public interface EvdevListener {
    int BUTTON_LEFT = 1;
    int BUTTON_MIDDLE = 2;
    int BUTTON_RIGHT = 3;
    int BUTTON_X1 = 4;
    int BUTTON_X2 = 5;

    void mouseMove(int deltaX, int deltaY);
    void mouseButtonEvent(int buttonId, boolean down);
    void mouseVScroll(byte amount);
    void mouseHScroll(byte amount);
    void keyboardEvent(boolean buttonDown, short keyCode);
}
