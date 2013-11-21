package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvShortBufferPool {
	private ConcurrentLinkedQueue<short[]> bufferList = new ConcurrentLinkedQueue<short[]>();
	private int bufferSize;
	
	private static final boolean doubleFreeDebug = true;
	
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
		short[] buff;
		if (doubleFreeDebug) {
			buff = null;
		}
		else {
			buff = bufferList.poll();
		}
		if (buff == null) {
			buff = new short[bufferSize];
		}
		return buff;
	}
	
	public void free(short[] buffer)
	{
		if (doubleFreeDebug) {
			for (short[] buf : bufferList) {
				if (buf == buffer) {
					throw new IllegalStateException("Double free detected");
				}
			}
		}
		
		bufferList.add(buffer);
	}
}
