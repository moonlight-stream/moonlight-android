package com.limelight.binding.input.driver;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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
    private static final int BTN_RIGHT_PAD_TOUCH = 0x00200000;
    private static final int BTN_LEFT_PAD_TOUCH = 0x02000000;

    // Trackpads are reported after the sticks: X and Y (signed, up is positive), then pressure
    private static final int LEFT_PAD_X_OFFSET = 18;
    private static final int LEFT_PAD_Y_OFFSET = 20;
    private static final int RIGHT_PAD_X_OFFSET = 24;
    private static final int RIGHT_PAD_Y_OFFSET = 26;
    private static final int PAD_REPORT_MIN_SIZE = 28; // Through the right pad Y axis

    // Mouse pixels per raw trackpad unit at 100% speed. A full swipe across a pad covers
    // 65536 units. The speed settings scale this.
    private static final float BASE_PAD_SENSITIVITY = 0.03f;

    // Scroll units per raw trackpad unit at 100% speed, where 120 units is one wheel notch
    private static final float BASE_SCROLL_SENSITIVITY = 0.05f;

    private volatile float padSensitivity = BASE_PAD_SENSITIVITY * 0.25f;
    private volatile float scrollSensitivity = BASE_SCROLL_SENSITIVITY * 0.5f;

    /**
     * Tracks one trackpad. Sliding a finger either moves the mouse like a laptop trackpad or,
     * for a scroll pad, turns the mouse wheel. Clicking the pad presses a mouse button.
     */
    private static class Pad {
        final byte mouseButton;
        final boolean scrolls;
        boolean touching;
        boolean clicking;
        int lastX, lastY;
        float remainderX, remainderY;

        Pad(byte mouseButton, boolean scrolls) {
            this.mouseButton = mouseButton;
            this.scrolls = scrolls;
        }
    }

    // The "..." button is sent as a Guide + A chord, played out like a person pressing it by hand:
    // Guide goes down first, A is tapped once Guide has been seen, and they are released in
    // reverse. Sending both at the same instant makes Steam treat the A as a plain "select".
    private static final long CHORD_GUIDE_LEAD_MS = 200;
    private static final long CHORD_A_HOLD_MS = 120;
    private static final long CHORD_GUIDE_TAIL_MS = 60;

    private ScheduledExecutorService chordExecutor; // Guarded by stateLock
    private boolean chordRunning, chordGuide, chordA, qamWasDown;
    private int lastButtons;

    // The right pad is the primary mouse, so its click is a left click. The left pad is the
    // scroll wheel, and its click is a right click.
    private final Pad rightPad = new Pad(MouseButtonPacket.BUTTON_LEFT, false);
    private final Pad leftPad = new Pad(MouseButtonPacket.BUTTON_RIGHT, true);

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
                        ControllerPacket.PADDLE3_FLAG | ControllerPacket.PADDLE4_FLAG;
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

        // Hosts treat the "misc" button as the Guide button on Xbox-style controllers, so the
        // "..." (quick access) button can't be sent as misc without also opening the Steam menu.
        // Steam opens its quick access menu with Guide + A on standard gamepads, so play that
        // chord for it instead.
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buttons & BTN_STEAM);

        lastButtons = buttons;
        boolean qamDown = (buttons & BTN_QAM) != 0;
        if (qamDown && !qamWasDown && !chordRunning && added && !stopped) {
            startQamChord();
        }
        qamWasDown = qamDown;
        applyChordFlags();

        setButtonFlag(ControllerPacket.UP_FLAG, buttons & BTN_DPAD_UP);
        setButtonFlag(ControllerPacket.DOWN_FLAG, buttons & BTN_DPAD_DOWN);
        setButtonFlag(ControllerPacket.LEFT_FLAG, buttons & BTN_DPAD_LEFT);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, buttons & BTN_DPAD_RIGHT);

        setButtonFlag(ControllerPacket.PADDLE1_FLAG, buttons & BTN_R4);
        setButtonFlag(ControllerPacket.PADDLE2_FLAG, buttons & BTN_L4);
        setButtonFlag(ControllerPacket.PADDLE3_FLAG, buttons & BTN_R5);
        setButtonFlag(ControllerPacket.PADDLE4_FLAG, buttons & BTN_L5);

        // The trackpads drive the mouse instead of the gamepad. Hosts have no use for them as
        // gamepad buttons on most virtual controllers.
        if (length >= PAD_REPORT_MIN_SIZE) {
            boolean live = added && !stopped;
            updatePad(rightPad, (buttons & BTN_RIGHT_PAD_TOUCH) != 0, (buttons & BTN_RIGHT_PAD_CLICK) != 0,
                    signShort(data, RIGHT_PAD_X_OFFSET), signShort(data, RIGHT_PAD_Y_OFFSET), live);
            updatePad(leftPad, (buttons & BTN_LEFT_PAD_TOUCH) != 0, (buttons & BTN_LEFT_PAD_CLICK) != 0,
                    signShort(data, LEFT_PAD_X_OFFSET), signShort(data, LEFT_PAD_Y_OFFSET), live);
        }

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

    private void updatePad(Pad pad, boolean touch, boolean click, int x, int y, boolean live) {
        if (touch && pad.touching && pad.scrolls) {
            // Raw Y is positive going up, and so is scrolling: sliding a finger up scrolls up
            float scroll = (y - pad.lastY) * scrollSensitivity + pad.remainderY;
            int amount = (int) scroll;
            pad.remainderY = scroll - amount;
            pad.remainderX = 0;

            if (live && amount != 0) {
                reportMouseScroll((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amount)));
            }
        }
        else if (touch && pad.touching) {
            // Raw Y is positive going up, but mouse Y is positive going down
            float dx = (x - pad.lastX) * padSensitivity + pad.remainderX;
            float dy = -(y - pad.lastY) * padSensitivity + pad.remainderY;
            int moveX = (int) dx;
            int moveY = (int) dy;

            // Keep the fractions so slow movements still add up
            pad.remainderX = dx - moveX;
            pad.remainderY = dy - moveY;

            if (live && (moveX != 0 || moveY != 0)) {
                reportMouseMove((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, moveX)),
                        (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, moveY)));
            }
        }
        else {
            // A new touch only sets where we start from, so the cursor doesn't jump
            pad.remainderX = 0;
            pad.remainderY = 0;
        }

        pad.touching = touch;
        pad.lastX = x;
        pad.lastY = y;

        if (click != pad.clicking) {
            pad.clicking = click;
            if (live) {
                reportMouseButton(pad.mouseButton, click);
            }
        }
    }

    /**
     * Sets how fast the trackpads move the mouse and scroll, as percentages of the base speed.
     */
    public void setTrackpadSpeeds(int mouseSpeedPercent, int scrollSpeedPercent) {
        padSensitivity = BASE_PAD_SENSITIVITY * Math.max(1, mouseSpeedPercent) / 100.0f;
        scrollSensitivity = BASE_SCROLL_SENSITIVITY * Math.max(1, scrollSpeedPercent) / 100.0f;
    }

    // Overlays the "..." chord on top of whatever the real Guide and A buttons are doing
    private void applyChordFlags() {
        if (chordGuide) {
            buttonFlags |= ControllerPacket.SPECIAL_BUTTON_FLAG;
        }
        else {
            setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, lastButtons & BTN_STEAM);
        }

        if (chordA) {
            buttonFlags |= ControllerPacket.A_FLAG;
        }
        else {
            setButtonFlag(ControllerPacket.A_FLAG, lastButtons & BTN_A);
        }
    }

    // Must be called with stateLock held
    private void startQamChord() {
        chordRunning = true;
        chordGuide = true;
        scheduleChordStep(1, CHORD_GUIDE_LEAD_MS);
    }

    // Must be called with stateLock held
    private void scheduleChordStep(final int step, long delayMs) {
        if (chordExecutor == null) {
            chordExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "SteamTritonChord");
                    t.setDaemon(true);
                    return t;
                }
            });
        }

        chordExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                runChordStep(step);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runChordStep(int step) {
        synchronized (stateLock) {
            if (!chordRunning || stopped) {
                return;
            }

            long nextDelayMs = -1;
            if (step == 1) {
                chordA = true; // Guide has been down for a while, now tap A
                nextDelayMs = CHORD_A_HOLD_MS;
            }
            else if (step == 2) {
                chordA = false; // Release A first
                nextDelayMs = CHORD_GUIDE_TAIL_MS;
            }
            else {
                chordGuide = false; // Then Guide
                chordRunning = false;
            }

            applyChordFlags();
            if (added) {
                reportInput();
            }

            if (nextDelayMs >= 0) {
                scheduleChordStep(step + 1, nextDelayMs);
            }
        }
    }

    /**
     * Lets go of any mouse button held by a trackpad click and cancels a "..." chord in progress.
     * Call this when the controller goes away.
     */
    protected void releaseHeldInput() {
        synchronized (stateLock) {
            chordRunning = false;
            chordGuide = false;
            chordA = false;
            qamWasDown = false;
            if (chordExecutor != null) {
                chordExecutor.shutdownNow();
                chordExecutor = null;
            }

            for (Pad pad : new Pad[] { rightPad, leftPad }) {
                if (pad.clicking) {
                    pad.clicking = false;
                    reportMouseButton(pad.mouseButton, false);
                }
                pad.touching = false;
                pad.remainderX = 0;
                pad.remainderY = 0;
            }
        }
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
