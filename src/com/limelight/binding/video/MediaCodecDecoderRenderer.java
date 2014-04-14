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
	
	private final static byte[] BITSTREAM_RESTRICTIONS = new byte[] {(byte) 0xF1, (byte) 0x83, 0x2A, 0x00};
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
	
	@Override
	public void setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {	
		this.redrawRate = redrawRate;
		
		//dumpDecoders();
		
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
				if (header.data[header.offset+4] == 0x67) {
					LimeLog.info("Fixing up SPS");
					
					//Set number of reference frames back to 1 as it's the minimum for bitstream restrictions
					this.replace(header, 80, 9, new byte[] {0x40}, 3);

					//Set bitstream restrictions to only buffer single frame
					byte last = header.data[header.length+header.offset-1];
					this.replace(header, header.length*8+Integer.numberOfLeadingZeros(last & - last)%8-9, 2, BITSTREAM_RESTRICTIONS, 3*8);
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

	/**
	 * Replace bits in array 
	 * @param source array in which bits should be replaced
	 * @param srcOffset offset in bits where replacement should take place
	 * @param srcLength length in bits of data that should be replaced
	 * @param data data array with the the replacement data
	 * @param dataLength length of replacement data in bits
	 */
	public void replace(ByteBufferDescriptor source, int srcOffset, int srcLength, byte[] data, int dataLength) {
		//Add 7 to always round up
		int length = (source.length*8-srcLength+dataLength+7)/8;

		int bitOffset = srcOffset%8;
		int byteOffset = srcOffset/8;

		byte dest[] = null;
		int offset = 0;
		if (length>source.length) {
			dest = new byte[length];

			//Copy the first bytes
			System.arraycopy(source.data, source.offset, dest, offset, byteOffset);
		} else {
			dest = source.data;
			offset = source.offset;
		}

		int byteLength = (bitOffset+dataLength+7)/8;
		int bitTrailing = 8 - (srcOffset+dataLength) % 8;
		for (int i=0;i<byteLength;i++) {
			byte result = 0;
			if (i != 0)
				result = (byte) (data[i-1] << 8-bitOffset);
			else if (bitOffset > 0)
				result = (byte) (source.data[byteOffset+source.offset] & (0xFF << 8-bitOffset));

			if (i == 0 || i != byteLength-1) {
				byte moved = (byte) ((data[i]&0xFF) >>> bitOffset);
				result |= moved;
			}

			if (i == byteLength-1 && bitTrailing > 0) {
				int sourceOffset = srcOffset+srcLength/8;
				int bitMove = (dataLength-srcLength)%8;
				if (bitMove<0) {
					result |= (byte) (source.data[sourceOffset+source.offset] << -bitMove & (0xFF >>> bitTrailing));
					result |= (byte) (source.data[sourceOffset+1+source.offset] << -bitMove & (0xFF >>> 8+bitMove));
				} else {
					byte moved = (byte) ((source.data[sourceOffset+source.offset]&0xFF) >>> bitOffset);
					result |= moved;
				}
			}

			dest[i+byteOffset+offset] = result;
		}

		//Source offset
		byteOffset += srcLength/8;
		bitOffset = (srcOffset+dataLength-srcLength)%8;

		//Offset in destination
		int destOffset = (srcOffset+dataLength)/8;

		for (int i=1;i<source.length-byteOffset;i++) {
			int diff = destOffset >= byteOffset-1?i:source.length-byteOffset-i;

			byte result = 0;
			result = (byte) (source.data[byteOffset+diff-1+source.offset] << 8-bitOffset);
			byte moved = (byte) ((source.data[byteOffset+diff+source.offset]&0xFF) >>> bitOffset);
			result ^= moved;

			dest[diff+destOffset+offset] = result;
		}

		source.data = dest;
		source.offset = offset;
		source.length = length;
	}
}
