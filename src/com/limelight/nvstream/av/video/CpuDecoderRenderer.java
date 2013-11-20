package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Surface;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;

public class CpuDecoderRenderer implements DecoderRenderer {

	private Surface renderTarget;
	private ByteBuffer decoderBuffer;
	private Thread rendererThread;
	private int width, height;
	
	@Override
	public void setup(int width, int height, Surface renderTarget) {
		this.renderTarget = renderTarget;
		this.width = width;
		this.height = height;
		
		int err = AvcDecoder.init(width, height);
		if (err != 0) {
			throw new IllegalStateException("AVC decoder initialization failure: "+err);
		}
		
		decoderBuffer = ByteBuffer.allocate(128*1024);
	}
	
	private int getPerFrameDelayMs(int frameRate) {
		return 1000 / frameRate;
	}

	@Override
	public void start() {
		rendererThread = new Thread() {
			@Override
			public void run() {
				int[] frameBuffer = new int[AvcDecoder.getFrameSize()];
				
				while (!isInterrupted())
				{
					try {
						// CPU decoding frame rate target is 30 fps
						Thread.sleep(getPerFrameDelayMs(30));
					} catch (InterruptedException e) {
						return;
					}
					
					if (!AvcDecoder.getCurrentFrame(frameBuffer, frameBuffer.length))
						continue;
					
					// Draw the new bitmap to the canvas
					Canvas c = renderTarget.lockCanvas(null);
					c.drawBitmap(frameBuffer, 0, width, 0, 0, width, height, false, null);
					renderTarget.unlockCanvasAndPost(c);
				}
			}
		};
		rendererThread.start();
	}

	@Override
	public void stop() {
		rendererThread.interrupt();
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
		
		AvcDecoder.decode(decoderBuffer.array(), 0, decodeUnit.getDataLength());
	}
}
