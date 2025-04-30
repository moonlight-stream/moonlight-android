package com.limelight.nvstream.input;

public class ControllerPacket {
    public static final int A_FLAG = 0x1000;
    public static final int B_FLAG = 0x2000;
    public static final int X_FLAG = 0x4000;
    public static final int Y_FLAG = 0x8000;
    public static final int UP_FLAG = 0x0001;
    public static final int DOWN_FLAG = 0x0002;
    public static final int LEFT_FLAG = 0x0004;
    public static final int RIGHT_FLAG = 0x0008;
    public static final int LB_FLAG = 0x0100;
    public static final int RB_FLAG = 0x0200;
    public static final int PLAY_FLAG = 0x0010;
    public static final int BACK_FLAG = 0x0020;
    public static final int LS_CLK_FLAG = 0x0040;
    public static final int RS_CLK_FLAG = 0x0080;
    public static final int SPECIAL_BUTTON_FLAG = 0x0400;

    // Extended buttons (Sunshine only)
    public static final int PADDLE1_FLAG  = 0x010000;
    public static final int PADDLE2_FLAG  = 0x020000;
    public static final int PADDLE3_FLAG  = 0x040000;
    public static final int PADDLE4_FLAG  = 0x080000;
    public static final int TOUCHPAD_FLAG = 0x100000; // Touchpad buttons on Sony controllers
    public static final int MISC_FLAG     = 0x200000; // Share/Mic/Capture/Mute buttons on various controllers
}