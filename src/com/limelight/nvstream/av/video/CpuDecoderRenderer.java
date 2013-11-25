package com.limelight.nvstream.av.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.view.Surface;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;

public class CpuDecoderRenderer implements DecoderRenderer {

	private Surface renderTarget;
	private ByteBuffer decoderBuffer;
	private Thread rendererThread;
	private int perfLevel;
	
	private static final int LOW_PERF = 1;
	private static final int MED_PERF = 2;
	private static final int HIGH_PERF = 3;
	
	private int findOptimalPerformanceLevel() {
		StringBuilder cpuInfo = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(new File("/proc/cpuinfo")));
			for (;;) {
				int ch = br.read();
				if (ch == -1)
					break;
				cpuInfo.append((char)ch);
			}
			
			// Here we're doing very simple heuristics based on CPU model
			String cpuInfoStr = cpuInfo.toString();

			// We order them from greatest to least for proper detection
			// of devices with multiple sets of cores (like Exynos 5 Octa)
			// TODO Make this better
			if (cpuInfoStr.contains("0xc0f")) {
				// Cortex-A15
				return MED_PERF;
			}
			else if (cpuInfoStr.contains("0xc09")) {
				// Cortex-A9
				return LOW_PERF;
			}
			else if (cpuInfoStr.contains("0xc07")) {
				// Cortex-A7
				return LOW_PERF;
			}
			else {
				// Didn't have anything we're looking for
				return MED_PERF;
			}
		} catch (IOException e) {
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {}
			}
		}
		
		// Couldn't read cpuinfo, so assume medium
		return MED_PERF;
	}
	
	@Override
	public void setup(int width, int height, Surface renderTarget) {
		this.renderTarget = renderTarget;
		this.perfLevel = findOptimalPerformanceLevel();
		
		int flags = 0;
		switch (perfLevel) {
		case LOW_PERF:
			flags = AvcDecoder.DISABLE_LOOP_FILTER |
					AvcDecoder.FAST_BILINEAR_FILTERING |
					AvcDecoder.FAST_DECODE |
					AvcDecoder.SLICE_THREADING;
			break;
		case MED_PERF:
			flags = AvcDecoder.BILINEAR_FILTERING |
					AvcDecoder.FAST_DECODE |
					AvcDecoder.SLICE_THREADING;
			break;
		case HIGH_PERF:
			flags = AvcDecoder.LOW_LATENCY_DECODE;
			break;
		}
		
		int err = AvcDecoder.init(width, height, flags, 2);
		if (err != 0) {
			throw new IllegalStateException("AVC decoder initialization failure: "+err);
		}
		
		decoderBuffer = ByteBuffer.allocate(92*1024);
		
		System.out.println("Using software decoding (performance level: "+perfLevel+")");
	}

	@Override
	public void start() {
		rendererThread = new Thread() {
			@Override
			public void run() {
				int frameRateTarget;
				long nextFrameTime = System.currentTimeMillis();
				
				switch (perfLevel) {
				case HIGH_PERF:
					frameRateTarget = 45;
					break;
				case MED_PERF:
					frameRateTarget = 30;
					break;
				case LOW_PERF:
				default:
					frameRateTarget = 15;
					break;
				}
				
				while (!isInterrupted())
				{
					long diff = nextFrameTime - System.currentTimeMillis();
					
					if (diff > 0) {
						// Sleep until the frame should be rendered
						try {
							Thread.sleep(diff);
						} catch (InterruptedException e) {
							return;
						}
					}
					
					nextFrameTime = computePresentationTimeMs(frameRateTarget);
					AvcDecoder.redraw(renderTarget);
					System.gc();
				}
			}
		};
		rendererThread.setName("Video - Renderer (CPU)");
		rendererThread.start();
	}
	
	private long computePresentationTimeMs(int frameRate) {
		return System.currentTimeMillis() + (1000 / frameRate);
	}

	@Override
	public void stop() {
		rendererThread.interrupt();
		
		try {
			rendererThread.join();
		} catch (InterruptedException e) { }
	}

	@Override
	public void release() {
		AvcDecoder.destroy();
	}

	@Override
	public boolean submitDecodeUnit(AvDecodeUnit decodeUnit) {
		byte[] data;
		
		// Use the reserved decoder buffer if this decode unit will fit
		if (decodeUnit.getDataLength() <= decoderBuffer.limit()) {
			decoderBuffer.clear();
			
			for (AvByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
			}
			
			data = decoderBuffer.array();
		}
		else {
			data = new byte[decodeUnit.getDataLength()];
			
			int offset = 0;
			for (AvByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				System.arraycopy(bbd.data, bbd.offset, data, offset, bbd.length);
				offset += bbd.length;
			}
		}
		
		return (AvcDecoder.decode(data, 0, decodeUnit.getDataLength()) == 0);
	}
}
