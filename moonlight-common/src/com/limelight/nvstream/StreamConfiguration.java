package com.limelight.nvstream;

public class StreamConfiguration {
	private int width, height;
	private int refreshRate;
	private int bitrate;
	private int maxPacketSize;
	
	public StreamConfiguration(int width, int height, int refreshRate, int bitrate) {
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
		this.bitrate = bitrate;
		this.maxPacketSize = 1024;
	}
	
	public StreamConfiguration(int width, int height, int refreshRate, int bitrate, int maxPacketSize) {
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
		this.bitrate = bitrate;
		this.maxPacketSize = maxPacketSize;
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
	
	public int getBitrate() {
		return bitrate;
	}
	
	public int getMaxPacketSize() {
		return maxPacketSize;
	}
}
