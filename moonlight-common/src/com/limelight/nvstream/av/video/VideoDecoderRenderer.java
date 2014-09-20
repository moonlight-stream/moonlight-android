package com.limelight.nvstream.av.video;

public interface VideoDecoderRenderer {
	public static final int FLAG_PREFER_QUALITY = 0x1;
	public static final int FLAG_FORCE_HARDWARE_DECODING = 0x2;
	public static final int FLAG_FORCE_SOFTWARE_DECODING = 0x4;
	public static final int FLAG_FILL_SCREEN = 0x8;
	
	// Allows the resolution to dynamically change mid-stream
	public static final int CAPABILITY_ADAPTIVE_RESOLUTION = 0x1;
	
	public int getCapabilities();
	
	public int getAverageEndToEndLatency();
	
	public int getAverageDecoderLatency();

	public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags);
	
	public boolean start(VideoDepacketizer depacketizer);
	
	public void stop();
	
	public void release();
}
