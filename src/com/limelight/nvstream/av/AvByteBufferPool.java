package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvByteBufferPool {
	private ConcurrentLinkedQueue<byte[]> bufferList = new ConcurrentLinkedQueue<byte[]>();
	private int bufferSize;
	
	public AvByteBufferPool(int size)
	{
		this.bufferSize = size;
	}
	
	public void purge()
	{
		bufferList.clear();
	}
	
	public byte[] allocate()
	{
		byte[] buff = bufferList.poll();
		if (buff == null) {
			buff = new byte[bufferSize];
		}
		return buff;
	}
	
	public void free(byte[] buffer)
	{
		bufferList.add(buffer);
	}
}
