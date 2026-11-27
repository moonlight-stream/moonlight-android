package com.limelight.binding.input.genshin;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java state machine that translates Genshin overlay gestures into standard
 * Moonlight KB+M packets (MoonBridge.sendKeyboardInput / sendMouseMove / sendMouseButton).
 *
 * Guarantees symmetric key/button down/up and zero stuck keys on releaseAll().
 */
public class GenshinInputHandler {

    public interface InputSink {
        void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags);
        void sendMouseMove(short deltaX, short deltaY);
        void sendMouseButtonDown(byte mouseButton);
        void sendMouseButtonUp(byte mouseButton);
    }

    public static InputSink fromConnection(final NvConnection conn) {
        return new InputSink() {
            @Override
            public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) {
                if (conn != null) {
                    conn.sendKeyboardInput(keyMap, keyDirection, modifier, flags);
                }
            }

            @Override
            public void sendMouseMove(short deltaX, short deltaY) {
                if (conn != null) {
                    conn.sendMouseMove(deltaX, deltaY);
                }
            }

            @Override
            public void sendMouseButtonDown(byte mouseButton) {
                if (conn != null) {
                    conn.sendMouseButtonDown(mouseButton);
                }
            }

            @Override
            public void sendMouseButtonUp(byte mouseButton) {
                if (conn != null) {
                    conn.sendMouseButtonUp(mouseButton);
                }
            }
        };
    }

    public static final float DEFAULT_DEADZONE = 0.15f;
    public static final float DEFAULT_WALK_THRESHOLD = 0.50f;
    public static final float MIN_CAMERA_SENSITIVITY = 0.5f;
    public static final float MAX_CAMERA_SENSITIVITY = 2.0f;

    private final InputSink sink;
    private final GenshinMapping mapping;

    // Preserves press order so releaseAll() can unwind cleanly
    private final Set<Short> pressedKeys = new LinkedHashSet<>();
    private final Set<Byte> pressedMouseButtons = new LinkedHashSet<>();
    private byte activeModifiers = 0;

    // Joystick movement state
    private boolean joyW = false;
    private boolean joyA = false;
    private boolean joyS = false;
    private boolean joyD = false;
    private boolean joyWalk = false;

    // Config
    private float deadzone = DEFAULT_DEADZONE;
    private float walkThreshold = DEFAULT_WALK_THRESHOLD;
    private float cameraSensitivity = 1.0f;
    private boolean sprintToggleMode = false;
    private boolean sprintUseRmb = false;
    private boolean sprintToggledOn = false;

    // Sub-pixel remainder accumulation for smooth camera movement
    private float cameraRemainderX = 0f;
    private float cameraRemainderY = 0f;

    public GenshinInputHandler(InputSink sink) {
        this(sink, new GenshinMapping());
    }

    public GenshinInputHandler(InputSink sink, GenshinMapping mapping) {
        if (sink == null) {
            throw new IllegalArgumentException("InputSink must not be null");
        }
        this.sink = sink;
        this.mapping = mapping != null ? mapping : new GenshinMapping();
    }

    public GenshinMapping getMapping() {
        return mapping;
    }

    public void setDeadzone(float deadzone) {
        this.deadzone = Math.max(0.05f, Math.min(0.45f, deadzone));
    }

    public void setWalkThreshold(float walkThreshold) {
        this.walkThreshold = Math.max(this.deadzone + 0.05f, Math.min(0.90f, walkThreshold));
    }

    public void setCameraSensitivity(float sensitivity) {
        this.cameraSensitivity = Math.max(MIN_CAMERA_SENSITIVITY, Math.min(MAX_CAMERA_SENSITIVITY, sensitivity));
    }

    public float getCameraSensitivity() {
        return cameraSensitivity;
    }

    public void setSprintToggleMode(boolean toggleMode) {
        if (this.sprintToggleMode != toggleMode) {
            releaseSprintOutput();
            this.sprintToggledOn = false;
            this.sprintToggleMode = toggleMode;
        }
    }

    public boolean isSprintToggleMode() {
        return sprintToggleMode;
    }

    public void setSprintUseRmb(boolean useRmb) {
        if (this.sprintUseRmb != useRmb) {
            releaseSprintOutput();
            this.sprintToggledOn = false;
            this.sprintUseRmb = useRmb;
        }
    }

    public boolean isSprintUseRmb() {
        return sprintUseRmb;
    }

    public boolean isSprintToggledOn() {
        return sprintToggledOn;
    }

    private static byte modifierBitForKey(short keyCode) {
        if (keyCode == GenshinMapping.KEY_LSHIFT) {
            return KeyboardPacket.MODIFIER_SHIFT;
        } else if (keyCode == GenshinMapping.KEY_LCTRL) {
            return KeyboardPacket.MODIFIER_CTRL;
        } else if (keyCode == GenshinMapping.KEY_LALT) {
            return KeyboardPacket.MODIFIER_ALT;
        }
        return 0;
    }

    public synchronized void pressKey(short keyCode) {
        if (keyCode == 0 || pressedKeys.contains(keyCode)) {
            return;
        }
        byte modBit = modifierBitForKey(keyCode);
        if (modBit != 0) {
            activeModifiers |= modBit;
        }
        pressedKeys.add(keyCode);
        sink.sendKeyboardInput(keyCode, KeyboardPacket.KEY_DOWN, activeModifiers, (byte) 0);
    }

    public synchronized void releaseKey(short keyCode) {
        if (keyCode == 0 || !pressedKeys.contains(keyCode)) {
            return;
        }
        pressedKeys.remove(keyCode);
        byte modBit = modifierBitForKey(keyCode);
        if (modBit != 0) {
            activeModifiers &= (byte) ~modBit;
        }
        sink.sendKeyboardInput(keyCode, KeyboardPacket.KEY_UP, activeModifiers, (byte) 0);
    }

    public synchronized void pressMouseButton(byte mouseButton) {
        if (mouseButton == GenshinMapping.MOUSE_NONE || pressedMouseButtons.contains(mouseButton)) {
            return;
        }
        pressedMouseButtons.add(mouseButton);
        sink.sendMouseButtonDown(mouseButton);
    }

    public synchronized void releaseMouseButton(byte mouseButton) {
        if (mouseButton == GenshinMapping.MOUSE_NONE || !pressedMouseButtons.contains(mouseButton)) {
            return;
        }
        pressedMouseButtons.remove(mouseButton);
        sink.sendMouseButtonUp(mouseButton);
    }

    /**
     * Handles standard button press/release (Attack, E, Q, Space, Shift/RMB sprint, F, R, Z, T, V, M, B, C, Esc, MMB, X, J, Tab).
     * Character slots 1..4 are handled via tapCharacterSlot / triggerCharacterBurst so short tap vs 400ms long-press are cleanly separated.
     */
    public synchronized void onActionDown(GenshinMapping.Action action) {
        if (action == null) {
            return;
        }
        switch (action.inputType) {
            case KEY:
                pressKey(mapping.getKeyCode(action));
                break;
            case MOUSE_BUTTON:
                pressMouseButton(action.defaultMouseButton);
                break;
            case SPRINT:
                if (sprintToggleMode) {
                    sprintToggledOn = !sprintToggledOn;
                    if (sprintToggledOn) {
                        pressSprintOutput();
                    } else {
                        releaseSprintOutput();
                    }
                } else {
                    pressSprintOutput();
                }
                break;
            case CHARACTER_SLOT:
                // Handled on tap release or long-press timer by overlay
                break;
        }
    }

    public synchronized void onActionUp(GenshinMapping.Action action) {
        if (action == null) {
            return;
        }
        switch (action.inputType) {
            case KEY:
                releaseKey(mapping.getKeyCode(action));
                break;
            case MOUSE_BUTTON:
                releaseMouseButton(action.defaultMouseButton);
                break;
            case SPRINT:
                if (!sprintToggleMode) {
                    releaseSprintOutput();
                }
                break;
            case CHARACTER_SLOT:
                break;
        }
    }

    private void pressSprintOutput() {
        if (sprintUseRmb) {
            pressMouseButton(MouseButtonPacket.BUTTON_RIGHT);
        } else {
            pressKey(GenshinMapping.KEY_LSHIFT);
        }
    }

    private void releaseSprintOutput() {
        if (sprintUseRmb) {
            releaseMouseButton(MouseButtonPacket.BUTTON_RIGHT);
        } else {
            releaseKey(GenshinMapping.KEY_LSHIFT);
        }
    }

    /**
     * Short tap on character 1..4: switch to that character.
     */
    public synchronized void tapCharacterSlot(int slot) {
        short key = GenshinMapping.getCharacterSlotKey(slot);
        pressKey(key);
        releaseKey(key);
    }

    /**
     * Long-press (>= 400ms) on character 1..4: switch + immediate Elemental Burst.
     * Strict order: Left Alt DOWN -> number DOWN -> number UP -> Left Alt UP.
     */
    public synchronized void triggerCharacterBurst(int slot) {
        short numKey = GenshinMapping.getCharacterSlotKey(slot);
        boolean altWasAlreadyHeld = pressedKeys.contains(GenshinMapping.KEY_LALT);
        if (!altWasAlreadyHeld) {
            pressKey(GenshinMapping.KEY_LALT);
        }
        pressKey(numKey);
        releaseKey(numKey);
        if (!altWasAlreadyHeld) {
            releaseKey(GenshinMapping.KEY_LALT);
        }
    }

    /**
     * Updates 8-direction WASD + walk (Left Ctrl) state from normalized joystick coordinates.
     *
     * @param normX [-1..1], positive is right
     * @param normY [-1..1], positive is down (screen coordinates), negative is up
     */
    public synchronized void updateJoystick(float normX, float normY) {
        double mag = Math.hypot(normX, normY);
        if (mag < deadzone) {
            applyJoystickState(false, false, false, false, false);
            return;
        }

        // Convert screen Y (+down) to math Y (+up)
        double angleDeg = Math.toDegrees(Math.atan2(-normY, normX));
        if (angleDeg < 0.0) {
            angleDeg += 360.0;
        }

        // 8 sectors of 45 degrees centered on E(0), NE(45), N(90), NW(135), W(180), SW(225), S(270), SE(315)
        int sector = ((int) Math.floor((angleDeg + 22.5) / 45.0)) % 8;
        boolean wantW = false;
        boolean wantA = false;
        boolean wantS = false;
        boolean wantD = false;

        switch (sector) {
            case 0: // E
                wantD = true;
                break;
            case 1: // NE
                wantW = true;
                wantD = true;
                break;
            case 2: // N
                wantW = true;
                break;
            case 3: // NW
                wantW = true;
                wantA = true;
                break;
            case 4: // W
                wantA = true;
                break;
            case 5: // SW
                wantS = true;
                wantA = true;
                break;
            case 6: // S
                wantS = true;
                break;
            case 7: // SE
                wantS = true;
                wantD = true;
                break;
        }

        boolean wantWalk = mag < walkThreshold;
        applyJoystickState(wantW, wantA, wantS, wantD, wantWalk);
    }

    public synchronized void releaseJoystick() {
        applyJoystickState(false, false, false, false, false);
    }

    private void applyJoystickState(boolean wantW, boolean wantA, boolean wantS, boolean wantD, boolean wantWalk) {
        // Release keys no longer active first so opposite directions never overlap
        if (joyW && !wantW) {
            releaseKey(GenshinMapping.KEY_W);
        }
        if (joyA && !wantA) {
            releaseKey(GenshinMapping.KEY_A);
        }
        if (joyS && !wantS) {
            releaseKey(GenshinMapping.KEY_S);
        }
        if (joyD && !wantD) {
            releaseKey(GenshinMapping.KEY_D);
        }
        if (joyWalk && !wantWalk) {
            releaseKey(GenshinMapping.KEY_LCTRL);
        }

        // Press newly active keys immediately
        if (!joyWalk && wantWalk) {
            pressKey(GenshinMapping.KEY_LCTRL);
        }
        if (!joyW && wantW) {
            pressKey(GenshinMapping.KEY_W);
        }
        if (!joyA && wantA) {
            pressKey(GenshinMapping.KEY_A);
        }
        if (!joyS && wantS) {
            pressKey(GenshinMapping.KEY_S);
        }
        if (!joyD && wantD) {
            pressKey(GenshinMapping.KEY_D);
        }

        joyW = wantW;
        joyA = wantA;
        joyS = wantS;
        joyD = wantD;
        joyWalk = wantWalk;
    }

    /**
     * Sends relative mouse motion scaled by cameraSensitivity.
     * Never sends absolute cursor position.
     */
    public synchronized void sendCameraDelta(float rawDx, float rawDy) {
        float scaledX = rawDx * cameraSensitivity + cameraRemainderX;
        float scaledY = rawDy * cameraSensitivity + cameraRemainderY;

        int intDx = (int) scaledX;
        int intDy = (int) scaledY;

        cameraRemainderX = scaledX - intDx;
        cameraRemainderY = scaledY - intDy;

        if (intDx == 0 && intDy == 0) {
            return;
        }

        short clampedDx = clampShort(intDx);
        short clampedDy = clampShort(intDy);
        sink.sendMouseMove(clampedDx, clampedDy);
    }

    public synchronized void resetCameraRemainder() {
        cameraRemainderX = 0f;
        cameraRemainderY = 0f;
    }

    /**
     * Releases EVERY currently pressed keyboard key and mouse button in reverse press order.
     * Must be called on pause, focus loss, disconnect, overlay disable, edit mode, and destroy.
     */
    public synchronized void releaseAll() {
        // Release non-modifier keys first, then modifiers, in reverse order
        List<Short> keysSnapshot = new ArrayList<>(pressedKeys);
        for (int i = keysSnapshot.size() - 1; i >= 0; i--) {
            short key = keysSnapshot.get(i);
            if (modifierBitForKey(key) == 0) {
                releaseKey(key);
            }
        }
        for (int i = keysSnapshot.size() - 1; i >= 0; i--) {
            short key = keysSnapshot.get(i);
            if (modifierBitForKey(key) != 0) {
                releaseKey(key);
            }
        }

        List<Byte> mouseSnapshot = new ArrayList<>(pressedMouseButtons);
        for (int i = mouseSnapshot.size() - 1; i >= 0; i--) {
            releaseMouseButton(mouseSnapshot.get(i));
        }

        joyW = joyA = joyS = joyD = joyWalk = false;
        sprintToggledOn = false;
        activeModifiers = 0;
        cameraRemainderX = 0f;
        cameraRemainderY = 0f;
    }

    public synchronized boolean isKeyPressed(short keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public synchronized boolean isMouseButtonPressed(byte mouseButton) {
        return pressedMouseButtons.contains(mouseButton);
    }

    public synchronized int getPressedKeyCount() {
        return pressedKeys.size();
    }

    public synchronized int getPressedMouseButtonCount() {
        return pressedMouseButtons.size();
    }

    public synchronized byte getActiveModifiers() {
        return activeModifiers;
    }

    private static short clampShort(int value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
    }
}
