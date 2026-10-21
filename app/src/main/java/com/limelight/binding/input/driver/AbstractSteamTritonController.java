package com.limelight.binding.input.driver;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

/**
 * Shared protocol handling for the 2026 Steam Controller (Valve codename "Triton"), regardless of
 * whether it is connected over USB or Bluetooth LE.
 *
 * Reports are passed around in the USB layout: the first byte is the report ID, followed by the
 * payload. Bluetooth notifications carry only the payload, so that driver prepends the ID.
 */
public abstract class AbstractSteamTritonController extends AbstractController {
    protected static final int VALVE_VENDOR_ID = 0x28de;

    // Input reports
    protected static final int REPORT_ID_STATE = 0x42;
    protected static final int REPORT_ID_STATE_BLE = 0x45;
    private static final int STATE_REPORT_MIN_SIZE = 18; // Through the right stick Y axis

    // Output reports
    protected static final int REPORT_ID_RUMBLE = 0x80;
    protected static final int RUMBLE_REPORT_SIZE = 10;

    // Feature report used to take the controller out of "lizard mode", where it emulates a
    // keyboard and mouse. It reverts on its own after a while, so it has to be resent.
    protected static final int FEATURE_REPORT_ID = 1;
    protected static final int FEATURE_REPORT_SIZE = 64;
    private static final int ID_SET_SETTINGS_VALUES = 0x87;
    private static final int SETTING_LIZARD_MODE = 9;
    private static final int LIZARD_MODE_OFF = 0;
    protected static final long LIZARD_REFRESH_INTERVAL_MS = 3000;

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

    protected final Object stateLock = new Object();

    // Set once the host has been told about this controller
    protected volatile boolean added;
    protected volatile boolean stopped;

    public AbstractSteamTritonController(int deviceId, UsbDriverListener listener, int vendorId, int productId) {
        super(deviceId, listener, vendorId, productId);

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

    /**
     * Parses an input report and reports the new state to the listener if it was a state report.
     * Safe to call from multiple threads.
     */
    protected void processInputReport(byte[] data, int length) {
        synchronized (stateLock) {
            if (parseStateReport(data, length) && added && !stopped) {
                reportInput();
            }
        }
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

    private boolean parseStateReport(byte[] data, int length) {
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
        leftTrigger = Math.max(0.0f, normalizeAxis(signShort(data, 6)));
        rightTrigger = Math.max(0.0f, normalizeAxis(signShort(data, 8)));

        // The controller reports up as positive on the Y axes. The listener expects the same
        // convention as the Xbox drivers, which means inverting the raw value.
        leftStickX = normalizeAxis(signShort(data, 10));
        leftStickY = normalizeAxis(-signShort(data, 12));
        rightStickX = normalizeAxis(signShort(data, 14));
        rightStickY = normalizeAxis(-signShort(data, 16));

        return true;
    }

    /**
     * Builds the feature report that turns off lizard mode, including the leading report ID byte.
     */
    protected static byte[] buildLizardModeOffReport() {
        byte[] report = new byte[FEATURE_REPORT_SIZE];
        report[0] = FEATURE_REPORT_ID;
        report[1] = (byte) ID_SET_SETTINGS_VALUES; // Message type
        report[2] = 3;                              // Payload length (one 3-byte setting)
        report[3] = (byte) SETTING_LIZARD_MODE;     // Setting number
        report[4] = (byte) LIZARD_MODE_OFF;         // Setting value (little endian)
        report[5] = 0;
        return report;
    }

    /**
     * Builds the rumble output report, including the leading report ID byte.
     * Motor speeds are unsigned 16-bit values.
     */
    protected static byte[] buildRumbleReport(int low, int high) {
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
        return report;
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // Trigger motors are not supported
    }
}
