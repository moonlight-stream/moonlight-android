package com.limelight.binding.video;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;

public class ConfigurableDecoderRenderer implements VideoDecoderRenderer {

	private VideoDecoderRenderer decoderRenderer;
	
	@Override
	public void release() {
		if (decoderRenderer != null) {
			decoderRenderer.release();
		}
	}

	@Override
	public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
		if (decoderRenderer == null) {
			throw new IllegalStateException("ConfigurableDecoderRenderer not initialized");
		}
		return decoderRenderer.setup(width, height, redrawRate, renderTarget, drFlags);
	}
	
	public void initializeWithFlags(int drFlags) {
		if ((drFlags & VideoDecoderRenderer.FLAG_FORCE_HARDWARE_DECODING) != 0 ||
				((drFlags & VideoDecoderRenderer.FLAG_FORCE_SOFTWARE_DECODING) == 0 &&
				MediaCodecHelper.findProbableSafeDecoder() != null)) {
			decoderRenderer = new MediaCodecDecoderRenderer();
		}
		else {
			decoderRenderer = new AndroidCpuDecoderRenderer();
		}
	}
	
	public boolean isHardwareAccelerated() {
		if (decoderRenderer == null) {
			throw new IllegalStateException("ConfigurableDecoderRenderer not initialized");
		}
		return (decoderRenderer instanceof MediaCodecDecoderRenderer);
	}

	@Override
	public boolean start(VideoDepacketizer depacketizer) {
		return decoderRenderer.start(depacketizer);
	}

	@Override
	public void stop() {
		decoderRenderer.stop();
	}

	@Override
	public int getCapabilities() {
		return decoderRenderer.getCapabilities();
	}

	@Override
	public int getAverageDecoderLatency() {
		if (decoderRenderer != null) {
			return decoderRenderer.getAverageDecoderLatency();
		}
		else {
			return 0;
		}
	}

	@Override
	public int getAverageEndToEndLatency() {
		if (decoderRenderer != null) {
			return decoderRenderer.getAverageEndToEndLatency();
		}
		else {
			return 0;
		}
	}
}
