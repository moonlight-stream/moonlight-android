package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvObjectPool;

public class AvVideoPacket {
	private AvByteBufferDescriptor buffer;
	private int refCount;
	
	private static AvObjectPool<AvVideoPacket> pool = new AvObjectPool<AvVideoPacket>();
	
	public static AvVideoPacket createNoCopy(AvByteBufferDescriptor payload) {
		AvVideoPacket pkt = pool.tryAllocate();
		if (pkt != null) {
			pkt.buffer = payload;
			pkt.refCount = 0;
			return pkt;
		}
		else {
			return new AvVideoPacket(payload);
		}
	}
	
	private AvVideoPacket(AvByteBufferDescriptor rtpPayload)
	{
		buffer = rtpPayload;
	}
	
	public AvByteBufferDescriptor getNewPayloadDescriptor()
	{
		return AvByteBufferDescriptor.newDescriptor(buffer.data, buffer.offset+56, buffer.length-56);
	}
	
	public int addRef()
	{
		return ++refCount;
	}
	
	public int release()
	{
		return --refCount;
	}
	
	public void free()
	{
		buffer.free();
		pool.free(this);
	}
}
