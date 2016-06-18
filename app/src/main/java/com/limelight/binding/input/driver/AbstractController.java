package com.limelight.binding.input.driver;

public abstract class AbstractController {

    private final int deviceId;

    private UsbDriverListener listener;

    protected short buttonFlags;
    protected float leftTrigger, rightTrigger;
    protected float rightStickX, rightStickY;
    protected float leftStickX, leftStickY;

    public int getControllerId() {
        return deviceId;
    }

    protected void setButtonFlag(int buttonFlag, int data) {
        if (data != 0) {
            buttonFlags |= buttonFlag;
        }
        else {
            buttonFlags &= ~buttonFlag;
        }
    }

    protected void reportInput() {
        listener.reportControllerState(deviceId, buttonFlags, leftStickX, leftStickY,
                rightStickX, rightStickY, leftTrigger, rightTrigger);
    }

    public abstract boolean start();
    public abstract void stop();

    public AbstractController(int deviceId, UsbDriverListener listener) {
        this.deviceId = deviceId;
        this.listener = listener;
    }

    protected void notifyDeviceRemoved() {
        listener.deviceRemoved(deviceId);
    }

    protected void notifyDeviceAdded() {
        listener.deviceAdded(deviceId);
    }
}
