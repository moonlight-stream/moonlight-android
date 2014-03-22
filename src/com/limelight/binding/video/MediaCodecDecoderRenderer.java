package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.view.SurfaceHolder;

public class MediaCodecDecoderRenderer implements VideoDecoderRenderer {

	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	private Thread rendererThread;
	private int redrawRate;
	private boolean needsSpsFixup;
	private boolean fastInputQueueing;
	
	public static final List<String> blacklistedDecoderPrefixes;
	public static final List<String> spsFixupDecoderPrefixes;
	public static final List<String> fastInputQueueingPrefixes;
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<String>();
		
		// TI's decoder technically supports high profile but doesn't work for some reason
		blacklistedDecoderPrefixes.add("omx.TI");
	}
	
	static {
		spsFixupDecoderPrefixes = new LinkedList<String>();
		spsFixupDecoderPrefixes.add("omx.nvidia");
	}
	
	static {
		fastInputQueueingPrefixes = new LinkedList<String>();
		fastInputQueueingPrefixes.add("omx.nvidia");
	}
		
	private static boolean isDecoderInList(List<String> decoderList, String decoderName) {
		for (String badPrefix : decoderList) {
			if (decoderName.length() >= badPrefix.length()) {
				String prefix = decoderName.substring(0, badPrefix.length());
				if (prefix.equalsIgnoreCase(badPrefix)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static void dumpDecoders() {
		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
			
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			LimeLog.info("Decoder: "+codecInfo.getName());
			for (String type : codecInfo.getSupportedTypes()) {
				LimeLog.info("\t"+type);
				CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
				
				for (CodecProfileLevel profile : caps.profileLevels) {
					LimeLog.info("\t\t"+profile.profile+" "+profile.level);
				}
			}
		}
	}

	public static MediaCodecInfo findSafeDecoder() {
		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
						
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			// Check for explicitly blacklisted decoders
			if (isDecoderInList(blacklistedDecoderPrefixes, codecInfo.getName())) {
				LimeLog.info("Skipping blacklisted decoder: "+codecInfo.getName());
				continue;
			}
			
			// Find a decoder that supports H.264 high profile
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase("video/avc")) {
					LimeLog.info("Examining decoder capabilities of "+codecInfo.getName());
					
					CodecCapabilities caps = codecInfo.getCapabilitiesForType("video/avc");
					for (CodecProfileLevel profile : caps.profileLevels) {
						if (profile.profile == CodecProfileLevel.AVCProfileHigh) {
							LimeLog.info("Decoder "+codecInfo.getName()+" supports high profile");
							LimeLog.info("Selected decoder: "+codecInfo.getName());
							return codecInfo;
						}
					}
				}
			}
		}
		
		return null;
	}
	
	@Override
	public void setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {	
		this.redrawRate = redrawRate;
		
		dumpDecoders();
		
		MediaCodecInfo safeDecoder = findSafeDecoder();
		if (safeDecoder != null) {
			videoDecoder = MediaCodec.createByCodecName(safeDecoder.getName());
			needsSpsFixup = isDecoderInList(spsFixupDecoderPrefixes, safeDecoder.getName());
			if (needsSpsFixup) {
				LimeLog.info("Decoder "+safeDecoder.getName()+" needs SPS fixup");
			}
			fastInputQueueing = isDecoderInList(fastInputQueueingPrefixes, safeDecoder.getName());
			if (fastInputQueueing) {
				LimeLog.info("Decoder "+safeDecoder.getName()+" supports fast input queueing");
			}
		}
		else {
			videoDecoder = MediaCodec.createDecoderByType("video/avc");
			needsSpsFixup = false;
			fastInputQueueing = false;
		}
		
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		videoDecoder.start();

		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
		
		LimeLog.info("Using hardware decoding");
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@Override
			public void run() {
				long nextFrameTimeUs = 0;
				BufferInfo info = new BufferInfo();
				while (!isInterrupted())
				{
					// Block for a maximum of 100 ms
					int outIndex = videoDecoder.dequeueOutputBuffer(info, 100000);
				    switch (outIndex) {
				    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				    	LimeLog.info("Output buffers changed");
					    break;
				    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				    	LimeLog.info("Output format changed");
				    	LimeLog.info("New output Format: " + videoDecoder.getOutputFormat());
				    	break;
				    default:
				      break;
				    }
				    
				    if (outIndex >= 0) {
					    int lastIndex = outIndex;
				    	boolean render = false;
					    
				    	if (currentTimeUs() >= nextFrameTimeUs) {
				    		render = true;
				    		nextFrameTimeUs = computePresentationTime(redrawRate);
				    	}
					    
					    // Get the last output buffer in the queue
					    while ((outIndex = videoDecoder.dequeueOutputBuffer(info, 0)) >= 0) {
					    	videoDecoder.releaseOutputBuffer(lastIndex, false);
					    	lastIndex = outIndex;
					    }
				    	
					    // Render that buffer if it's time for the next frame
				    	videoDecoder.releaseOutputBuffer(lastIndex, render);
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
	public boolean submitDecodeUnit(DecodeUnit decodeUnit) {
		if (decodeUnit.getType() != DecodeUnit.TYPE_H264) {
			System.err.println("Unknown decode unit type");
			return false;
		}
		
		int mcFlags = 0;
		
		if ((decodeUnit.getFlags() & DecodeUnit.DU_FLAG_CODEC_CONFIG) != 0) {
			LimeLog.info("Codec config");
			mcFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
		}
		if ((decodeUnit.getFlags() & DecodeUnit.DU_FLAG_SYNC_FRAME) != 0) {
			LimeLog.info("Sync frame");
			mcFlags |= MediaCodec.BUFFER_FLAG_SYNC_FRAME;
		}
		
		int inputIndex = videoDecoder.dequeueInputBuffer(-1);
		if (inputIndex >= 0)
		{
			ByteBuffer buf = videoDecoderInputBuffers[inputIndex];

			// Clear old input data
			buf.clear();

			// The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
			// or max_dec_frame_buffering which increases decoding latency on Tegra.
			// We manually modify the SPS here to speed-up decoding if the decoder was flagged as needing it.
			if (needsSpsFixup) {
				ByteBufferDescriptor header = decodeUnit.getBufferList().get(0);
				// Check for SPS NALU type
				if (header.data[header.offset+4] == 0x67) {
					int spsLength;

					switch (header.length) {
					case 26:
						LimeLog.info("Modifying SPS (26)");
						buf.put(header.data, header.offset, 24);
						buf.put((byte) 0x11);
						buf.put((byte) 0xe3);
						buf.put((byte) 0x06);
						buf.put((byte) 0x50);
						spsLength = header.length + 2;
						break;
					case 27:
						LimeLog.info("Modifying SPS (27)");
						buf.put(header.data, header.offset, 25);
						buf.put((byte) 0x04);
						buf.put((byte) 0x78);
						buf.put((byte) 0xc1);
						buf.put((byte) 0x94);
						spsLength = header.length + 2;
						break;
					default:
						LimeLog.warning("Unknown SPS of length "+header.length);
						buf.put(header.data, header.offset, header.length);
						spsLength = header.length;
						break;
					}

					videoDecoder.queueInputBuffer(inputIndex,
							0, spsLength,
							0, mcFlags);
					return true;
				}
			}

			// Copy data from our buffer list into the input buffer
			for (ByteBufferDescriptor desc : decodeUnit.getBufferList())
			{
				buf.put(desc.data, desc.offset, desc.length);
			}

			videoDecoder.queueInputBuffer(inputIndex,
					0, decodeUnit.getDataLength(),
					0, mcFlags);
		}
		
		return true;
	}

	@Override
	public int getCapabilities() {
		return fastInputQueueing ? VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT : 0;
	}
}
