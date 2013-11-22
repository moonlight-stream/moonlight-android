package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.AvDecodeUnit;

import android.view.Surface;

public interface DecoderRenderer {
	public void setup(int width, int height, Surface renderTarget);
	
	public void start();
	
	public void stop();
	
	public void release();
	
	public boolean submitDecodeUnit(AvDecodeUnit decodeUnit);
}
