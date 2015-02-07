package com.limelight.binding.input.evdev;

public class EvdevEvent {
    public static final int EVDEV_MIN_EVENT_SIZE = 16;
    public static final int EVDEV_MAX_EVENT_SIZE = 24;

    /* Event types */
    public static final short EV_SYN = 0x00;
    public static final short EV_KEY = 0x01;
    public static final short EV_REL = 0x02;
    public static final short EV_MSC = 0x04;

    /* Relative axes */
    public static final short REL_X = 0x00;
    public static final short REL_Y = 0x01;
    public static final short REL_WHEEL = 0x08;

    /* Buttons */
    public static final short BTN_LEFT = 0x110;
    public static final short BTN_RIGHT = 0x111;
    public static final short BTN_MIDDLE = 0x112;
    public static final short BTN_SIDE = 0x113;
    public static final short BTN_EXTRA = 0x114;
    public static final short BTN_FORWARD = 0x115;
    public static final short BTN_BACK = 0x116;
    public static final short BTN_TASK = 0x117;
    public static final short BTN_GAMEPAD = 0x130;

    /* Keys */
    public static final short KEY_Q = 16;

    public final short type;
    public final short code;
    public final int value;

    public EvdevEvent(short type, short code, int value) {
        this.type = type;
        this.code = code;
        this.value = value;
    }
}
