package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;

public class Xbox360Controller extends AbstractXboxController {

    // This list is taken from the Xpad driver in the Linux kernel.
    // I've excluded the devices that aren't "controllers" in the traditional sense, but
    // if people really want to use their dancepads or fight sticks with Moonlight, I can
    // put them in.
    private static final DeviceIdTuple[] supportedDeviceTuples = {
            new DeviceIdTuple(0x045e, 0x028e, "Microsoft X-Box 360 pad"),
            new DeviceIdTuple(0x044f, 0xb326, "Thrustmaster Gamepad GP XID"),
            new DeviceIdTuple(0x046d, 0xc21d, "Logitech Gamepad F310"),
            new DeviceIdTuple(0x046d, 0xc21e, "Logitech Gamepad F510"),
            new DeviceIdTuple(0x046d, 0xc21f, "Logitech Gamepad F710"),
            new DeviceIdTuple(0x046d, 0xc242, "Logitech Chillstream Controller"),
            new DeviceIdTuple(0x0738, 0x4716, "Mad Catz Wired Xbox 360 Controller"),
            new DeviceIdTuple(0x0738, 0x4726, "Mad Catz Xbox 360 Controller"),
            new DeviceIdTuple(0x0738, 0xb726, "Mad Catz Xbox controller - MW2"),
            new DeviceIdTuple(0x0738, 0xbeef, "Mad Catz JOYTECH NEO SE Advanced GamePad"),
            new DeviceIdTuple(0x0738, 0xcb02, "Saitek Cyborg Rumble Pad - PC/Xbox 360"),
            new DeviceIdTuple(0x0738, 0xcb03, "Saitek P3200 Rumble Pad - PC/Xbox 360"),
            new DeviceIdTuple(0x0e6f, 0x0113, "Afterglow AX.1 Gamepad for Xbox 360"),
            new DeviceIdTuple(0x0e6f, 0x0201, "Pelican PL-3601 'TSZ' Wired Xbox 360 Controller"),
            new DeviceIdTuple(0x0e6f, 0x0213, "Afterglow Gamepad for Xbox 360"),
            new DeviceIdTuple(0x0e6f, 0x021f, "Rock Candy Gamepad for Xbox 360"),
            new DeviceIdTuple(0x0e6f, 0x0301, "Logic3 Controller"),
            new DeviceIdTuple(0x0e6f, 0x0401, "Logic3 Controller"),
            new DeviceIdTuple(0x12ab, 0x0301, "PDP AFTERGLOW AX.1"),
            new DeviceIdTuple(0x146b, 0x0601, "BigBen Interactive XBOX 360 Controller"),
            new DeviceIdTuple(0x1532, 0x0037, "Razer Sabertooth"),
            new DeviceIdTuple(0x15e4, 0x3f00, "Power A Mini Pro Elite"),
            new DeviceIdTuple(0x15e4, 0x3f0a, "Xbox Airflo wired controller"),
            new DeviceIdTuple(0x15e4, 0x3f10, "Batarang Xbox 360 controller"),
            new DeviceIdTuple(0x162e, 0xbeef, "Joytech Neo-Se Take2"),
            new DeviceIdTuple(0x1689, 0xfd00, "Razer Onza Tournament Edition"),
            new DeviceIdTuple(0x1689, 0xfd01, "Razer Onza Classic Edition"),
            new DeviceIdTuple(0x24c6, 0x5d04, "Razer Sabertooth"),
            new DeviceIdTuple(0x1bad, 0xf016, "Mad Catz Xbox 360 Controller"),
            new DeviceIdTuple(0x1bad, 0xf023, "MLG Pro Circuit Controller (Xbox)"),
            new DeviceIdTuple(0x1bad, 0xf900, "Harmonix Xbox 360 Controller"),
            new DeviceIdTuple(0x1bad, 0xf901, "Gamestop Xbox 360 Controller"),
            new DeviceIdTuple(0x1bad, 0xf903, "Tron Xbox 360 controller"),
            new DeviceIdTuple(0x24c6, 0x5300, "PowerA MINI PROEX Controller"),
            new DeviceIdTuple(0x24c6, 0x5303, "Xbox Airflo wired controller"),
    };

    public static boolean canClaimDevice(UsbDevice device) {
        for (DeviceIdTuple tuple : supportedDeviceTuples) {
            if (device.getVendorId() == tuple.vid && device.getProductId() == tuple.pid) {
                LimeLog.info("XB360 can claim device: " + tuple.name);
                return true;
            }
        }
        return false;
    }

    public Xbox360Controller(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(device, connection, deviceId, listener);
    }

    @Override
    protected boolean handleRead(ByteBuffer buffer) {
        // Skip first byte
        buffer.position(buffer.position()+1);

        // DPAD
        byte b = buffer.get();
        setButtonFlag(ControllerPacket.LEFT_FLAG, b & 0x04);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, b & 0x08);
        setButtonFlag(ControllerPacket.UP_FLAG, b & 0x01);
        setButtonFlag(ControllerPacket.DOWN_FLAG, b & 0x02);

        // Start/Select
        setButtonFlag(ControllerPacket.PLAY_FLAG, b & 0x10);
        setButtonFlag(ControllerPacket.BACK_FLAG, b & 0x20);

        // LS/RS
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b & 0x40);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b & 0x80);

        // ABXY buttons
        b = buffer.get();
        setButtonFlag(ControllerPacket.A_FLAG, b & 0x10);
        setButtonFlag(ControllerPacket.B_FLAG, b & 0x20);
        setButtonFlag(ControllerPacket.X_FLAG, b & 0x40);
        setButtonFlag(ControllerPacket.Y_FLAG, b & 0x80);

        // LB/RB
        setButtonFlag(ControllerPacket.LB_FLAG, b & 0x01);
        setButtonFlag(ControllerPacket.RB_FLAG, b & 0x02);

        // Xbox button
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, b & 0x04);

        // Triggers
        leftTrigger = buffer.get() / 255.0f;
        rightTrigger = buffer.get() / 255.0f;

        // Left stick
        leftStickX = buffer.getShort() / 32767.0f;
        leftStickY = ~buffer.getShort() / 32767.0f;

        // Right stick
        rightStickX = buffer.getShort() / 32767.0f;
        rightStickY = ~buffer.getShort() / 32767.0f;

        // Return true to send input
        return true;
    }

    @Override
    protected boolean doInit() {
        // Xbox 360 wired controller requires no initialization
        return true;
    }

    private static class DeviceIdTuple {
        public final int vid;
        public final int pid;
        public final String name;

        public DeviceIdTuple(int vid, int pid, String name) {
            this.vid = vid;
            this.pid = pid;
            this.name = name;
        }
    }
}
