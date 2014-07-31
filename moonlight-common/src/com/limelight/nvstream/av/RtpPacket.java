package com.limelight.nvstream.av;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RtpPacket {
	
	private byte packetType;
	private short seqNum;
	private int headerSize;
	
	private ByteBufferDescriptor buffer;
	private ByteBuffer bb;
	
	public static final int FLAG_EXTENSION = 0x10;
	
	public static final int FIXED_HEADER_SIZE = 12;
	public static final int MAX_HEADER_SIZE = 16;

	
	public RtpPacket(byte[] buffer)
	{
		this.buffer = new ByteBufferDescriptor(buffer, 0, buffer.length);
		this.bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
	}
	
	public void initializeWithLength(int length)
	{
		// Rewind to start
		bb.rewind();
		
		// Read the RTP header byte
		byte header = bb.get();
		
		// Get the packet type
		packetType = bb.get();
		
		// Get the sequence number
		seqNum = bb.getShort();
		
		// If an extension is present, read the fields
		headerSize = FIXED_HEADER_SIZE;
		if ((header & FLAG_EXTENSION) != 0) {
			headerSize += 4; // 2 additional fields
		}
		
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
		bb.reinitialize(buffer.data, buffer.offset+headerSize, buffer.length-headerSize);
	}
}
