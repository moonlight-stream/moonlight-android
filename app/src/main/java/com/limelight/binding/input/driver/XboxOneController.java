package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;

public class XboxOneController extends AbstractXboxController {

    private static final int XB1_IFACE_SUBCLASS = 71;
    private static final int XB1_IFACE_PROTOCOL = 208;

    private static final int[] SUPPORTED_VENDORS = {
            0x045e, // Microsoft
            0x0738, // Mad Catz
            0x0e6f, // Unknown
            0x0f0d, // Hori
            0x1532, // Razer Wildcat
            0x24c6, // PowerA
    };

    // FIXME: odata_serial
    private static final byte[] XB1_INIT_DATA = {0x05, 0x20, 0x00, 0x01, 0x00};

    public XboxOneController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(device, connection, deviceId, listener);
    }

    private void processButtons(ByteBuffer buffer) {
        byte b = buffer.get();

        setButtonFlag(ControllerPacket.PLAY_FLAG, b & 0x04);
        setButtonFlag(ControllerPacket.BACK_FLAG, b & 0x08);

        setButtonFlag(ControllerPacket.A_FLAG, b & 0x10);
        setButtonFlag(ControllerPacket.B_FLAG, b & 0x20);
        setButtonFlag(ControllerPacket.X_FLAG, b & 0x40);
        setButtonFlag(ControllerPacket.Y_FLAG, b & 0x80);

        b = buffer.get();
        setButtonFlag(ControllerPacket.LEFT_FLAG, b & 0x04);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, b & 0x08);
        setButtonFlag(ControllerPacket.UP_FLAG, b & 0x01);
        setButtonFlag(ControllerPacket.DOWN_FLAG, b & 0x02);

        setButtonFlag(ControllerPacket.LB_FLAG, b & 0x10);
        setButtonFlag(ControllerPacket.RB_FLAG, b & 0x20);

        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b & 0x40);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b & 0x80);

        leftTrigger = buffer.getShort() / 1023.0f;
        rightTrigger = buffer.getShort() / 1023.0f;

        leftStickX = buffer.getShort() / 32767.0f;
        leftStickY = ~buffer.getShort() / 32767.0f;

        rightStickX = buffer.getShort() / 32767.0f;
        rightStickY = ~buffer.getShort() / 32767.0f;
    }

    private void ackModeReport(byte seqNum) {
        byte[] payload = {0x01, 0x20, seqNum, 0x09, 0x00, 0x07, 0x20, 0x02,
                0x00, 0x00, 0x00, 0x00, 0x00};
        connection.bulkTransfer(outEndpt, payload, payload.length, 3000);
    }

    @Override
    protected boolean handleRead(ByteBuffer buffer) {
        switch (buffer.get())
        {
            case 0x20:
                buffer.position(buffer.position()+3);
                processButtons(buffer);
                return true;

            case 0x07:
                // The Xbox One S controller needs acks for mode reports otherwise
                // it retransmits them forever.
                if (buffer.get() == 0x30) {
                    ackModeReport(buffer.get());
                    buffer.position(buffer.position() + 1);
                }
                else {
                    buffer.position(buffer.position() + 2);
                }
                setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get() & 0x01);
                return true;
        }

        return false;
    }

    public static boolean canClaimDevice(UsbDevice device) {
        for (int supportedVid : SUPPORTED_VENDORS) {
            if (device.getVendorId() == supportedVid &&
                    device.getInterfaceCount() >= 1 &&
                    device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    device.getInterface(0).getInterfaceSubclass() == XB1_IFACE_SUBCLASS &&
                    device.getInterface(0).getInterfaceProtocol() == XB1_IFACE_PROTOCOL) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean doInit() {
        // Send the initialization packet
        int res = connection.bulkTransfer(outEndpt, XB1_INIT_DATA, XB1_INIT_DATA.length, 3000);
        if (res != XB1_INIT_DATA.length) {
            LimeLog.warning("Initialization transfer failed: "+res);
            return false;
        }

        return true;
    }
}
