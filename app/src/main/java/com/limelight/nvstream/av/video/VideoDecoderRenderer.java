package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.DecodeUnit;

public abstract class VideoDecoderRenderer {
	public static final int FLAG_PREFER_QUALITY = 0x1;
	public static final int FLAG_FORCE_HARDWARE_DECODING = 0x2;
	public static final int FLAG_FORCE_SOFTWARE_DECODING = 0x4;
	public static final int FLAG_FILL_SCREEN = 0x8;
	
	// Allows the resolution to dynamically change mid-stream
	public static final int CAPABILITY_ADAPTIVE_RESOLUTION = 0x1;
	
	// Allows decode units to be submitted directly from the receive thread
	public static final int CAPABILITY_DIRECT_SUBMIT = 0x2;
	
	// !!! EXPERIMENTAL !!!
	// Allows reference frame invalidation to be use to recover from packet loss
	public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION = 0x4;
	
	public int getCapabilities() {
		return 0;
	}
	
	public int getAverageEndToEndLatency() {
		return 0;
	}
	
	public int getAverageDecoderLatency() {
		return 0;
	}
	
	public void directSubmitDecodeUnit(DecodeUnit du) {
		throw new UnsupportedOperationException("CAPABILITY_DIRECT_SUBMIT requires overriding directSubmitDecodeUnit()");
	}

	public abstract boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags);
	
	public abstract boolean start(VideoDepacketizer depacketizer);
	
	public abstract void stop();
	
	public abstract void release();
}
