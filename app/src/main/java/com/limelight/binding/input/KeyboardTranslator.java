package com.limelight.binding.input;

import android.annotation.TargetApi;
import android.hardware.input.InputManager;
import android.os.Build;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.Arrays;

/**
 * Class to translate a Android key code into the codes GFE is expecting
 * @author Diego Waxemberg
 * @author Cameron Gutman
 */
public class KeyboardTranslator implements InputManager.InputDeviceListener {
    
    /**
     * GFE's prefix for every key code
     */
    private static final short KEY_PREFIX = (short) 0x80;
    
    public static final int VK_0 = 48;
    public static final int VK_9 = 57;
    public static final int VK_A = 65;
    public static final int VK_C = 67;
    public static final int VK_D = 68;
    public static final int VK_G = 71;
    public static final int VK_V = 86;
    public static final int VK_Z = 90;
    public static final int VK_NUMPAD0 = 96;
    public static final int VK_BACK_SLASH = 92;
    public static final int VK_CAPS_LOCK = 20;
    public static final int VK_CLEAR = 12;
    public static final int VK_COMMA = 44;
    public static final int VK_BACK_SPACE = 8;
    public static final int VK_EQUALS = 61;
    public static final int VK_ESCAPE = 27;
    public static final int VK_F1 = 112;
    public static final int VK_F11 = 122;
    public static final int VK_END = 35;
    public static final int VK_HOME = 36;
    public static final int VK_NUM_LOCK = 144;
    public static final int VK_PAGE_UP = 33;
    public static final int VK_PAGE_DOWN = 34;
    public static final int VK_PLUS = 521;
    public static final int VK_CLOSE_BRACKET = 93;
    public static final int VK_SCROLL_LOCK = 145;
    public static final int VK_SEMICOLON = 59;
    public static final int VK_SLASH = 47;
    public static final int VK_SPACE = 32;
    public static final int VK_PRINTSCREEN = 154;
    public static final int VK_TAB = 9;
    public static final int VK_LEFT = 37;
    public static final int VK_RIGHT = 39;
    public static final int VK_UP = 38;
    public static final int VK_DOWN = 40;
    public static final int VK_BACK_QUOTE = 192;
    public static final int VK_QUOTE = 222;
    public static final int VK_PAUSE = 19;
    public static final int VK_LWIN = 91;
    public static final int VK_LSHIFT = 160;
    public static final int VK_LCONTROL = 162;

    private static class KeyboardMapping {
        private final InputDevice device;
        private final int[] deviceKeyCodeToQwertyKeyCode;

        @TargetApi(33)
        public KeyboardMapping(InputDevice device) {
            int maxKeyCode = KeyEvent.getMaxKeyCode();

            this.device = device;
            this.deviceKeyCodeToQwertyKeyCode = new int[maxKeyCode + 1];

            // Any unmatched keycodes are treated as unknown
            Arrays.fill(deviceKeyCodeToQwertyKeyCode, KeyEvent.KEYCODE_UNKNOWN);

            for (int i = 0; i <= maxKeyCode; i++) {
                int deviceKeyCode = device.getKeyCodeForKeyLocation(i);
                if (deviceKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    deviceKeyCodeToQwertyKeyCode[deviceKeyCode] = i;
                }
            }
        }

        @TargetApi(33)
        public int getDeviceKeyCodeForQwertyKeyCode(int qwertyKeyCode) {
            return device.getKeyCodeForKeyLocation(qwertyKeyCode);
        }

        public int getQwertyKeyCodeForDeviceKeyCode(int deviceKeyCode) {
            if (deviceKeyCode > KeyEvent.getMaxKeyCode()) {
                return KeyEvent.KEYCODE_UNKNOWN;
            }

            return deviceKeyCodeToQwertyKeyCode[deviceKeyCode];
        }
    }

    private final SparseArray<KeyboardMapping> keyboardMappings = new SparseArray<>();

