package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;

public class Xbox360WirelessController extends AbstractXboxController {
    private static final int XB360W_IFACE_SUBCLASS = 93;
    private static final int XB360W_IFACE_PROTOCOL = 129; // Wireless only

    private static final int[] SUPPORTED_VENDORS = {
            0x045e, // Microsoft
    };

    public static boolean canClaimDevice(UsbDevice device) {
        for (int supportedVid : SUPPORTED_VENDORS) {
            if (device.getVendorId() == supportedVid &&
                    device.getInterfaceCount() >= 1 &&
                    device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    device.getInterface(0).getInterfaceSubclass() == XB360W_IFACE_SUBCLASS &&
                    device.getInterface(0).getInterfaceProtocol() == XB360W_IFACE_PROTOCOL) {
                return true;
            }
        }

        return false;
    }

    public Xbox360WirelessController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(device, connection, deviceId, listener);
    }

    @Override
    protected boolean handleRead(ByteBuffer buffer) {
        // Unreachable
        return true;
    }

    private boolean sendLedCommand(byte command) {
        byte[] commandBuffer = {
                0x00,
                0x00,
                0x08,
                (byte) (0x40 + command),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00};

        int res = connection.bulkTransfer(outEndpt, commandBuffer, commandBuffer.length, 3000);
        if (res != commandBuffer.length) {
            LimeLog.warning("LED set transfer failed: "+res);
            return false;
        }

        return true;
    }

    @Override
    protected boolean doInit() {
        // Turn the LED on corresponding to our device ID
        sendLedCommand((byte)(2 + (getControllerId() % 4)));

        // Close the interface and return false to give control back to the kernel.
        connection.releaseInterface(device.getInterface(0));
        return false;
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        // Unreachable.
    }
}
