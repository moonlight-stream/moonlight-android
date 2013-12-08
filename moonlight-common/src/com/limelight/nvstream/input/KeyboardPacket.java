package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class KeyboardPacket extends InputPacket {
	private static final int PACKET_TYPE = 0x0A;
	private static final int PACKET_LENGTH = 14;
	
	public static final byte KEY_DOWN = 0x03;
	public static final byte KEY_UP = 0x04;

	private short keyCode;
	private byte keyDirection;
	
	public KeyboardPacket(short keyCode, byte keyDirection) {
		super(PACKET_TYPE);
		this.keyCode = keyCode;
		this.keyDirection = keyDirection;
	}

	public byte[] toWireHeader()
	{
		ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		
		bb.putInt(packetType);
		
		return bb.array();
	}
	
	@Override
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		
		bb.put(toWireHeader());
		bb.put(keyDirection);
		bb.putShort((short)0);
		bb.putShort((short)0);
		bb.putShort(keyCode);
		bb.put((byte)0);
		bb.put((byte)0);
		bb.put((byte)0);
		byte[] packet = bb.array();
		for (int i = 0; i < packet.length; i++) {
			System.out.printf("%02x ", packet[i]);
		}
		System.out.println();
		return bb.array();
	}
}
