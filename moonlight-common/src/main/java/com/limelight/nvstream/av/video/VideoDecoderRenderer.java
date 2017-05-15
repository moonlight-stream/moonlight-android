package com.limelight.nvstream.av.video;

public abstract class VideoDecoderRenderer {
	public abstract boolean setup(int format, int width, int height, int redrawRate);

	public abstract int submitDecodeUnit(byte[] frameData);
	
	public abstract void cleanup();
}
