package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvByteBufferPool {
	private ConcurrentLinkedQueue<byte[]> bufferList = new ConcurrentLinkedQueue<byte[]>();
	private int bufferSize;
	
	private static final boolean doubleFreeDebug = true;
	
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
		byte[] buff;
		if (doubleFreeDebug) {
			buff = null;
		}
		else {
			buff = bufferList.poll();
		}
		if (buff == null) {
			buff = new byte[bufferSize];
		}
		return buff;
	}
	
	public void free(byte[] buffer)
	{
		if (doubleFreeDebug) {
			for (byte[] buf : bufferList) {
				if (buf == buffer) {
					throw new IllegalStateException("Double free detected");
				}
			}
		}
		
		bufferList.add(buffer);
	}
}
