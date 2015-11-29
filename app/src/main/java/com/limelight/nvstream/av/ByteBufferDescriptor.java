package com.limelight.nvstream.av;

public class ByteBufferDescriptor {
	public byte[] data;
	public int offset;
	public int length;
	
	public ByteBufferDescriptor nextDescriptor;
	
	public ByteBufferDescriptor(byte[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
	
	public ByteBufferDescriptor(ByteBufferDescriptor desc)
	{
		this.data = desc.data;
		this.offset = desc.offset;
		this.length = desc.length;
	}
	
	public void reinitialize(byte[] data, int offset, int length)
	{
		this.data = data;
		this.offset = offset;
		this.length = length;
		this.nextDescriptor = null;
	}
	
	public void print()
	{
		print(offset, length);
	}
	
	public void print(int length)
	{
		print(this.offset, length);
	}
	
	public void print(int offset, int length)
	{
		for (int i = offset; i < offset+length;) {
			if (i + 8 <= offset+length) {
				System.out.printf("%x: %02x %02x %02x %02x %02x %02x %02x %02x\n", i,
						data[i], data[i+1], data[i+2], data[i+3], data[i+4], data[i+5], data[i+6], data[i+7]);
				i += 8;
			}
			else {
				System.out.printf("%x: %02x \n", i, data[i]);
				i++;
			}
		}
		System.out.println();
	}
}
