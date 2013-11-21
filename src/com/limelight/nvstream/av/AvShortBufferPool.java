package com.limelight.nvstream.av;

import java.util.LinkedList;

public class AvShortBufferPool {
	private LinkedList<short[]> bufferList = new LinkedList<short[]>();
	private int bufferSize;
	
	public AvShortBufferPool(int size)
	{
		this.bufferSize = size;
	}
	
	public synchronized void purge()
	{
		this.bufferList = new LinkedList<short[]>();
	}
	
	public synchronized short[] allocate()
	{
		if (bufferList.isEmpty())
		{
			return new short[bufferSize];
		}
		else
		{
			return bufferList.removeFirst();
		}
	}
	
	public synchronized void free(short[] buffer)
	{
		bufferList.addFirst(buffer);
	}
}
