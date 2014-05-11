package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.ByteBufferDescriptor;

public class VideoPacket {
	private ByteBufferDescriptor buffer;
	
	private int frameIndex;
	private int packetIndex;
	private int totalPackets;
	private int payloadLength;
	private int flags;
	private int streamPacketIndex;
	
	public static final int FLAG_EOF = 0x2;
	public static final int FLAG_SOF = 0x4;
	
	public VideoPacket(ByteBufferDescriptor rtpPayload)
	{
		buffer = new ByteBufferDescriptor(rtpPayload);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer.data).order(ByteOrder.LITTLE_ENDIAN);
		bb.position(buffer.offset);
		
		frameIndex = bb.getInt();
		packetIndex = bb.getInt();
		totalPackets = bb.getInt();
		flags = bb.getInt();
		payloadLength = bb.getInt();
		streamPacketIndex = bb.getInt();
	}
	
	public int getFlags()
	{
		return flags;
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
	
	public int getStreamPacketIndex()
	{
		return streamPacketIndex;
	}
	
	public ByteBufferDescriptor getNewPayloadDescriptor()
	{
		return new ByteBufferDescriptor(buffer.data, buffer.offset+56, buffer.length-56);
	}
}
