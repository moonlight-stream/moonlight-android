package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class ConfigTuple {
	public short packetType;
	public short payloadLength;
	
	public static final short HEADER_LENGTH = 4;
	
	public ConfigTuple(short packetType, short payloadLength)
	{
		this.packetType = packetType;
		this.payloadLength = payloadLength;
	}
	
	public abstract byte[] payloadToWire();
	
	public byte[] toWire()
	{
		byte[] payload = payloadToWire();
		ByteBuffer bb = ByteBuffer.allocate(HEADER_LENGTH + (payload != null ? payload.length : 0))
				.order(ByteOrder.LITTLE_ENDIAN);
		
		bb.putShort(packetType);
		bb.putShort(payloadLength);
		
		if (payload != null) {
			bb.put(payload);
		}
		
		return bb.array();
	}
	
	@Override
	public int hashCode()
	{
		return packetType;
	}
	
	@Override
	public boolean equals(Object o)
	{
		// We only compare the packet types on purpose
		if (o instanceof ConfigTuple) {
			return ((ConfigTuple)o).packetType == packetType;
		}
		else {
			return false;
		}
	}
}
