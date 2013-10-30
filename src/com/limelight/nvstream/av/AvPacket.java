package com.limelight.nvstream.av;

public class AvPacket {
	private AvBufferDescriptor buffer;
	
	public AvPacket(AvBufferDescriptor rtpPayload)
	{
		byte[] data = new byte[rtpPayload.length];
		System.arraycopy(rtpPayload.data, rtpPayload.offset, data, 0, rtpPayload.length);
		buffer = new AvBufferDescriptor(data, 0, data.length);
	}
	
	public AvBufferDescriptor getPayload()
	{
		int payloadOffset = buffer.offset+56;
		return new AvBufferDescriptor(buffer.data, payloadOffset, buffer.length-payloadOffset);
	}
}
