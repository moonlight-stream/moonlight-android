package com.limelight.nvstream.av;

public class AvByteBufferDescriptor {
	public byte[] data;
	public int offset;
	public int length;
	public Object context;
	
	private static AvObjectPool<AvByteBufferDescriptor> pool = new AvObjectPool<AvByteBufferDescriptor>();
	public static AvByteBufferDescriptor newDescriptor(byte[] data, int offset, int length) {
		AvByteBufferDescriptor buffer = pool.tryAllocate();
		if (buffer != null) {
			buffer.data = data;
			buffer.offset = offset;
			buffer.length = length;
			buffer.context = null;
			return buffer;
		}
		else {
			return new AvByteBufferDescriptor(data, offset, length);
		}
	}
	
	public static AvByteBufferDescriptor newDescriptor(AvByteBufferDescriptor buffer) {
		return newDescriptor(buffer.data, buffer.offset, buffer.length);
	}
	
	private AvByteBufferDescriptor(byte[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
		this.context = null;
	}
	
	private AvByteBufferDescriptor(AvByteBufferDescriptor desc)
	{
		this.data = desc.data;
		this.offset = desc.offset;
		this.length = desc.length;
		this.context = null;
	}
	
	public void free() {
		pool.free(this);
	}
}
