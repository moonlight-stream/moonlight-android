package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class NvInputPacket {
	public static final int HEADER_LENGTH = 0x4;
	
	protected int packetType;
	
	public NvInputPacket(int packetType)
	{
		this.packetType = packetType;
	}
	
	public abstract byte[] toWire();
	
	public byte[] toWireHeader()
	{
		ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		
		bb.putInt(packetType);
		
		return bb.array();
	}
}
