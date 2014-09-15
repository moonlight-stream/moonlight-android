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
	
	public abstract ByteOrder getPayloadByteOrder();
	
	public abstract void toWirePayload(ByteBuffer bb);
	
	public abstract int getPacketLength();
	
	public void toWireHeader(ByteBuffer bb)
	{
		// We don't use putInt() here because it will be subject to the byte order
		// of the byte buffer. We just write it as a big endian int.
		bb.put((byte)(packetType >> 24));
		bb.put((byte)(packetType >> 16));
		bb.put((byte)(packetType >> 8));
		bb.put((byte)(packetType & 0xFF));
	}
	
	public void toWire(ByteBuffer bb)
	{
		bb.rewind();
		toWireHeader(bb);
		toWirePayload(bb);
	}
}
