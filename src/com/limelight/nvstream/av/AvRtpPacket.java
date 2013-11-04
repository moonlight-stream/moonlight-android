package com.limelight.nvstream.av;

import java.nio.ByteBuffer;

public class AvRtpPacket {
	
	private short seqNum;
	private AvBufferDescriptor buffer;
	
	public AvRtpPacket(AvBufferDescriptor buffer)
	{
		this.buffer = new AvBufferDescriptor(buffer);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer.data, buffer.offset, buffer.length);
		
		// Discard the first couple of bytes
		bb.getShort();
		
		// Get the sequence number
		seqNum = bb.getShort();
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
