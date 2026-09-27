package com.limelight.customcontrols;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * Translates custom control keycodes into Moonlight host inputs.
 *
 * Special codes:
 *   -1  = toggle software keyboard (handled in overlay)
 *   -2  = toggle GUI / controls visibility (handled in overlay)
 *   -3  = mouse primary button (left click)
 *   -4  = mouse secondary button (right click)
 *   -5  = virtual mouse toggle (handled in overlay)
 *   -6  = mouse middle button
 *   -7  = scroll up
 *   -8  = scroll down
 *   Positive codes are GLFW keycodes → converted via CustomJsonParser.glfwToMoonlightVk()
 */
public class CustomInputBridge {

    // Special button constants (match custom control layout data)
    public static final int SPECIAL_KEYBOARD    = -1;
    public static final int SPECIAL_TOGGLECTRL  = -2;
    public static final int SPECIAL_MOUSEPRI    = -3;
    public static final int SPECIAL_MOUSESEC    = -4;
    public static final int SPECIAL_VIRTUALMOUSE= -5;
    public static final int SPECIAL_MOUSEMID    = -6;
    public static final int SPECIAL_SCROLLUP    = -7;
    public static final int SPECIAL_SCROLLDOWN  = -8;

    private NvConnection conn;
    // Retained for future gamepad emulation via ControllerHandler; currently keyboard/mouse only.
    @SuppressWarnings("unused")
    private ControllerHandler controllerHandler;

    /** Callback for special buttons that need UI-level handling. */
    public interface SpecialButtonListener {
        void onToggleKeyboard();
        void onToggleControls();
        void onToggleVirtualMouse();
    }

    private SpecialButtonListener specialListener;

    public CustomInputBridge() {}

    public synchronized void attach(NvConnection conn, ControllerHandler handler,
                                     SpecialButtonListener listener) {
        this.conn              = conn;
        this.controllerHandler = handler;
        this.specialListener   = listener;
    }

    public synchronized void detach() {
        conn              = null;
        controllerHandler = null;
        specialListener   = null;
    }

    /**
     * Called by CustomButtonView when a button is pressed or released.
     * @param keycodes Array of GLFW/special codes from the JSON.
     * @param down     true = press, false = release.
     */
    public synchronized void sendKeycodes(int[] keycodes, boolean down) {
        if (keycodes == null) return;
        for (int code : keycodes) {
            if (code == 0) continue;
            sendSingleCode(code, down);
        }
    }

    private void sendSingleCode(int code, boolean down) {
        // Special buttons
        if (code < 0) {
            handleSpecial(code, down);
            return;
        }

        // Regular GLFW keyboard code
        short vk = CustomJsonParser.glfwToMoonlightVk(code);
        if (vk == 0) return;

        sendKeyboardVk(vk, down);
    }

    private void handleSpecial(int code, boolean down) {
        if (!down) {
            // Mouse button releases need to be forwarded
            if (code == SPECIAL_MOUSEPRI && conn != null)
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
            else if (code == SPECIAL_MOUSESEC && conn != null)
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            else if (code == SPECIAL_MOUSEMID && conn != null)
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
            return;
        }

        // down = true only below
        switch (code) {
            case SPECIAL_KEYBOARD:
                if (specialListener != null) specialListener.onToggleKeyboard();
                break;
            case SPECIAL_TOGGLECTRL:
                if (specialListener != null) specialListener.onToggleControls();
                break;
            case SPECIAL_MOUSEPRI:
                if (conn != null) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                break;
            case SPECIAL_MOUSESEC:
                if (conn != null) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                break;
            case SPECIAL_MOUSEMID:
                if (conn != null) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                break;
            case SPECIAL_VIRTUALMOUSE:
                if (specialListener != null) specialListener.onToggleVirtualMouse();
                break;
            case SPECIAL_SCROLLUP:
                if (conn != null) conn.sendMouseScroll((byte) 1);
                break;
            case SPECIAL_SCROLLDOWN:
                if (conn != null) conn.sendMouseScroll((byte) -1);
                break;
        }
    }

    private void sendKeyboardVk(short vk, boolean down) {
        if (conn == null) return;
        // Moonlight expects the key with the 0x80 prefix applied
        short moonKey = (short)(0x8000 | vk);
        byte action = down ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP;
        // Determine modifier byte from VK code
        byte modifier = 0;
        if (vk == 16) modifier = KeyboardPacket.MODIFIER_SHIFT;
        else if (vk == 17) modifier = KeyboardPacket.MODIFIER_CTRL;
        else if (vk == 18) modifier = KeyboardPacket.MODIFIER_ALT;
        conn.sendKeyboardInput(moonKey, action, modifier, (byte) 0);
    }

    /** Release all currently pressed keys/buttons (e.g. when overlay hides). */
    public synchronized void releaseAll(int[][] allKeycodes) {
        if (allKeycodes == null) return;
        for (int[] kc : allKeycodes) sendKeycodes(kc, false);
    }
}

