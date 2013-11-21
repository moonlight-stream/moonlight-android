package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvObjectPool<T> {
	private ConcurrentLinkedQueue<T> objectList = new ConcurrentLinkedQueue<T>();
	
	public void purge()
	{
		objectList.clear();
	}
	
	public T tryAllocate()
	{
		return objectList.poll();
	}
	
	public void free(T object)
	{
		objectList.add(object);
	}
}
