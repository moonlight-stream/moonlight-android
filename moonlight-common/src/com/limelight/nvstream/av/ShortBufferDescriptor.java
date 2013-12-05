package com.limelight.nvstream.av;

public class ShortBufferDescriptor {
	public short[] data;
	public int offset;
	public int length;
	
	public ShortBufferDescriptor(short[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
	
	public ShortBufferDescriptor(ShortBufferDescriptor desc)
	{
		this.data = desc.data;
		this.offset = desc.offset;
		this.length = desc.length;
	}
	
	public void reinitialize(short[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
}
