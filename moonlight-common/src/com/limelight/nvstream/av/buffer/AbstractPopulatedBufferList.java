package com.limelight.nvstream.av.buffer;

public abstract class AbstractPopulatedBufferList<T> {
	protected final int maxQueueSize;
	protected final BufferFactory factory;
	
	public AbstractPopulatedBufferList(int maxQueueSize, BufferFactory factory) {
		this.factory = factory;
		this.maxQueueSize = maxQueueSize;
	}
	
	public abstract int getPopulatedCount();
	
	public abstract int getFreeCount();
	
	public abstract T pollFreeObject();
	
	public abstract void addPopulatedObject(T object);
	
	public abstract void freePopulatedObject(T object);
	
	public void clearPopulatedObjects() {
		T object;
		while ((object = pollPopulatedObject()) != null) {
			freePopulatedObject(object);
		}
	}
	
	public abstract T pollPopulatedObject();
	
	public abstract T peekPopulatedObject();
	
	public T takePopulatedObject() throws InterruptedException {
		throw new UnsupportedOperationException("Blocking is unsupported on this buffer list");
	}
	
	public static interface BufferFactory {
		public Object createFreeBuffer();
		public void cleanupObject(Object o);
	}
}
