package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.util.ArrayList;

/**
 * USB driver for the 2026 Steam Controller (Valve codename "Triton"), connected either
 * directly over USB-C or through the wireless puck.
 *
 * Out of the box the controller behaves like a keyboard and mouse ("lizard mode"), so Android
 * never exposes it as a gamepad. This driver claims the controller's HID interface, tells it
 * to leave lizard mode with a feature report, and parses its vendor-specific input reports.
 *
 * Bluetooth LE is not handled here: the controller uses a custom GATT service over BLE rather
 * than standard HID, so it is not visible to the USB host stack.
 */
public class SteamTritonController extends AbstractController {
    private static final int VALVE_VENDOR_ID = 0x28de;
    private static final int PRODUCT_ID_WIRED = 0x1302;
    private static final int PRODUCT_ID_PUCK = 0x1304;

    // The puck exposes one HID interface per paired controller on interfaces 2 through 5.
    private static final int PUCK_FIRST_INTERFACE = 2;
    private static final int PUCK_LAST_INTERFACE = 5;

    // USB HID class requests
    private static final int HID_REQ_TYPE_SET = 0x21; // host-to-device, class, interface
    private static final int HID_REQ_SET_REPORT = 0x09;
    private static final int HID_REPORT_TYPE_OUTPUT = 2;
    private static final int HID_REPORT_TYPE_FEATURE = 3;
    private static final int HID_SUBCLASS_BOOT = 1; // Boot keyboard/mouse interfaces

    // Feature report protocol
    private static final int FEATURE_REPORT_ID = 1;
    private static final int FEATURE_REPORT_SIZE = 64;
    private static final int ID_SET_SETTINGS_VALUES = 0x87;
    private static final int SETTING_LIZARD_MODE = 9;
    private static final int LIZARD_MODE_OFF = 0;

    // Input reports
    private static final int REPORT_ID_STATE = 0x42;
    private static final int REPORT_ID_STATE_BLE = 0x45;
    private static final int STATE_REPORT_MIN_SIZE = 18; // Through the right stick Y axis

    // Output reports
    private static final int REPORT_ID_RUMBLE = 0x80;
    private static final int RUMBLE_REPORT_SIZE = 10;

    // Button bits in the 32-bit button field
    private static final int BTN_A = 0x00000001;
    private static final int BTN_B = 0x00000002;
    private static final int BTN_X = 0x00000004;
    private static final int BTN_Y = 0x00000008;
    private static final int BTN_QAM = 0x00000010;
    private static final int BTN_R3 = 0x00000020;
    private static final int BTN_VIEW = 0x00000040;
    private static final int BTN_R4 = 0x00000080;
    private static final int BTN_R5 = 0x00000100;
    private static final int BTN_R = 0x00000200;
    private static final int BTN_DPAD_DOWN = 0x00000400;
    private static final int BTN_DPAD_RIGHT = 0x00000800;
    private static final int BTN_DPAD_LEFT = 0x00001000;
    private static final int BTN_DPAD_UP = 0x00002000;
    private static final int BTN_MENU = 0x00004000;
    private static final int BTN_L3 = 0x00008000;
    private static final int BTN_STEAM = 0x00010000;
    private static final int BTN_L4 = 0x00020000;
    private static final int BTN_L5 = 0x00040000;
    private static final int BTN_L = 0x00080000;
    private static final int BTN_RIGHT_PAD_CLICK = 0x00400000;
    private static final int BTN_LEFT_PAD_CLICK = 0x04000000;

    // The controller reverts to lizard mode on its own after a while, so we refresh the setting.
    private static final long LIZARD_REFRESH_INTERVAL_MS = 3000;

    // The controller stops rumbling after ~50 ms without a new command, so we resend while active.
    private static final long RUMBLE_REFRESH_INTERVAL_MS = 40;

    private static class HidStream {
        final UsbInterface iface;
        final UsbEndpoint inEndpoint;
        final UsbEndpoint outEndpoint;

