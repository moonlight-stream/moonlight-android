package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.DecodeUnit;

public interface VideoDecoderRenderer {
	public static int FLAG_PREFER_QUALITY = 0x1;
	
	public void setup(int width, int height, Object renderTarget, int drFlags);
	
	public void start();
	
	public void stop();
	
	public void release();
	
	public boolean submitDecodeUnit(DecodeUnit decodeUnit);
}
