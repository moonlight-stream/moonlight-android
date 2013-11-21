package com.limelight.nvstream.av;

public class AvShortBufferDescriptor {
	public short[] data;
	public int offset;
	public int length;
	
	private static AvObjectPool<AvShortBufferDescriptor> pool = new AvObjectPool<AvShortBufferDescriptor>();
	public static AvShortBufferDescriptor newDescriptor(short[] data, int offset, int length) {
		AvShortBufferDescriptor buffer = pool.tryAllocate();
		if (buffer != null) {
			buffer.data = data;
			buffer.offset = offset;
			buffer.length = length;
			return buffer;
		}
		else {
			return new AvShortBufferDescriptor(data, offset, length);
		}
	}
	
	public static AvShortBufferDescriptor newDescriptor(AvShortBufferDescriptor buffer) {
		return newDescriptor(buffer.data, buffer.offset, buffer.length);
	}
	
	private AvShortBufferDescriptor(short[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
	
	private AvShortBufferDescriptor(AvShortBufferDescriptor desc)
	{
		this.data = desc.data;
		this.offset = desc.offset;
		this.length = desc.length;
	}
	
	public void free() {
		pool.free(this);
	}
}
