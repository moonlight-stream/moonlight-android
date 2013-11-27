package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.AvDecodeUnit;

import android.view.SurfaceHolder;

public interface DecoderRenderer {
	public static int FLAG_PREFER_QUALITY = 0x1;
	
	public void setup(int width, int height, SurfaceHolder renderTarget, int drFlags);
	
	public void start();
	
	public void stop();
	
	public void release();
	
	public boolean submitDecodeUnit(AvDecodeUnit decodeUnit);
}
