package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;

import android.view.Surface;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;

public class CpuDecoderRenderer implements DecoderRenderer {

	private Surface renderTarget;
	private ByteBuffer decoderBuffer;
	private Thread rendererThread;
	
	@Override
	public void setup(int width, int height, Surface renderTarget) {
		this.renderTarget = renderTarget;
		
		int err = AvcDecoder.init(width, height);
		if (err != 0) {
			//throw new IllegalStateException("AVC decoder initialization failure: "+err);
		}
		
		decoderBuffer = ByteBuffer.allocate(128*1024);
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public void release() {
		AvcDecoder.destroy();
	}

	@Override
	public void submitDecodeUnit(AvDecodeUnit decodeUnit) {
		decoderBuffer.clear();
		
		for (AvByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
			decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
		}
		
		//AvcDecoder.decode(decoderBuffer.array(), 0, decodeUnit.getDataLength());
	}
}