        HidStream(UsbInterface iface, UsbEndpoint inEndpoint, UsbEndpoint outEndpoint) {
            this.iface = iface;
            this.inEndpoint = inEndpoint;
            this.outEndpoint = outEndpoint;
        }
    }

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final ArrayList<HidStream> streams = new ArrayList<>();
    private final ArrayList<Thread> threads = new ArrayList<>();
    private final Object stateLock = new Object();
    private final Object rumbleLock = new Object();

    private volatile boolean stopped;
    private volatile boolean added;

    private int rumbleLow, rumbleHigh;

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == VALVE_VENDOR_ID &&
                (device.getProductId() == PRODUCT_ID_WIRED || device.getProductId() == PRODUCT_ID_PUCK);
    }

    public SteamTritonController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_STEAM;
        this.capabilities = MoonBridge.LI_CCAP_ANALOG_TRIGGERS | MoonBridge.LI_CCAP_RUMBLE;
        this.supportedButtonFlags =
                ControllerPacket.A_FLAG | ControllerPacket.B_FLAG | ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG |
                        ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG |
                        ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG |
                        ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG |
                        ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.SPECIAL_BUTTON_FLAG |
                        ControllerPacket.PADDLE1_FLAG | ControllerPacket.PADDLE2_FLAG |
                        ControllerPacket.PADDLE3_FLAG | ControllerPacket.PADDLE4_FLAG |
                        ControllerPacket.TOUCHPAD_FLAG | ControllerPacket.MISC_FLAG;
    }

    private boolean isGamepadInterface(UsbInterface iface) {
        if (device.getProductId() == PRODUCT_ID_PUCK) {
            return iface.getId() >= PUCK_FIRST_INTERFACE && iface.getId() <= PUCK_LAST_INTERFACE;
        }

        // On the wired controller, skip the boot keyboard and mouse interfaces used by lizard mode
        // and take the remaining HID interface(s), which carry the vendor-specific gamepad reports.
        return (iface.getInterfaceClass() == UsbConstants.USB_CLASS_HID ||
                iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) &&
                iface.getInterfaceSubclass() != HID_SUBCLASS_BOOT;
    }

    @Override
    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (!isGamepadInterface(iface)) {
                continue;
            }

            UsbEndpoint in = null, out = null;
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = iface.getEndpoint(j);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
                    continue;
                }

                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN && in == null) {
                    in = endpoint;
                }
                else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT && out == null) {
                    out = endpoint;
                }
            }

            if (in == null) {
                continue;
            }

            // Force the claim to detach the kernel's keyboard/mouse emulation driver
            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim Steam Controller interface " + iface.getId());
                continue;
            }

            streams.add(new HidStream(iface, in, out));
        }

        if (streams.isEmpty()) {
            LimeLog.warning("No usable Steam Controller interfaces found");
            return false;
        }

        // Take the controller out of lizard mode before we start listening
        boolean lizardDisabled = false;
        for (HidStream stream : streams) {
            lizardDisabled |= disableLizardMode(stream);
        }
        if (!lizardDisabled) {
            LimeLog.warning("Unable to disable Steam Controller lizard mode");
            return false;
        }

        for (HidStream stream : streams) {
            Thread t = createInputThread(stream);
            threads.add(t);
            t.start();
        }

        Thread maintenance = createMaintenanceThread();
        threads.add(maintenance);
        maintenance.start();

        return true;
    }

    private Thread createInputThread(final HidStream stream) {
        return new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[Math.max(64, stream.inEndpoint.getMaxPacketSize())];

                while (!isInterrupted() && !stopped) {
                    int res;

                    // There is no way to tell a failed device from an expired timeout, so we
                    // assume the device failed if the transfer fails before the timeout elapses.
                    do {
                        long lastMillis = SystemClock.uptimeMillis();
                        res = connection.bulkTransfer(stream.inEndpoint, buffer, buffer.length, 3000);

                        if (res == 0) {
                            res = -1;
                        }

                        if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                            LimeLog.warning("Detected device I/O error");
                            SteamTritonController.this.stop();
                            break;
                        }
                    } while (res == -1 && !isInterrupted() && !stopped);

                    if (res == -1 || stopped) {
                        break;
                    }

                    synchronized (stateLock) {
                        if (handleRead(buffer, res) && added && !stopped) {
                            reportInput();
                        }
                    }
                }
            }
        };
    }

    private Thread createMaintenanceThread() {
        return new Thread() {
            @Override
            public void run() {
                try {
                    // Give the old InputDevice time to go away (if any) before we claim its
                    // controller number, as the Xbox drivers do.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }

                // Report that we're added _before_ reporting input
                notifyDeviceAdded();
                added = true;

                long lastLizardRefresh = SystemClock.uptimeMillis();
                while (!isInterrupted() && !stopped) {
                    try {
                        Thread.sleep(RUMBLE_REFRESH_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }

                    long now = SystemClock.uptimeMillis();
                    if (now - lastLizardRefresh >= LIZARD_REFRESH_INTERVAL_MS) {
                        lastLizardRefresh = now;
                        for (HidStream stream : streams) {
                            disableLizardMode(stream);
                        }
                    }

                    int low, high;
                    synchronized (rumbleLock) {
                        low = rumbleLow;
                        high = rumbleHigh;
                    }
                    if (low != 0 || high != 0) {
                        sendRumble(low, high);
                    }
                }
            }
        };
    }

    private boolean disableLizardMode(HidStream stream) {
        byte[] report = new byte[FEATURE_REPORT_SIZE];
        report[0] = FEATURE_REPORT_ID;
        report[1] = (byte) ID_SET_SETTINGS_VALUES; // Message type
        report[2] = 3;                              // Payload length (one 3-byte setting)
        report[3] = (byte) SETTING_LIZARD_MODE;     // Setting number
        report[4] = (byte) LIZARD_MODE_OFF;         // Setting value (little endian)
        report[5] = 0;

        int res = connection.controlTransfer(HID_REQ_TYPE_SET, HID_REQ_SET_REPORT,
                (HID_REPORT_TYPE_FEATURE << 8) | FEATURE_REPORT_ID,
                stream.iface.getId(), report, report.length, 1000);
        if (res != report.length) {
            LimeLog.warning("Lizard mode feature report failed: " + res);
            return false;
        }

        return true;
    }

    private static int unsignShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int signShort(byte[] data, int offset) {
        return (short) unsignShort(data, offset);
    }

    private static float normalizeAxis(int value) {
        return Math.max(-1.0f, Math.min(1.0f, value / 32767.0f));
    }

    private boolean handleRead(byte[] data, int length) {
        if (length < STATE_REPORT_MIN_SIZE) {
            // Not a state report (lizard mode reports and the like are much smaller)
            return false;
        }

        int reportId = data[0] & 0xFF;
        if (reportId != REPORT_ID_STATE && reportId != REPORT_ID_STATE_BLE) {
            // Battery, wireless status and other reports
            return false;
        }

        // Layout: ID, sequence number, 32-bit buttons, then 16-bit little endian axes
        int buttons = unsignShort(data, 2) | (unsignShort(data, 4) << 16);

        setButtonFlag(ControllerPacket.A_FLAG, buttons & BTN_A);
        setButtonFlag(ControllerPacket.B_FLAG, buttons & BTN_B);
        setButtonFlag(ControllerPacket.X_FLAG, buttons & BTN_X);
        setButtonFlag(ControllerPacket.Y_FLAG, buttons & BTN_Y);

        setButtonFlag(ControllerPacket.LB_FLAG, buttons & BTN_L);
        setButtonFlag(ControllerPacket.RB_FLAG, buttons & BTN_R);
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, buttons & BTN_L3);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, buttons & BTN_R3);

        // The View button (left of the Steam button) is Start and Menu (right) is Back,
        // matching how SDL maps this controller.
        setButtonFlag(ControllerPacket.PLAY_FLAG, buttons & BTN_VIEW);
        setButtonFlag(ControllerPacket.BACK_FLAG, buttons & BTN_MENU);
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buttons & BTN_STEAM);
        setButtonFlag(ControllerPacket.MISC_FLAG, buttons & BTN_QAM);

        setButtonFlag(ControllerPacket.UP_FLAG, buttons & BTN_DPAD_UP);
        setButtonFlag(ControllerPacket.DOWN_FLAG, buttons & BTN_DPAD_DOWN);
        setButtonFlag(ControllerPacket.LEFT_FLAG, buttons & BTN_DPAD_LEFT);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, buttons & BTN_DPAD_RIGHT);

        setButtonFlag(ControllerPacket.PADDLE1_FLAG, buttons & BTN_R4);
        setButtonFlag(ControllerPacket.PADDLE2_FLAG, buttons & BTN_L4);
        setButtonFlag(ControllerPacket.PADDLE3_FLAG, buttons & BTN_R5);
        setButtonFlag(ControllerPacket.PADDLE4_FLAG, buttons & BTN_L5);

        setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, buttons & (BTN_LEFT_PAD_CLICK | BTN_RIGHT_PAD_CLICK));

        // Triggers are non-negative 15-bit values
        leftTrigger = normalizeAxis(signShort(data, 6));
        rightTrigger = normalizeAxis(signShort(data, 8));
        leftTrigger = Math.max(0.0f, leftTrigger);
        rightTrigger = Math.max(0.0f, rightTrigger);

        // The controller reports up as positive on the Y axes. The listener expects the same
        // convention as the Xbox drivers, which means inverting the raw value.
        leftStickX = normalizeAxis(signShort(data, 10));
        leftStickY = normalizeAxis(-signShort(data, 12));
        rightStickX = normalizeAxis(signShort(data, 14));
        rightStickY = normalizeAxis(-signShort(data, 16));

        return true;
    }

    private void sendRumble(int low, int high) {
        byte[] report = new byte[RUMBLE_REPORT_SIZE];
        report[0] = (byte) REPORT_ID_RUMBLE;
        report[1] = 0; // Type
        report[2] = 0; // Intensity (little endian)
        report[3] = 0;
        report[4] = (byte) (low & 0xFF); // Left speed
        report[5] = (byte) ((low >> 8) & 0xFF);
        report[6] = 0; // Left gain
        report[7] = (byte) (high & 0xFF); // Right speed
        report[8] = (byte) ((high >> 8) & 0xFF);
        report[9] = 0; // Right gain

        HidStream stream = streams.isEmpty() ? null : streams.get(0);
        if (stream == null || stopped) {
            return;
        }

        int res;
        if (stream.outEndpoint != null) {
            res = connection.bulkTransfer(stream.outEndpoint, report, report.length, 100);
        }
        else {
            res = connection.controlTransfer(HID_REQ_TYPE_SET, HID_REQ_SET_REPORT,
                    (HID_REPORT_TYPE_OUTPUT << 8) | REPORT_ID_RUMBLE,
                    stream.iface.getId(), report, report.length, 100);
        }

        if (res != report.length) {
            LimeLog.warning("Rumble transfer failed: " + res);
        }
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        int low = lowFreqMotor & 0xFFFF;
        int high = highFreqMotor & 0xFFFF;

        synchronized (rumbleLock) {
            rumbleLow = low;
            rumbleHigh = high;
        }

        // Send immediately; the maintenance thread keeps it alive while non-zero
        sendRumble(low, high);
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // Trigger motors are not supported
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }

        // Cancel any rumble effects while the connection is still open
        synchronized (rumbleLock) {
            rumbleLow = 0;
            rumbleHigh = 0;
        }
        sendRumble(0, 0);

        stopped = true;

        for (Thread t : threads) {
            t.interrupt();
        }
        threads.clear();

        connection.close();

        notifyDeviceRemoved();
    }
}
