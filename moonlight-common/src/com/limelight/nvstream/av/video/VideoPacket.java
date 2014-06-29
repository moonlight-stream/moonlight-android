package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;

public class VideoPacket {
	private ByteBufferDescriptor buffer;
	private ByteBuffer byteBuffer;
	
	private int dataOffset;
	
	private int frameIndex;
	private int packetIndex;
	private int totalPackets;
	private int payloadLength;
	private int flags;
	private int streamPacketIndex;
	
	public static final int FLAG_EOF = 0x2;
	public static final int FLAG_SOF = 0x4;
	
	public static final int HEADER_SIZE = 56;
	
	public VideoPacket(byte[] buffer)
	{
		this.buffer = new ByteBufferDescriptor(buffer, 0, buffer.length);
		this.byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
	}
	
	public void initializeWithLengthNoRtpHeader(int length)
	{
		// Back to beginning
		byteBuffer.rewind();
		
		// Read the video header fields
		frameIndex = byteBuffer.getInt();
		packetIndex = byteBuffer.getInt();
		totalPackets = byteBuffer.getInt();
		flags = byteBuffer.getInt();
		payloadLength = byteBuffer.getInt();
		streamPacketIndex = byteBuffer.getInt();
		
		// Data offset without the RTP header
		dataOffset = HEADER_SIZE;
		
		// Update descriptor length
		buffer.length = length;
	}
	
	public void initializeWithLength(int length)
	{
		// Skip the RTP header
		byteBuffer.position(RtpPacket.HEADER_SIZE);
		
		// Read the video header fields
		frameIndex = byteBuffer.getInt();
		packetIndex = byteBuffer.getInt();
		totalPackets = byteBuffer.getInt();
		flags = byteBuffer.getInt();
		payloadLength = byteBuffer.getInt();
		streamPacketIndex = byteBuffer.getInt();
		
		// Data offset includes the RTP header
		dataOffset = RtpPacket.HEADER_SIZE + HEADER_SIZE;
		
		// Update descriptor length
		buffer.length = length;
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
	
	public byte[] getBuffer()
	{
		return buffer.data;
	}
	
	public void initializePayloadDescriptor(ByteBufferDescriptor bb)
	{
		bb.reinitialize(buffer.data, buffer.offset+dataOffset, buffer.length-dataOffset);
	}
}
