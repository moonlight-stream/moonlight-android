package com.limelight.nvstream.av;

import java.nio.ByteBuffer;

public class AvRtpPacket {
	
	private byte packetType;
	private short seqNum;
	private AvByteBufferDescriptor buffer;
	
	private static AvObjectPool<AvRtpPacket> pool = new AvObjectPool<AvRtpPacket>();
	
	public static AvRtpPacket create(AvByteBufferDescriptor payload) {
		return createNoCopy(AvByteBufferDescriptor.newDescriptor(payload));
	}
	
	public static AvRtpPacket createNoCopy(AvByteBufferDescriptor payload) {
		AvRtpPacket pkt = pool.tryAllocate();
		if (pkt == null) {
			pkt = new AvRtpPacket();
		}
		pkt.initialize(payload);
		return pkt;
	}
	
	private AvRtpPacket() { }
	
	private void initialize(AvByteBufferDescriptor buffer)
	{
		this.buffer = buffer;
		
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
	
	public void free()
	{
		buffer.free();
		pool.free(this);
	}
	
	public AvByteBufferDescriptor getNewPayloadDescriptor()
	{
		return AvByteBufferDescriptor.newDescriptor(buffer.data, buffer.offset+12, buffer.length-12);
	}
}
