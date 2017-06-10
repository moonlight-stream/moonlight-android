package com.limelight.nvstream.av.video;

public abstract class VideoDecoderRenderer {
	public abstract int setup(int format, int width, int height, int redrawRate);

	public abstract void start();

	public abstract void stop();

	public abstract int submitDecodeUnit(byte[] frameData, int frameLength, int frameNumber, long receiveTimeMs);
	
	public abstract void cleanup();

	public abstract int getCapabilities();
}
