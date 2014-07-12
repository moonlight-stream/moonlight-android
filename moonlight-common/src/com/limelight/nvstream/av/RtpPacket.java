package com.limelight.nvstream.av;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RtpPacket {
	
	private byte packetType;
	private short seqNum;
	
	private ByteBufferDescriptor buffer;
	private ByteBuffer bb;
	
	public static final int HEADER_SIZE = 12;
	
	public RtpPacket(byte[] buffer)
	{
		this.buffer = new ByteBufferDescriptor(buffer, 0, buffer.length);
		this.bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
	}
	
	public void initializeWithLength(int length)
	{
		// Discard the first byte
		bb.position(1);
		
		// Get the packet type
		packetType = bb.get();
		
		// Get the sequence number
		seqNum = bb.getShort();
		
		// Update descriptor length
		buffer.length = length;
	}
	
	public byte getPacketType()
	{
		return packetType;
	}
	
	public short getSequenceNumber()
	{
		return seqNum;
	}
	
	public byte[] getBuffer()
	{
		return buffer.data;
	}
	
	public void initializePayloadDescriptor(ByteBufferDescriptor bb)
	{
		bb.reinitialize(buffer.data, buffer.offset+HEADER_SIZE, buffer.length-HEADER_SIZE);
	}
}
