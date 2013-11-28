package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.AvByteBufferDescriptor;

public class AvVideoPacket {
	private AvByteBufferDescriptor buffer;
	
	private int frameIndex;
	private int packetIndex;
	private int totalPackets;
	private int payloadLength;
	
	public AvVideoPacket(AvByteBufferDescriptor rtpPayload)
	{
		buffer = new AvByteBufferDescriptor(rtpPayload);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer.data).order(ByteOrder.LITTLE_ENDIAN);
		bb.position(buffer.offset);
		
		frameIndex = bb.getInt();
		packetIndex = bb.getInt();
		totalPackets = bb.getInt();
		
		bb.position(bb.position()+4);
		
		payloadLength = bb.getInt();
	}
	
	public int getFrameIndex()
	{
		return frameIndex;
	}
	
	public int getPacketIndex()
	{
		return packetIndex;
	}
	
	public int getPayloadLength()
	{
		return payloadLength;
	}
	
	public int getTotalPackets()
	{
		return totalPackets;
	}
	
	public AvByteBufferDescriptor getNewPayloadDescriptor()
	{
		return new AvByteBufferDescriptor(buffer.data, buffer.offset+56, buffer.length-56);
	}
}
