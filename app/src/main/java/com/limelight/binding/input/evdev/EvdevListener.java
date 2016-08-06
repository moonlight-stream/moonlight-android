package com.limelight.binding.input.evdev;

public interface EvdevListener {
    public static final int BUTTON_LEFT = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int BUTTON_RIGHT = 3;

    public void mouseMove(int deltaX, int deltaY);
    public void mouseButtonEvent(int buttonId, boolean down);
    public void mouseScroll(byte amount);
    public void keyboardEvent(boolean buttonDown, short keyCode);
}
