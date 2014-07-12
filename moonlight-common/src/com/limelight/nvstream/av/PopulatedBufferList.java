package com.limelight.nvstream.av;

import java.util.concurrent.ArrayBlockingQueue;

public class PopulatedBufferList<T> {
	private ArrayBlockingQueue<T> populatedList;
	private ArrayBlockingQueue<T> freeList;
	
	@SuppressWarnings("unchecked")
	public PopulatedBufferList(int maxQueueSize, BufferFactory factory) {
		this.populatedList = new ArrayBlockingQueue<T>(maxQueueSize, false);
		this.freeList = new ArrayBlockingQueue<T>(maxQueueSize, false);
		
		for (int i = 0; i < maxQueueSize; i++) {
			freeList.add((T) factory.createFreeBuffer());
		}
	}
	
	public T pollFreeObject() {
		return freeList.poll();
	}
	
	public void addPopulatedObject(T object) {
		populatedList.add(object);
	}
	
	public void freePopulatedObject(T object) {
		freeList.add(object);
	}
	
	public void clearPopulatedObjects() {
		T object;
		while ((object = populatedList.poll()) != null) {
			freeList.add(object);
		}
	}
	
	public T pollPopulatedObject() {
		return populatedList.poll();
	}
	
	public T peekPopulatedObject() {
		return populatedList.peek();
	}
	
	public T takePopulatedObject() throws InterruptedException {
		return populatedList.take();
	}
	
	public static interface BufferFactory {
		public Object createFreeBuffer();
	}
}
