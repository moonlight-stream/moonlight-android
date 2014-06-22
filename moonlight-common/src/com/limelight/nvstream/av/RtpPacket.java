package com.limelight.nvstream.av;

import java.nio.ByteBuffer;

public class RtpPacket {
	
	private byte packetType;
	private short seqNum;
	private ByteBufferDescriptor buffer;
	
	public static final int HEADER_SIZE = 12;
	
	public RtpPacket(ByteBufferDescriptor buffer)
	{
		this.buffer = new ByteBufferDescriptor(buffer);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer.data, buffer.offset, buffer.length);
		
		// Discard the first byte
		bb.position(bb.position()+1);
		
		// Get the packet type
		packetType = bb.get();
		
		// Get the sequence number
		seqNum = bb.getShort();
	}
	
	public byte getPacketType()
	{
		return packetType;
	}
	
	public short getSequenceNumber()
	{
		return seqNum;
	}
	
	public byte[] getBackingBuffer()
	{
		return buffer.data;
	}
	
	public ByteBufferDescriptor getNewPayloadDescriptor()
	{
		return new ByteBufferDescriptor(buffer.data, buffer.offset+HEADER_SIZE, buffer.length-HEADER_SIZE);
	}
}
