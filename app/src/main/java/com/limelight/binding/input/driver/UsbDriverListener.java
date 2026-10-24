package com.limelight.binding.input.driver;

public interface UsbDriverListener {
    void reportControllerState(int controllerId, int buttonFlags,
                               float leftStickX, float leftStickY,
                               float rightStickX, float rightStickY,
                               float leftTrigger, float rightTrigger);

    // Relative mouse movement and mouse buttons, for controllers with trackpads
    void reportMouseMove(short deltaX, short deltaY);
    void reportMouseButton(byte mouseButton, boolean pressed);
    // High resolution scroll amount, where 120 is one wheel notch and positive scrolls up
    void reportMouseScroll(short scrollAmount);

    void deviceRemoved(AbstractController controller);
    void deviceAdded(AbstractController controller);
}
