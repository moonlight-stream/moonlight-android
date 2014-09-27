package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class KeyboardPacket extends InputPacket {
	private static final int PACKET_TYPE = 0x0A;
	public static final int PACKET_LENGTH = 14;
	
	public static final byte KEY_DOWN = 0x03;
	public static final byte KEY_UP = 0x04;

	public static final byte MODIFIER_SHIFT = 0x01;
	public static final byte MODIFIER_CTRL = 0x02;
	public static final byte MODIFIER_ALT = 0x04;
	
	short keyCode;
	byte keyDirection;
	byte modifier;
	
	public KeyboardPacket(short keyCode, byte keyDirection, byte modifier) {
		super(PACKET_TYPE);
		this.keyCode = keyCode;
		this.keyDirection = keyDirection;
		this.modifier = modifier;
	}

	@Override
	public void toWirePayload(ByteBuffer bb) {
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put(keyDirection);
		bb.putShort((short)0);
		bb.putShort((short)0);
		bb.putShort(keyCode);
		bb.put(modifier);
		bb.put((byte)0);
		bb.put((byte)0);
	}

	@Override
	public int getPacketLength() {
		return PACKET_LENGTH;
	}
}
