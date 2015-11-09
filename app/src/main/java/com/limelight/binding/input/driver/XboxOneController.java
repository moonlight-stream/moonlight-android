package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.limelight.LimeLog;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class XboxOneController {
    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final int deviceId;

    private Thread inputThread;
    private UsbDriverListener listener;
    private boolean stopped;

    private short buttonFlags;
    private float leftTrigger, rightTrigger;
    private float rightStickX, rightStickY;
    private float leftStickX, leftStickY;

    private static final int MICROSOFT_VID = 0x045e;
    private static final int XB1_IFACE_SUBCLASS = 71;
    private static final int XB1_IFACE_PROTOCOL = 208;

    // FIXME: odata_serial
    private static final byte[] XB1_INIT_DATA = {0x05, 0x20, 0x00, 0x01, 0x00};

    public XboxOneController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        this.device = device;
        this.connection = connection;
        this.deviceId = deviceId;
        this.listener = listener;
    }

    public int getControllerId() {
        return this.deviceId;
    }

    private void setButtonFlag(int buttonFlag, int data) {
        if (data != 0) {
            buttonFlags |= buttonFlag;
        }
        else {
            buttonFlags &= ~buttonFlag;
        }
    }

    private void reportInput() {
        listener.reportControllerState(deviceId, buttonFlags, leftStickX, leftStickY,
                rightStickX, rightStickY, leftTrigger, rightTrigger);
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

        reportInput();
    }

    private void processPacket(ByteBuffer buffer) {
        switch (buffer.get())
        {
            case 0x20:
                buffer.position(buffer.position()+3);
                processButtons(buffer);
                break;

            case 0x07:
                buffer.position(buffer.position() + 3);
                setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get() & 0x01);
                reportInput();
                break;
        }
    }

    private void startInputThread(final UsbEndpoint inEndpt) {
        inputThread = new Thread() {
            public void run() {
                while (!isInterrupted() && !stopped) {
                    byte[] buffer = new byte[64];

                    int res;

                    //
                    // There's no way that I can tell to determine if a device has failed
                    // or if the timeout has simply expired. We'll check how long the transfer
                    // took to fail and assume the device failed if it happened before the timeout
                    // expired.
                    //

                    do {
                        // Read the next input state packet
                        long lastMillis = MediaCodecHelper.getMonotonicMillis();
                        res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 3000);
                        if (res == -1 && MediaCodecHelper.getMonotonicMillis() - lastMillis < 1000) {
                            LimeLog.warning("Detected device I/O error");
                            XboxOneController.this.stop();
                            break;
                        }
                    } while (res == -1 && !isInterrupted() && !stopped);

                    if (res == -1 || stopped) {
                        break;
                    }

                    processPacket(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN));
                }
            }
        };
        inputThread.setName("Xbox One Controller - Input Thread");
        inputThread.start();
    }

    public boolean start() {
        // Force claim all interfaces
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);

            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim interfaces");
                return false;
            }
        }

        // Find the endpoints
        UsbEndpoint outEndpt = null;
        UsbEndpoint inEndpt = null;
        UsbInterface iface = device.getInterface(0);
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    LimeLog.warning("Found duplicate IN endpoint");
                    return false;
                }
                inEndpt = endpt;
            }
            else if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    LimeLog.warning("Found duplicate OUT endpoint");
                    return false;
                }
                outEndpt = endpt;
            }
        }

        // Make sure the required endpoints were present
        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint");
            return false;
        }

        // Send the initialization packet
        int res = connection.bulkTransfer(outEndpt, XB1_INIT_DATA, XB1_INIT_DATA.length, 3000);
        if (res != XB1_INIT_DATA.length) {
            LimeLog.warning("Initialization transfer failed: "+res);
            return false;
        }

        // Start listening for controller input
        startInputThread(inEndpt);

        // Report this device added via the listener
        listener.deviceAdded(deviceId);

        return true;
    }

    public void stop() {
        if (stopped) {
            return;
        }

        stopped = true;

        // Stop the input thread
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

        // Report the device removed
        listener.deviceRemoved(deviceId);

        // Close the USB connection
        connection.close();
    }

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == MICROSOFT_VID &&
                device.getInterfaceCount() >= 1 &&
                device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                device.getInterface(0).getInterfaceSubclass() == XB1_IFACE_SUBCLASS &&
                device.getInterface(0).getInterfaceProtocol() == XB1_IFACE_PROTOCOL;
    }
}