    public KeyboardTranslator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (int deviceId : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(deviceId);
                if (device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                    keyboardMappings.set(deviceId, new KeyboardMapping(device));
                }
            }
        }
    }

    public boolean hasNormalizedMapping(int keycode, int deviceId) {
        if (deviceId >= 0) {
            KeyboardMapping mapping = keyboardMappings.get(deviceId);
            if (mapping != null) {
                // Try to map this device-specific keycode onto a QWERTY layout.
                // GFE assumes incoming keycodes are from a QWERTY keyboard.
                int qwertyKeyCode = mapping.getQwertyKeyCodeForDeviceKeyCode(keycode);
                if (qwertyKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Translates the given keycode and returns the GFE keycode
     * @param keycode the code to be translated
     * @param deviceId InputDevice.getId() or -1 if unknown
     * @return a GFE keycode for the given keycode
     */
    public short translate(int keycode, int deviceId) {
        int translated;

        // If a device ID was provided, look up the keyboard mapping
        if (deviceId >= 0) {
            KeyboardMapping mapping = keyboardMappings.get(deviceId);
            if (mapping != null) {
                // Try to map this device-specific keycode onto a QWERTY layout.
                // GFE assumes incoming keycodes are from a QWERTY keyboard.
                int qwertyKeyCode = mapping.getQwertyKeyCodeForDeviceKeyCode(keycode);
                if (qwertyKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    keycode = qwertyKeyCode;
                }
            }
        }
        
        // This is a poor man's mapping between Android key codes
        // and Windows VK_* codes. For all defined VK_ codes, see:
        // https://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
        if (keycode >= KeyEvent.KEYCODE_0 &&
            keycode <= KeyEvent.KEYCODE_9) {
            translated = (keycode - KeyEvent.KEYCODE_0) + VK_0;
        }
        else if (keycode >= KeyEvent.KEYCODE_A &&
                 keycode <= KeyEvent.KEYCODE_Z) {
            translated = (keycode - KeyEvent.KEYCODE_A) + VK_A;
        }
        else if (keycode >= KeyEvent.KEYCODE_NUMPAD_0 &&
                 keycode <= KeyEvent.KEYCODE_NUMPAD_9) {
            translated = (keycode - KeyEvent.KEYCODE_NUMPAD_0) + VK_NUMPAD0;
        }
        else if (keycode >= KeyEvent.KEYCODE_F1 &&
                 keycode <= KeyEvent.KEYCODE_F12) {
            translated = (keycode - KeyEvent.KEYCODE_F1) + VK_F1;
        }
        else {
            switch (keycode) {
            case KeyEvent.KEYCODE_ALT_LEFT:
                translated = 0xA4;
                break;

            case KeyEvent.KEYCODE_ALT_RIGHT:
                translated = 0xA5;
                break;
                
            case KeyEvent.KEYCODE_BACKSLASH:
                translated = 0xdc;
                break;
                
            case KeyEvent.KEYCODE_CAPS_LOCK:
                translated = VK_CAPS_LOCK;
                break;
                
            case KeyEvent.KEYCODE_CLEAR:
                translated = VK_CLEAR;
                break;
                
            case KeyEvent.KEYCODE_COMMA:
                translated = 0xbc;
                break;
                
            case KeyEvent.KEYCODE_CTRL_LEFT:
                translated = VK_LCONTROL;
                break;

            case KeyEvent.KEYCODE_CTRL_RIGHT:
                translated = 0xA3;
                break;
                
            case KeyEvent.KEYCODE_DEL:
                translated = VK_BACK_SPACE;
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                translated = 0x0d;
                break;

            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_EQUALS:
                translated = 0xbb;
                break;
                
            case KeyEvent.KEYCODE_ESCAPE:
                translated = VK_ESCAPE;
                break;
                
            case KeyEvent.KEYCODE_FORWARD_DEL:
                translated = 0x2e;
                break;
                
            case KeyEvent.KEYCODE_INSERT:
                translated = 0x2d;
                break;
                
            case KeyEvent.KEYCODE_LEFT_BRACKET:
                translated = 0xdb;
                break;

            case KeyEvent.KEYCODE_META_LEFT:
                translated = VK_LWIN;
                break;

            case KeyEvent.KEYCODE_META_RIGHT:
                translated = 0x5c;
                break;

            case KeyEvent.KEYCODE_MENU:
                translated = 0x5d;
                break;

            case KeyEvent.KEYCODE_MINUS:
                translated = 0xbd;
                break;
                
            case KeyEvent.KEYCODE_MOVE_END:
                translated = VK_END;
                break;
                
            case KeyEvent.KEYCODE_MOVE_HOME:
                translated = VK_HOME;
                break;
                
            case KeyEvent.KEYCODE_NUM_LOCK:
                translated = VK_NUM_LOCK;
                break;
                
            case KeyEvent.KEYCODE_PAGE_DOWN:
                translated = VK_PAGE_DOWN;
                break;
                
            case KeyEvent.KEYCODE_PAGE_UP:
                translated = VK_PAGE_UP;
                break;
                
            case KeyEvent.KEYCODE_PERIOD:
                translated = 0xbe;
                break;
                
            case KeyEvent.KEYCODE_RIGHT_BRACKET:
                translated = 0xdd;
                break;
                
            case KeyEvent.KEYCODE_SCROLL_LOCK:
                translated = VK_SCROLL_LOCK;
                break;
                
            case KeyEvent.KEYCODE_SEMICOLON:
                translated = 0xba;
                break;
                
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                translated = VK_LSHIFT;
                break;

            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                translated = 0xA1;
                break;
                
            case KeyEvent.KEYCODE_SLASH:
                translated = 0xbf;
                break;
                
            case KeyEvent.KEYCODE_SPACE:
                translated = VK_SPACE;
                break;
                
            case KeyEvent.KEYCODE_SYSRQ:
                // Android defines this as SysRq/PrntScrn
                translated = VK_PRINTSCREEN;
                break;
                
            case KeyEvent.KEYCODE_TAB:
                translated = VK_TAB;
                break;
                
            case KeyEvent.KEYCODE_DPAD_LEFT:
                translated = VK_LEFT;
                break;
                
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                translated = VK_RIGHT;
                break;
                
            case KeyEvent.KEYCODE_DPAD_UP:
                translated = VK_UP;
                break;
                
            case KeyEvent.KEYCODE_DPAD_DOWN:
                translated = VK_DOWN;
                break;
                
            case KeyEvent.KEYCODE_GRAVE:
                translated = VK_BACK_QUOTE;
                break;
                
            case KeyEvent.KEYCODE_APOSTROPHE:
                translated = 0xde;
                break;
                
            case KeyEvent.KEYCODE_BREAK:
                translated = VK_PAUSE;
                break;

            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                translated = 0x6F;
                break;

            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                translated = 0x6A;
                break;

            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                translated = 0x6D;
                break;

            case KeyEvent.KEYCODE_NUMPAD_ADD:
                translated = 0x6B;
                break;

            case KeyEvent.KEYCODE_NUMPAD_DOT:
                translated = 0x6E;
                break;

            default:
                return 0;
            }
        }
        
        return (short) ((KEY_PREFIX << 8) | translated);
    }

    @Override
    public void onInputDeviceAdded(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InputDevice device = InputDevice.getDevice(index);
            if (device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                keyboardMappings.put(index, new KeyboardMapping(device));
            }
        }
    }

    @Override
    public void onInputDeviceRemoved(int index) {
        keyboardMappings.remove(index);
    }

    @Override
    public void onInputDeviceChanged(int index) {
        keyboardMappings.remove(index);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InputDevice device = InputDevice.getDevice(index);
            if (device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                keyboardMappings.set(index, new KeyboardMapping(device));
            }
        }
    }
}
