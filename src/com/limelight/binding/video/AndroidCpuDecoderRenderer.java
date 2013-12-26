package com.limelight.binding.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.view.SurfaceHolder;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

public class AndroidCpuDecoderRenderer implements VideoDecoderRenderer {

	private Thread rendererThread;
	private int targetFps;
	
	private static final int DECODER_BUFFER_SIZE = 92*1024;
	private ByteBuffer decoderBuffer;
	
	// Only sleep if the difference is above this value
	private static final int WAIT_CEILING_MS = 8;
	
	private static final int LOW_PERF = 1;
	private static final int MED_PERF = 2;
	private static final int HIGH_PERF = 3;
	
	private int cpuCount = Runtime.getRuntime().availableProcessors();
	
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
	public void setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
		this.targetFps = redrawRate;
		
		int perfLevel = findOptimalPerformanceLevel();
		int threadCount;
		
		int avcFlags = 0;
		switch (perfLevel) {
		case HIGH_PERF:
			// Single threaded low latency decode is ideal but hard to acheive
			avcFlags = AvcDecoder.LOW_LATENCY_DECODE;
			threadCount = 1;
			break;

		case LOW_PERF:
			// Disable the loop filter for performance reasons
			avcFlags = AvcDecoder.DISABLE_LOOP_FILTER |
				AvcDecoder.FAST_BILINEAR_FILTERING |
				AvcDecoder.FAST_DECODE;
			
			// Use plenty of threads to try to utilize the CPU as best we can
			threadCount = cpuCount - 1;
			break;

		default:
		case MED_PERF:
			avcFlags = AvcDecoder.BILINEAR_FILTERING |
				AvcDecoder.FAST_DECODE;
			
			// Only use 2 threads to minimize frame processing latency
			threadCount = 2;
			break;
		}
		
		// If the user wants quality, we'll remove the low IQ flags
		if ((drFlags & VideoDecoderRenderer.FLAG_PREFER_QUALITY) != 0) {
			// Make sure the loop filter is enabled
			avcFlags &= ~AvcDecoder.DISABLE_LOOP_FILTER;
			
			// Disable the non-compliant speed optimizations
			avcFlags &= ~AvcDecoder.FAST_DECODE;
			
			System.out.println("Using high quality decoding");
		}
		
		int err = AvcDecoder.init(width, height, avcFlags, threadCount);
		if (err != 0) {
			throw new IllegalStateException("AVC decoder initialization failure: "+err);
		}
		
		AvcDecoder.setRenderTarget(((SurfaceHolder)renderTarget).getSurface());
		
		decoderBuffer = ByteBuffer.allocate(DECODER_BUFFER_SIZE + AvcDecoder.getInputPaddingSize());
		
		System.out.println("Using software decoding (performance level: "+perfLevel+")");
	}

	@Override
	public void start() {
		rendererThread = new Thread() {
			@Override
			public void run() {
				long nextFrameTime = System.currentTimeMillis();
				
				while (!isInterrupted())
				{
					long diff = nextFrameTime - System.currentTimeMillis();

					if (diff > WAIT_CEILING_MS) {
						try {
							Thread.sleep(diff);
						} catch (InterruptedException e) {
							return;
						}
					}

					nextFrameTime = computePresentationTimeMs(targetFps);
					AvcDecoder.redraw();
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
	public boolean submitDecodeUnit(DecodeUnit decodeUnit) {
		byte[] data;
		
		// Use the reserved decoder buffer if this decode unit will fit
		if (decodeUnit.getDataLength() <= DECODER_BUFFER_SIZE) {
			decoderBuffer.clear();
			
			for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
			}
			
			data = decoderBuffer.array();
		}
		else {
			data = new byte[decodeUnit.getDataLength()+AvcDecoder.getInputPaddingSize()];
			
			int offset = 0;
			for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
				System.arraycopy(bbd.data, bbd.offset, data, offset, bbd.length);
				offset += bbd.length;
			}
		}
		
		return (AvcDecoder.decode(data, 0, decodeUnit.getDataLength()) == 0);
	}
}
