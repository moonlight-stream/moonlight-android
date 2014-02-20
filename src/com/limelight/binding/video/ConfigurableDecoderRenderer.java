package com.limelight.binding.video;

import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class ConfigurableDecoderRenderer implements VideoDecoderRenderer {

	private VideoDecoderRenderer decoderRenderer;
	
	@Override
	public void release() {
		decoderRenderer.release();
	}

	@Override
	public void setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
		if ((drFlags & VideoDecoderRenderer.FLAG_FORCE_HARDWARE_DECODING) != 0 ||
			((drFlags & VideoDecoderRenderer.FLAG_FORCE_SOFTWARE_DECODING) == 0 &&
			  MediaCodecDecoderRenderer.findSafeDecoder() != null)) {
			decoderRenderer = new MediaCodecDecoderRenderer();
		}
		else {
			decoderRenderer = new AndroidCpuDecoderRenderer();
		}
		decoderRenderer.setup(width, height, redrawRate, renderTarget, drFlags);
	}

	@Override
	public void start() {
		decoderRenderer.start();
	}

	@Override
	public void stop() {
		decoderRenderer.stop();
	}

	@Override
	public boolean submitDecodeUnit(DecodeUnit du) {
		return decoderRenderer.submitDecodeUnit(du);
	}

	@Override
	public int getCapabilities() {
		return decoderRenderer.getCapabilities();
	}

}
