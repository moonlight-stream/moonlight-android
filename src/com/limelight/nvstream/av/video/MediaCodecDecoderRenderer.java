package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;
import android.view.Surface;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaCodecDecoderRenderer implements DecoderRenderer {

	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	private Thread rendererThread;
	
	public static final List<String> blacklistedDecoderPrefixes;
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<String>();
		blacklistedDecoderPrefixes.add("omx.google");
		blacklistedDecoderPrefixes.add("omx.nvidia");
		blacklistedDecoderPrefixes.add("omx.TI");
		blacklistedDecoderPrefixes.add("omx.RK");
		blacklistedDecoderPrefixes.add("AVCDecoder");
	}

	public static MediaCodecInfo findSafeDecoder() {

		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
			boolean badCodec = false;
			
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			for (String badPrefix : blacklistedDecoderPrefixes) {
				String name = codecInfo.getName();
				if (name.length() > badPrefix.length()) {
					String prefix = name.substring(0, badPrefix.length());
					if (prefix.equalsIgnoreCase(badPrefix)) {
						badCodec = true;
						break;
					}
				}
			}
			
			if (badCodec) {
				System.out.println("Blacklisted decoder: "+codecInfo.getName());
				continue;
			}
			
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase("video/avc")) {
					System.out.println("Selected decoder: "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	@Override
	public void setup(int width, int height, Surface renderTarget) {
		videoDecoder = MediaCodec.createByCodecName(findSafeDecoder().getName());
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);

		videoDecoder.configure(videoFormat, renderTarget, null, 0);

		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		
		videoDecoder.start();

		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
		
		System.out.println("Using hardware decoding");
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@Override
			public void run() {
				long nextFrameTimeUs = 0;
				while (!isInterrupted())
				{
					BufferInfo info = new BufferInfo();
					int outIndex = videoDecoder.dequeueOutputBuffer(info, 100);
				    switch (outIndex) {
				    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				    	System.out.println("Output buffers changed");
					    break;
				    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				    	System.out.println("Output format changed");
				    	System.out.println("New output Format: " + videoDecoder.getOutputFormat());
				    	break;
				    default:
				      break;
				    }
				    if (outIndex >= 0) {
				    	boolean render = false;
				    	
				    	if (currentTimeUs() >= nextFrameTimeUs) {
				    		render = true;
				    		nextFrameTimeUs = computePresentationTime(60);
				    	}
				    	
				    	videoDecoder.releaseOutputBuffer(outIndex, render);
				    }
				}
			}
		};
		rendererThread.setName("Video - Renderer (MediaCodec)");
		rendererThread.start();
	}
	
	private static long currentTimeUs() {
		return System.nanoTime() / 1000;
	}

	private long computePresentationTime(int frameRate) {
		return currentTimeUs() + (1000000 / frameRate);
	}

	@Override
	public void start() {
		startRendererThread();
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
		if (videoDecoder != null) {
			videoDecoder.release();
		}
	}

	@Override
	public boolean submitDecodeUnit(AvDecodeUnit decodeUnit) {
		if (decodeUnit.getType() != AvDecodeUnit.TYPE_H264) {
			System.err.println("Unknown decode unit type");
			return false;
		}
		
		int inputIndex = videoDecoder.dequeueInputBuffer(-1);
		if (inputIndex >= 0)
		{
			ByteBuffer buf = videoDecoderInputBuffers[inputIndex];
			
			// Clear old input data
			buf.clear();
			
			// Copy data from our buffer list into the input buffer
			for (AvByteBufferDescriptor desc : decodeUnit.getBufferList())
			{
				buf.put(desc.data, desc.offset, desc.length);
			}
			
			videoDecoder.queueInputBuffer(inputIndex,
						0, decodeUnit.getDataLength(),
						0, decodeUnit.getFlags());
		}
		
		return true;
	}
}
