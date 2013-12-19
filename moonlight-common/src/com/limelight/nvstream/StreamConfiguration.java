package com.limelight.nvstream;

public class StreamConfiguration {
	private int width, height;
	private int refreshRate;
	
	public StreamConfiguration(int width, int height, int refreshRate) {
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int getRefreshRate() {
		return refreshRate;
	}
}
