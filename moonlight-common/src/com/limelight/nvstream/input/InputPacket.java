package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class InputPacket {
	public static final int HEADER_LENGTH = 0x4;
	
	protected int packetType;
	
	public InputPacket(int packetType)
	{
		this.packetType = packetType;
	}
		
	public abstract void toWirePayload(ByteBuffer bb);
	
	public abstract int getPacketLength();
	
	public void toWireHeader(ByteBuffer bb)
	{
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(packetType);
	}
	
	public void toWire(ByteBuffer bb)
	{
		bb.rewind();
		toWireHeader(bb);
		toWirePayload(bb);
	}
}
