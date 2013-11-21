package com.limelight.nvstream.av;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AvObjectPool<T> {
	private ConcurrentLinkedQueue<T> objectList = new ConcurrentLinkedQueue<T>();
	
	private static final boolean doubleFreeDebug = false;
	
	public void purge()
	{
		objectList.clear();
	}
	
	public T tryAllocate()
	{
		if (doubleFreeDebug) {
			return null;
		}
		else {
			return objectList.poll();
		}
	}
	
	public void free(T object)
	{
		if (doubleFreeDebug) {
			for (T obj : objectList) {
				if (obj == object) {
					throw new IllegalStateException("Double free detected");
				}
			}
		}
		
		objectList.add(object);
	}
}
