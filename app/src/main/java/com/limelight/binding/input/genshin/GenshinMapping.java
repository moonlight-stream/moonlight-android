package com.limelight.binding.input.genshin;

import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.EnumMap;
import java.util.Map;

/**
 * Default PC keyboard/mouse mapping for Genshin Impact (US QWERTY Win32 VK codes
 * with Moonlight/GFE 0x80 prefix, verified against KeyboardTranslator.java and Limelight.h).
 */
public final class GenshinMapping {

    private static final int KEY_PREFIX = 0x80 << 8;

    private static short vk(int win32Vk) {
        return (short) (KEY_PREFIX | (win32Vk & 0xFF));
    }

    // Standard Win32 Virtual Key codes with GFE 0x8000 prefix
    public static final short KEY_TAB    = vk(0x09);
    public static final short KEY_ESC    = vk(0x1B);
    public static final short KEY_SPACE  = vk(0x20);

    public static final short KEY_1      = vk(0x31);
    public static final short KEY_2      = vk(0x32);
    public static final short KEY_3      = vk(0x33);
    public static final short KEY_4      = vk(0x34);

    public static final short KEY_A      = vk(0x41);
    public static final short KEY_B      = vk(0x42);
    public static final short KEY_C      = vk(0x43);
    public static final short KEY_D      = vk(0x44);
    public static final short KEY_E      = vk(0x45);
    public static final short KEY_F      = vk(0x46);
    public static final short KEY_J      = vk(0x4A);
    public static final short KEY_M      = vk(0x4D);
    public static final short KEY_Q      = vk(0x51);
    public static final short KEY_R      = vk(0x52);
    public static final short KEY_S      = vk(0x53);
    public static final short KEY_T      = vk(0x54);
    public static final short KEY_V      = vk(0x56);
    public static final short KEY_W      = vk(0x57);
    public static final short KEY_X      = vk(0x58);
    public static final short KEY_Z      = vk(0x5A);

    public static final short KEY_LSHIFT = vk(0xA0);
    public static final short KEY_LCTRL  = vk(0xA2);
    public static final short KEY_LALT   = vk(0xA4);

    public static final byte MOUSE_NONE   = 0;
    public static final byte MOUSE_LEFT   = MouseButtonPacket.BUTTON_LEFT;
    public static final byte MOUSE_MIDDLE = MouseButtonPacket.BUTTON_MIDDLE;
    public static final byte MOUSE_RIGHT  = MouseButtonPacket.BUTTON_RIGHT;

    public static final long CHAR_BURST_LONG_PRESS_MS = 400L;

    public enum InputType {
        KEY,
        MOUSE_BUTTON,
        SPRINT,
        CHARACTER_SLOT
    }

    public enum Action {
        ATTACK("attack", "ATK", "LMB", InputType.MOUSE_BUTTON, (short) 0, MOUSE_LEFT, false, 0),
        SKILL_E("skill_e", "SKILL", "E", InputType.KEY, KEY_E, MOUSE_NONE, false, 0),
        BURST_Q("burst_q", "BURST", "Q", InputType.KEY, KEY_Q, MOUSE_NONE, false, 0),
        JUMP("jump", "JUMP", "SPC", InputType.KEY, KEY_SPACE, MOUSE_NONE, false, 0),
        SPRINT("sprint", "DASH", "SHF", InputType.SPRINT, KEY_LSHIFT, MOUSE_RIGHT, false, 0),
        INTERACT_F("interact_f", "PICK", "F", InputType.KEY, KEY_F, MOUSE_NONE, false, 0),

        CHAR_1("char_1", "P1", "1", InputType.CHARACTER_SLOT, KEY_1, MOUSE_NONE, false, 1),
        CHAR_2("char_2", "P2", "2", InputType.CHARACTER_SLOT, KEY_2, MOUSE_NONE, false, 2),
        CHAR_3("char_3", "P3", "3", InputType.CHARACTER_SLOT, KEY_3, MOUSE_NONE, false, 3),
        CHAR_4("char_4", "P4", "4", InputType.CHARACTER_SLOT, KEY_4, MOUSE_NONE, false, 4),

        AIM_R("aim_r", "AIM", "R", InputType.KEY, KEY_R, MOUSE_NONE, true, 0),
        GADGET_Z("gadget_z", "GDG", "Z", InputType.KEY, KEY_Z, MOUSE_NONE, true, 0),
        SPECIAL_T("special_t", "ACT", "T", InputType.KEY, KEY_T, MOUSE_NONE, true, 0),
        QUEST_NAV_V("quest_nav_v", "NAV", "V", InputType.KEY, KEY_V, MOUSE_NONE, true, 0),
        DROP_X("drop_x", "DROP", "X", InputType.KEY, KEY_X, MOUSE_NONE, true, 0),
        ELEMENTAL_SIGHT("elemental_sight", "EYE", "MMB", InputType.MOUSE_BUTTON, (short) 0, MOUSE_MIDDLE, true, 0),

        MAP_M("map_m", "MAP", "M", InputType.KEY, KEY_M, MOUSE_NONE, true, 0),
        BAG_B("bag_b", "BAG", "B", InputType.KEY, KEY_B, MOUSE_NONE, true, 0),
        CHARACTER_C("char_c", "CHR", "C", InputType.KEY, KEY_C, MOUSE_NONE, true, 0),
        PAIMON_ESC("paimon_esc", "MENU", "ESC", InputType.KEY, KEY_ESC, MOUSE_NONE, true, 0),
        QUEST_J("quest_j", "QST", "J", InputType.KEY, KEY_J, MOUSE_NONE, true, 0),
        QUICK_WHEEL_TAB("quick_tab", "WHEEL", "TAB", InputType.KEY, KEY_TAB, MOUSE_NONE, true, 0);

        public final String id;
        public final String label;
        public final String keyBadge;
        public final InputType inputType;
        public final short defaultKeyCode;
        public final byte defaultMouseButton;
        public final boolean secondary;
        public final int characterSlot;

        Action(String id, String label, String keyBadge, InputType inputType,
               short defaultKeyCode, byte defaultMouseButton, boolean secondary, int characterSlot) {
            this.id = id;
            this.label = label;
            this.keyBadge = keyBadge;
            this.inputType = inputType;
            this.defaultKeyCode = defaultKeyCode;
            this.defaultMouseButton = defaultMouseButton;
            this.secondary = secondary;
            this.characterSlot = characterSlot;
        }
    }

    private final Map<Action, Short> customKeyOverrides = new EnumMap<>(Action.class);

    public void setCustomKeyCode(Action action, short keyCode) {
        if (action == null) {
            return;
        }
        if (keyCode == 0) {
            customKeyOverrides.remove(action);
        } else {
            customKeyOverrides.put(action, keyCode);
        }
    }

    public short getKeyCode(Action action) {
        Short override = customKeyOverrides.get(action);
        return override != null ? override : action.defaultKeyCode;
    }

    public static short getCharacterSlotKey(int slot) {
        switch (slot) {
            case 1: return KEY_1;
            case 2: return KEY_2;
            case 3: return KEY_3;
            case 4: return KEY_4;
            default: throw new IllegalArgumentException("Invalid character slot: " + slot);
        }
    }
}
