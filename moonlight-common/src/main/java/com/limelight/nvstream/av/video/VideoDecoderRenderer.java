package com.limelight.nvstream.av.video;

public abstract class VideoDecoderRenderer {
	public abstract int setup(int format, int width, int height, int redrawRate);

	public abstract void start();

	public abstract void stop();

	// This is called once for each frame-start NALU. This means it will be called several times
	// for an IDR frame which contains several parameter sets and the I-frame data.
	public abstract int submitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,
										 int frameNumber, long receiveTimeMs);
	
	public abstract void cleanup();

	public abstract int getCapabilities();
}
