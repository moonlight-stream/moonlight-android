package com.limelight.nvstream.av;

public class AvPacket {
	private AvBufferDescriptor buffer;
	
	public AvPacket(AvBufferDescriptor rtpPayload)
	{
		buffer = new AvBufferDescriptor(rtpPayload.data, rtpPayload.offset, rtpPayload.length);
	}
	
	public AvBufferDescriptor getNewPayloadDescriptor()
	{
		return new AvBufferDescriptor(buffer.data, buffer.offset+56, buffer.length-56);
	}
}
