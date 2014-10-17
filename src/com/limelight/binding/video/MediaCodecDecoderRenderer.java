package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.VUIParameters;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;
import android.view.SurfaceHolder;

// Ignore warnings about deprecated MediaCodecList APIs in API level 21
// We don't care about any of the new codec types anyway.
@SuppressWarnings("deprecation")
public class MediaCodecDecoderRenderer implements VideoDecoderRenderer {

	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	private Thread rendererThread;
	private boolean needsSpsBitstreamFixup;
	private VideoDepacketizer depacketizer;
	private boolean adaptivePlayback;
	private int initialWidth, initialHeight;
	
	private long totalTimeMs;
	private long decoderTimeMs;
	private int totalFrames;
	
	private String decoderName;
	private int numSpsIn;
	private int numPpsIn;
	private int numIframeIn;
	
	public static final List<String> preferredDecoders;

	public static final List<String> blacklistedDecoderPrefixes;
	public static final List<String> spsFixupBitstreamFixupDecoderPrefixes;
	public static final List<String> whitelistedAdaptiveResolutionPrefixes;
	
	static {
		preferredDecoders = new LinkedList<String>();
	}
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<String>();
		
		// Software decoders that don't support H264 high profile
		blacklistedDecoderPrefixes.add("omx.google");
		blacklistedDecoderPrefixes.add("AVCDecoder");
	}
	
	static {
		spsFixupBitstreamFixupDecoderPrefixes = new LinkedList<String>();
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.nvidia");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.qcom");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.mtk");
		
		whitelistedAdaptiveResolutionPrefixes = new LinkedList<String>();
		whitelistedAdaptiveResolutionPrefixes.add("omx.nvidia");
		whitelistedAdaptiveResolutionPrefixes.add("omx.qcom");
		whitelistedAdaptiveResolutionPrefixes.add("omx.sec");
		whitelistedAdaptiveResolutionPrefixes.add("omx.TI");
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public MediaCodecDecoderRenderer() {
		//dumpDecoders();
		
		MediaCodecInfo decoder = findProbableSafeDecoder();
		if (decoder == null) {
			decoder = findFirstDecoder();
		}
		if (decoder == null) {
			// This case is handled later in setup()
			return;
		}
		
		decoderName = decoder.getName();
		
		// Possibly enable adaptive playback on KitKat and above
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			try {
				if (decoder.getCapabilitiesForType("video/avc").
						isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback))
				{
					// This will make getCapabilities() return that adaptive playback is supported
					LimeLog.info("Adaptive playback supported (FEATURE_AdaptivePlayback)");
					adaptivePlayback = true;
				}
			} catch (Exception e) {
				// Tolerate buggy codecs
			}
		}
				
		if (!adaptivePlayback) {
			if (isDecoderInList(whitelistedAdaptiveResolutionPrefixes, decoderName)) {
				LimeLog.info("Adaptive playback supported (whitelist)");
				adaptivePlayback = true;
			}
		}
		
		needsSpsBitstreamFixup = isDecoderInList(spsFixupBitstreamFixupDecoderPrefixes, decoderName);
		if (needsSpsBitstreamFixup) {
			LimeLog.info("Decoder "+decoderName+" needs SPS bitstream restrictions fixup");
		}
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
	
	public static String dumpDecoders() throws Exception {
		String str = "";
		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
			
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			str += "Decoder: "+codecInfo.getName()+"\n";
			for (String type : codecInfo.getSupportedTypes()) {
				str += "\t"+type+"\n";
				CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
				
				for (CodecProfileLevel profile : caps.profileLevels) {
					str += "\t\t"+profile.profile+" "+profile.level+"\n";
				}
			}
		}
		return str;
	}
	
	private static MediaCodecInfo findPreferredDecoder() {
		// This is a different algorithm than the other findXXXDecoder functions,
		// because we want to evaluate the decoders in our list's order
		// rather than MediaCodecList's order
		
		for (String preferredDecoder : preferredDecoders) {
			for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
				MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
							
				// Skip encoders
				if (codecInfo.isEncoder()) {
					continue;
				}
				
				// Check for preferred decoders
				if (preferredDecoder.equalsIgnoreCase(codecInfo.getName())) {
					LimeLog.info("Preferred decoder choice is "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	private static MediaCodecInfo findFirstDecoder() {
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
			
			// Find a decoder that supports H.264
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase("video/avc")) {
					LimeLog.info("First decoder choice is "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	public static MediaCodecInfo findProbableSafeDecoder() {
		// First look for a preferred decoder by name
		MediaCodecInfo info = findPreferredDecoder();
		if (info != null) {
			return info;
		}
		
		// Now look for decoders we know are safe
		try {
			// If this function completes, it will determine if the decoder is safe
			return findKnownSafeDecoder();
		} catch (Exception e) {
			// Some buggy devices seem to throw exceptions
			// from getCapabilitiesForType() so we'll just assume
			// they're okay and go with the first one we find
			return findFirstDecoder();
		}
	}

	// We declare this method as explicitly throwing Exception
	// since some bad decoders can throw IllegalArgumentExceptions unexpectedly
	// and we want to be sure all callers are handling this possibility
	private static MediaCodecInfo findKnownSafeDecoder() throws Exception {
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
					
					CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);
					for (CodecProfileLevel profile : caps.profileLevels) {
						if (profile.profile == CodecProfileLevel.AVCProfileHigh) {
							LimeLog.info("Decoder "+codecInfo.getName()+" supports high profile");
							LimeLog.info("Selected decoder: "+codecInfo.getName());
							return codecInfo;
						}
					}
					
					LimeLog.info("Decoder "+codecInfo.getName()+" does NOT support high profile");
				}
			}
		}
		
		return null;
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
		this.initialWidth = width;
		this.initialHeight = height;
		
		if (decoderName == null) {
			LimeLog.severe("No available hardware decoder!");
			return false;
		}
		
		// Codecs have been known to throw all sorts of crazy runtime exceptions
		// due to implementation problems
		try {
			videoDecoder = MediaCodec.createByCodecName(decoderName);
		} catch (Exception e) {
			return false;
		}
		
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		
		// Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
		// so we don't fill these pre-KitKat
		if (adaptivePlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			videoFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
			videoFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
		}
		
		videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		
		LimeLog.info("Using hardware decoding");
		
		return true;
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@Override
			public void run() {
				BufferInfo info = new BufferInfo();
				DecodeUnit du = null;
				int inputIndex = -1;
				while (!isInterrupted())
				{
					// In order to get as much data to the decoder as early as possible,
					// try to submit up to 5 decode units at once without blocking.
					if (inputIndex == -1 && du == null) {
						for (int i = 0; i < 5; i++) {
							inputIndex = videoDecoder.dequeueInputBuffer(0);
							du = depacketizer.pollNextDecodeUnit();

							// Stop if we can't get a DU or input buffer
							if (du == null || inputIndex == -1) {
								break;
							}
							
							submitDecodeUnit(du, inputIndex);
							
							du = null;
							inputIndex = -1;
						}
					}
					
					// Grab an input buffer if we don't have one already.
					// This way we can have one ready hopefully by the time
					// the depacketizer is done with this frame. It's important
					// that this can timeout because it's possible that we could exhaust
					// the decoder's input buffers and deadlocks because aren't pulling
					// frames out of the other end.
					if (inputIndex == -1) {
						try {
							// If we've got a DU waiting to be given to the decoder, 
							// wait a full 3 ms for an input buffer. Otherwise
							// just see if we can get one immediately.
							inputIndex = videoDecoder.dequeueInputBuffer(du != null ? 3000 : 0);
						} catch (Exception e) {
							throw new RendererException(MediaCodecDecoderRenderer.this, e);
						}
					}
					
					// Grab a decode unit if we don't have one already
					if (du == null) {
						du = depacketizer.pollNextDecodeUnit();
					}
					
					// If we've got both a decode unit and an input buffer, we'll
					// submit now. Otherwise, we wait until we have one.
					if (du != null && inputIndex >= 0) {
						submitDecodeUnit(du, inputIndex);
						
						// DU and input buffer have both been consumed
						du = null;
						inputIndex = -1;
					}
					
					// Try to output a frame
					try {
						int outIndex = videoDecoder.dequeueOutputBuffer(info, 0);

					    if (outIndex >= 0) {
					    	long presentationTimeUs = info.presentationTimeUs;
						    int lastIndex = outIndex;
						    
						    // Get the last output buffer in the queue
						    while ((outIndex = videoDecoder.dequeueOutputBuffer(info, 0)) >= 0) {
						    	videoDecoder.releaseOutputBuffer(lastIndex, false);
						    	lastIndex = outIndex;
						    	presentationTimeUs = info.presentationTimeUs;
						    }
						    
						    // Render the last buffer
					    	videoDecoder.releaseOutputBuffer(lastIndex, true);
					    	
						    // Add delta time to the totals (excluding probable outliers)
						    long delta = System.currentTimeMillis()-(presentationTimeUs/1000);
						    if (delta > 5 && delta < 300) {
						    	decoderTimeMs += delta;
							    totalTimeMs += delta;
						    }
					    } else {
						    switch (outIndex) {
						    case MediaCodec.INFO_TRY_AGAIN_LATER:
						    	// Getting an input buffer may already block
						    	// so don't park if we still need to do that
						    	if (inputIndex >= 0) {
							    	LockSupport.parkNanos(1);
						    	}
						    	break;
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
					    }
					} catch (Exception e) {
						throw new RendererException(MediaCodecDecoderRenderer.this, e);
					}
				}
			}
		};
		rendererThread.setName("Video - Renderer (MediaCodec)");
		rendererThread.setPriority(Thread.MAX_PRIORITY);
		rendererThread.start();
	}

	@Override
	public boolean start(VideoDepacketizer depacketizer) {
		this.depacketizer = depacketizer;
		
		// Start the decoder
		videoDecoder.start();
		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
		
		// Start the rendering thread
		startRendererThread();
		return true;
	}

	@Override
	public void stop() {
		// Halt the rendering thread
		rendererThread.interrupt();
		try {
			rendererThread.join();
		} catch (InterruptedException e) { }
		
		// Stop the decoder
		videoDecoder.stop();
	}

	@Override
	public void release() {
		if (videoDecoder != null) {
			videoDecoder.release();
		}
	}

	private void submitDecodeUnit(DecodeUnit decodeUnit, int inputBufferIndex) {
		ByteBuffer buf = videoDecoderInputBuffers[inputBufferIndex];

		long currentTime = System.currentTimeMillis();
		long delta = currentTime-decodeUnit.getReceiveTimestamp();
		if (delta >= 0 && delta < 300) {
		    totalTimeMs += currentTime-decodeUnit.getReceiveTimestamp();
		    totalFrames++;
		}
		
		// Clear old input data
		buf.clear();
		
		int codecFlags = 0;
		int decodeUnitFlags = decodeUnit.getFlags();
		if ((decodeUnitFlags & DecodeUnit.DU_FLAG_CODEC_CONFIG) != 0) {
			codecFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
		}
		if ((decodeUnitFlags & DecodeUnit.DU_FLAG_SYNC_FRAME) != 0) {
			codecFlags |= MediaCodec.BUFFER_FLAG_SYNC_FRAME;
			numIframeIn++;
		}
		
		if ((decodeUnitFlags & DecodeUnit.DU_FLAG_CODEC_CONFIG) != 0) {
			ByteBufferDescriptor header = decodeUnit.getBufferList().get(0);
			if (header.data[header.offset+4] == 0x67) {
				numSpsIn++;
				
				ByteBuffer spsBuf = ByteBuffer.wrap(header.data);
				
				// Skip to the start of the NALU data
				spsBuf.position(header.offset+5);
				
				SeqParameterSet sps = SeqParameterSet.read(spsBuf);
				
				// TI OMAP4 requires a reference frame count of 1 to decode successfully. Exynos 4
				// also requires this fixup.
				//
				// I'm doing this fixup for all devices because I haven't seen any devices that
				// this causes issues for. At worst, it seems to do nothing and at best it fixes
				// issues with video lag, hangs, and crashes.
				LimeLog.info("Patching num_ref_frames in SPS");
				sps.num_ref_frames = 1;
				
				if (needsSpsBitstreamFixup) {
					// The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
					// or max_dec_frame_buffering which increases decoding latency on Tegra.
					LimeLog.info("Adding bitstream restrictions");

					sps.vuiParams.bitstreamRestriction = new VUIParameters.BitstreamRestriction();
					sps.vuiParams.bitstreamRestriction.motion_vectors_over_pic_boundaries_flag = true;
					sps.vuiParams.bitstreamRestriction.max_bytes_per_pic_denom = 2;
					sps.vuiParams.bitstreamRestriction.max_bits_per_mb_denom = 1;
					sps.vuiParams.bitstreamRestriction.log2_max_mv_length_horizontal = 16;
					sps.vuiParams.bitstreamRestriction.log2_max_mv_length_vertical = 16;
					sps.vuiParams.bitstreamRestriction.num_reorder_frames = 0;
					sps.vuiParams.bitstreamRestriction.max_dec_frame_buffering = 1;
				}
				
				// Write the annex B header
				buf.put(header.data, header.offset, 5);
				
				// Write the modified SPS to the input buffer
				sps.write(buf);
				
				try {
					videoDecoder.queueInputBuffer(inputBufferIndex,
							0, buf.position(),
							currentTime * 1000, codecFlags);
				} catch (Exception e) {
					throw new RendererException(this, e, buf, codecFlags);
				}
				
				depacketizer.freeDecodeUnit(decodeUnit);
				return;
			} else if (header.data[header.offset+4] == 0x68) {
				numPpsIn++;
			}
		}

		// Copy data from our buffer list into the input buffer
		for (ByteBufferDescriptor desc : decodeUnit.getBufferList())
		{
			buf.put(desc.data, desc.offset, desc.length);
		}

		try {
			videoDecoder.queueInputBuffer(inputBufferIndex,
					0, decodeUnit.getDataLength(),
					currentTime * 1000, codecFlags);
		} catch (Exception e) {
			throw new RendererException(this, e, buf, codecFlags);
		}
		
		depacketizer.freeDecodeUnit(decodeUnit);
		return;
	}

	@Override
	public int getCapabilities() {
		return adaptivePlayback ?
				VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION : 0;
	}

	@Override
	public int getAverageDecoderLatency() {
		if (totalFrames == 0) {
			return 0;
		}
		return (int)(decoderTimeMs / totalFrames);
	}

	@Override
	public int getAverageEndToEndLatency() {
		if (totalFrames == 0) {
			return 0;
		}
		return (int)(totalTimeMs / totalFrames);
	}
	
	public class RendererException extends RuntimeException {
		private static final long serialVersionUID = 8985937536997012406L;
		
		private Exception originalException;
		private MediaCodecDecoderRenderer renderer;
		private ByteBuffer currentBuffer;
		private int currentCodecFlags;
		
		public RendererException(MediaCodecDecoderRenderer renderer, Exception e) {
			this.renderer = renderer;
			this.originalException = e;
		}
		
		public RendererException(MediaCodecDecoderRenderer renderer, Exception e, ByteBuffer currentBuffer, int currentCodecFlags) {
			this.renderer = renderer;
			this.originalException = e;
			this.currentBuffer = currentBuffer;
			this.currentCodecFlags = currentCodecFlags;
		}
		
		public String toString() {
			String str = "";
			
			str += "Decoder: "+renderer.decoderName+"\n";
			str += "Initial video dimensions: "+renderer.initialWidth+"x"+renderer.initialHeight+"\n";
			str += "In stats: "+renderer.numSpsIn+", "+renderer.numPpsIn+", "+renderer.numIframeIn+"\n";
			str += "Total frames: "+renderer.totalFrames+"\n";
			
			if (currentBuffer != null) {
				str += "Current buffer: ";
				currentBuffer.flip();
				while (currentBuffer.hasRemaining() && currentBuffer.position() < 10) {
					str += String.format((Locale)null, "%02x ", currentBuffer.get());
				}
				str += "\n";
				str += "Buffer codec flags: "+currentCodecFlags+"\n";
			}
			
			str += "Full decoder dump:\n";
			try {
				str += dumpDecoders();
			} catch (Exception e) {
				str += e.getMessage();
			}
			
			str += originalException.toString();
			
			return str;
		}
	}
}
