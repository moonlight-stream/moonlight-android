package com.limelight.nvstream;

public class StreamConfiguration {
	private String app;
	private int width, height;
	private int refreshRate;
	private int bitrate;
	
	public StreamConfiguration(String app, int width, int height, int refreshRate, int bitrate) {
		this.app = app;
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
		this.bitrate = bitrate;
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
	
	public String getApp() {
		return app;
	}
}
