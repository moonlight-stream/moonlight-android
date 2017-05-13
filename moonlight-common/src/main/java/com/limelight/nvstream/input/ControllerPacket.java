package com.limelight.nvstream.input;

public class ControllerPacket {
	public static final short A_FLAG = 0x1000;
	public static final short B_FLAG = 0x2000;
	public static final short X_FLAG = 0x4000;
	public static final short Y_FLAG = (short)0x8000;
	public static final short UP_FLAG = 0x0001;
	public static final short DOWN_FLAG = 0x0002;
	public static final short LEFT_FLAG = 0x0004;
	public static final short RIGHT_FLAG = 0x0008;
	public static final short LB_FLAG = 0x0100;
	public static final short RB_FLAG = 0x0200;
	public static final short PLAY_FLAG = 0x0010;
	public static final short BACK_FLAG = 0x0020;
	public static final short LS_CLK_FLAG = 0x0040;
	public static final short RS_CLK_FLAG = 0x0080;
	public static final short SPECIAL_BUTTON_FLAG = 0x0400;
}