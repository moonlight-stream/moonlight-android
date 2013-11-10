package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.AvBufferDescriptor;

public class AvVideoPacket {
	private AvBufferDescriptor buffer;
	
	public AvVideoPacket(AvBufferDescriptor rtpPayload)
	{
		buffer = new AvBufferDescriptor(rtpPayload);
	}
	
	public AvBufferDescriptor getNewPayloadDescriptor()
	{
		return new AvBufferDescriptor(buffer.data, buffer.offset+56, buffer.length-56);
	}
}
