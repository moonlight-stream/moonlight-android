package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvShortBufferPool {
	private ConcurrentLinkedQueue<short[]> bufferList = new ConcurrentLinkedQueue<short[]>();
	private int bufferSize;
	
	public AvShortBufferPool(int size)
	{
		this.bufferSize = size;
	}
	
	public void purge()
	{
		bufferList.clear();
	}
	
	public short[] allocate()
	{
		short[] buff = bufferList.poll();
		if (buff == null) {
			buff = new short[bufferSize];
		}
		return buff;
	}
	
	public void free(short[] buffer)
	{
		bufferList.add(buffer);
	}
}
