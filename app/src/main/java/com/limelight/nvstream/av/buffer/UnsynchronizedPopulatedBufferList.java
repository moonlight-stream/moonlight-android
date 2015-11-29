package com.limelight.nvstream.av.buffer;

import java.util.ArrayList;

public class UnsynchronizedPopulatedBufferList<T> extends AbstractPopulatedBufferList<T> {
	private final ArrayList<T> populatedList;
	private final ArrayList<T> freeList;

	@SuppressWarnings("unchecked")
	public UnsynchronizedPopulatedBufferList(int maxQueueSize, BufferFactory factory) {
		super(maxQueueSize, factory);
		
		this.populatedList = new ArrayList<T>(maxQueueSize);
		this.freeList = new ArrayList<T>(maxQueueSize);
		
		for (int i = 0; i < maxQueueSize; i++) {
			freeList.add((T) factory.createFreeBuffer());
		}
	}
	
	@Override
	public int getPopulatedCount() {
		return populatedList.size();
	}
	
	@Override
	public int getFreeCount() {
		return freeList.size();
	}
	
	@Override
	public T pollFreeObject() {
		if (freeList.isEmpty()) {
			return null;
		}
		
		return freeList.remove(0);
	}
	
	@Override
	public void addPopulatedObject(T object) {
		populatedList.add(object);
	}
	
	@Override
	public void freePopulatedObject(T object) {
		factory.cleanupObject(object);
		freeList.add(object);
	}
	
	@Override
	public T pollPopulatedObject() {
		if (populatedList.isEmpty()) {
			return null;
		}
		
		return populatedList.remove(0);
	}

	@Override
	public T peekPopulatedObject() {
		if (populatedList.isEmpty()) {
			return null;
		}
		
		return populatedList.get(0);
	}
}
