package com.limelight.nvstream.av;

import java.nio.ByteBuffer;

public class AvRtpPacket {
	
	private byte packetType;
	private short seqNum;
	private AvBufferDescriptor buffer;
	
	public AvRtpPacket(AvBufferDescriptor buffer)
	{
		this.buffer = new AvBufferDescriptor(buffer);
		
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
	
	public AvBufferDescriptor getNewPayloadDescriptor()
	{
		return new AvBufferDescriptor(buffer.data, buffer.offset+12, buffer.length-12);
	}
}
