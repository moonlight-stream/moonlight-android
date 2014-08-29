package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;

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
	private boolean needsSpsBitstreamFixup;
	private boolean needsSpsNumRefFixup;
	private VideoDepacketizer depacketizer;
	
	private long totalTimeMs;
	private long decoderTimeMs;
	private int totalFrames;
	
	private final static byte[] BITSTREAM_RESTRICTIONS = new byte[] {(byte) 0xF1, (byte) 0x83, 0x2A, 0x00};
	
	public static final List<String> blacklistedDecoderPrefixes;
	public static final List<String> spsFixupBitstreamFixupDecoderPrefixes;
	public static final List<String> spsFixupNumRefFixupDecoderPrefixes;
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<String>();
		
		// Nothing here right now :)
	}
	
	static {
		spsFixupBitstreamFixupDecoderPrefixes = new LinkedList<String>();
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.nvidia");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.qcom");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.sec");
		
		spsFixupNumRefFixupDecoderPrefixes = new LinkedList<String>();
		spsFixupNumRefFixupDecoderPrefixes.add("omx.TI");
		spsFixupNumRefFixupDecoderPrefixes.add("omx.qcom");
		spsFixupNumRefFixupDecoderPrefixes.add("omx.sec");
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
	public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
		//dumpDecoders();
		
		// It's nasty to put all this in a try-catch block,
		// but codecs have been known to throw all sorts of crazy runtime exceptions
		// due to implementation problems
		try {
			MediaCodecInfo safeDecoder = findSafeDecoder();
			if (safeDecoder != null) {
				videoDecoder = MediaCodec.createByCodecName(safeDecoder.getName());
				needsSpsBitstreamFixup = isDecoderInList(spsFixupBitstreamFixupDecoderPrefixes, safeDecoder.getName());
				needsSpsNumRefFixup = isDecoderInList(spsFixupNumRefFixupDecoderPrefixes, safeDecoder.getName());
				if (needsSpsBitstreamFixup) {
					LimeLog.info("Decoder "+safeDecoder.getName()+" needs SPS bitstream restrictions fixup");
				}
				if (needsSpsNumRefFixup) {
					LimeLog.info("Decoder "+safeDecoder.getName()+" needs SPS ref num fixup");
				}
			}
			else {
				videoDecoder = MediaCodec.createDecoderByType("video/avc");
				needsSpsBitstreamFixup = false;
				needsSpsNumRefFixup = false;
			}
		} catch (Exception e) {
			return false;
		}
		
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		videoDecoder.start();

		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
		
		LimeLog.info("Using hardware decoding");
		
		return true;
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@Override
			public void run() {
				BufferInfo info = new BufferInfo();
				DecodeUnit du;
				while (!isInterrupted())
				{
					du = depacketizer.pollNextDecodeUnit();
					if (du != null) {
						if (!submitDecodeUnit(du)) {
							// Thread was interrupted
							depacketizer.freeDecodeUnit(du);
							return;
						}
						else {
							depacketizer.freeDecodeUnit(du);
						}
					}
					
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
					    	LockSupport.parkNanos(1);
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
		startRendererThread();
		return true;
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

	private boolean submitDecodeUnit(DecodeUnit decodeUnit) {
		int inputIndex;
		
		do {
			if (Thread.interrupted()) {
				return false;
			}
			
			inputIndex = videoDecoder.dequeueInputBuffer(100000);
		} while (inputIndex < 0);
		
		ByteBuffer buf = videoDecoderInputBuffers[inputIndex];

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
		}
		
		if ((decodeUnitFlags & DecodeUnit.DU_FLAG_CODEC_CONFIG) != 0 &&
			(needsSpsBitstreamFixup || needsSpsNumRefFixup)) {
			ByteBufferDescriptor header = decodeUnit.getBufferList().get(0);
			if (header.data[header.offset+4] == 0x67) {
				byte last = header.data[header.length+header.offset-1];

				// TI OMAP4 requires a reference frame count of 1 to decode successfully
				if (needsSpsNumRefFixup) {
					LimeLog.info("Fixing up num ref frames");
					this.replace(header, 80, 9, new byte[] {0x40}, 3);
				}

				// The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
				// or max_dec_frame_buffering which increases decoding latency on Tegra.
				// We manually modify the SPS here to speed-up decoding if the decoder was flagged as needing it.
				int spsLength;
				if (needsSpsBitstreamFixup) {
					if (!needsSpsNumRefFixup) {
						switch (header.length) {
						case 26:
							LimeLog.info("Adding bitstream restrictions to SPS (26)");
							buf.put(header.data, header.offset, 24);
							buf.put((byte) 0x11);
							buf.put((byte) 0xe3);
							buf.put((byte) 0x06);
							buf.put((byte) 0x50);
							spsLength = header.length + 2;
							break;
						case 27:
							LimeLog.info("Adding bitstream restrictions to SPS (27)");
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
					}
					else {
						// Set bitstream restrictions to only buffer single frame
						// (starts 9 bits before stop bit and 6 bits earlier because of the shortening above)
						this.replace(header, header.length*8+Integer.numberOfLeadingZeros(last & - last)%8-9-6, 2, BITSTREAM_RESTRICTIONS, 3*8);
						buf.put(header.data, header.offset, header.length);
						spsLength = header.length;
					}

				}
				else {
					buf.put(header.data, header.offset, header.length);
					spsLength = header.length;
				}

				videoDecoder.queueInputBuffer(inputIndex,
						0, spsLength,
						currentTime * 1000, codecFlags);
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
				currentTime * 1000, codecFlags);
		
		return true;
	}

	@Override
	public int getCapabilities() {
		return 0;
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
}
