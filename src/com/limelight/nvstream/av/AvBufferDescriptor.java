package com.limelight.nvstream.av;

public class AvBufferDescriptor {
	public byte[] data;
	public int offset;
	public int length;
	
	public AvBufferDescriptor(byte[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
}
