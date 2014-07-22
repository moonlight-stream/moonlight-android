package com.limelight.nvstream;

public class StreamConfiguration {
	private String app;
	private int width, height;
	private int refreshRate;
	private int bitrate;
	private int maxPacketSize;
	private boolean sops;
	
	public StreamConfiguration(String app, int width, int height, int refreshRate, int bitrate) {
		this.app = app;
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
		this.bitrate = bitrate;
		this.maxPacketSize = 1024;
		this.sops = true;
	}
	
	public StreamConfiguration(String app, int width, int height, int refreshRate, int bitrate, int maxPacketSize, boolean sops) {
		this.app = app;
		this.width = width;
		this.height = height;
		this.refreshRate = refreshRate;
		this.bitrate = bitrate;
		this.maxPacketSize = maxPacketSize;
		this.sops = sops;
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

	public String getApp() {
		return app;
	}
	
	public boolean getSops() {
		return sops;
	}
}
