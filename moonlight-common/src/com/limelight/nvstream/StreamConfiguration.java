package com.limelight.nvstream;

public class StreamConfiguration {
	private String app;
	private int width, height;
	private int refreshRate;
	private int bitrate;
	private boolean sops;
	private boolean enableAdaptiveResolution;
	private boolean playLocalAudio;
	private int maxPacketSize;
	private boolean remote;
	
	public static class Builder {
		private StreamConfiguration config = new StreamConfiguration();
		
		public StreamConfiguration.Builder setApp(String app) {
			config.app = app;
			return this;
		}
		
		public StreamConfiguration.Builder setRemote(boolean remote) {
			config.remote = remote;
			return this;
		}
		
		public StreamConfiguration.Builder setResolution(int width, int height) {
			config.width = width;
			config.height = height;
			return this;
		}
		
		public StreamConfiguration.Builder setRefreshRate(int refreshRate) {
			config.refreshRate = refreshRate;
			return this;
		}
		
		public StreamConfiguration.Builder setBitrate(int bitrate) {
			config.bitrate = bitrate;
			return this;
		}
		
		public StreamConfiguration.Builder setEnableSops(boolean enable) {
			config.sops = enable;
			return this;
		}
		
		public StreamConfiguration.Builder enableAdaptiveResolution(boolean enable) {
			config.enableAdaptiveResolution = enable;
			return this;
		}
		
		public StreamConfiguration.Builder enableLocalAudioPlayback(boolean enable) {
			config.playLocalAudio = enable;
			return this;
		}
		
		public StreamConfiguration.Builder setMaxPacketSize(int maxPacketSize) {
			config.maxPacketSize = maxPacketSize;
			return this;
		}
		
		public StreamConfiguration build() {
			return config;
		}
	}
	
	private StreamConfiguration() {
		// Set default attributes
		this.app = "Steam";
		this.width = 1280;
		this.height = 720;
		this.refreshRate = 60;
		this.bitrate = 10000;
		this.maxPacketSize = 1024;
		this.sops = true;
		this.enableAdaptiveResolution = false;
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
	
	public boolean getAdaptiveResolutionEnabled() {
		return enableAdaptiveResolution;
	}
	
	public boolean getPlayLocalAudio() {
		return playLocalAudio;
	}
	
	public boolean getRemote() {
		return remote;
	}
}
