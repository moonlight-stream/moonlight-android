package com.limelight.binding.video;

import java.nio.ByteBuffer;
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
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.os.Build;
import android.view.SurfaceHolder;

@SuppressWarnings("unused")
public class MediaCodecDecoderRenderer extends EnhancedDecoderRenderer {

	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	private Thread rendererThread;
	private boolean needsSpsBitstreamFixup, isExynos4;
	private VideoDepacketizer depacketizer;
	private boolean adaptivePlayback;
	private int initialWidth, initialHeight;

    private boolean needsBaselineSpsHack;
    private SeqParameterSet savedSps;
	
	private long lastTimestampUs;
	private long totalTimeMs;
	private long decoderTimeMs;
	private int totalFrames;
	
	private String decoderName;
	private int numSpsIn;
	private int numPpsIn;
	private int numIframeIn;
	
	private static final boolean ENABLE_ASYNC_RENDERER = false;
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public MediaCodecDecoderRenderer() {
		//dumpDecoders();
		
		MediaCodecInfo decoder = MediaCodecHelper.findProbableSafeDecoder();
		if (decoder == null) {
			decoder = MediaCodecHelper.findFirstDecoder();
		}
		if (decoder == null) {
			// This case is handled later in setup()
			return;
		}
		
		decoderName = decoder.getName();
		
		// Set decoder-specific attributes
		adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(decoderName, decoder);
		needsSpsBitstreamFixup = MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(decoderName, decoder);
        needsBaselineSpsHack = MediaCodecHelper.decoderNeedsBaselineSpsHack(decoderName, decoder);
        isExynos4 = MediaCodecHelper.isExynos4Device();
        if (needsSpsBitstreamFixup) {
			LimeLog.info("Decoder "+decoderName+" needs SPS bitstream restrictions fixup");
		}
        if (needsBaselineSpsHack) {
            LimeLog.info("Decoder "+decoderName+" needs baseline SPS hack");
        }
		if (isExynos4) {
			LimeLog.info("Decoder "+decoderName+" is on Exynos 4");
		}
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
		
		// On Lollipop, we use asynchronous mode to avoid having a busy looping renderer thread
		if (ENABLE_ASYNC_RENDERER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			videoDecoder.setCallback(new MediaCodec.Callback() {
				@Override
				public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
			    	LimeLog.info("Output format changed");
			    	LimeLog.info("New output Format: " + format);
				}
				
				@Override
				public void onOutputBufferAvailable(MediaCodec codec, int index,
						BufferInfo info) {
					try {
						// FIXME: It looks like we can't frameskip here
						codec.releaseOutputBuffer(index, true);
					} catch (Exception e) {
						handleDecoderException(MediaCodecDecoderRenderer.this, e, null, 0);
					}
				}
				
				@Override
				public void onInputBufferAvailable(MediaCodec codec, int index) {
					try {
						submitDecodeUnit(depacketizer.takeNextDecodeUnit(), codec.getInputBuffer(index), index);
					} catch (InterruptedException e) {
						// What do we do here?
						e.printStackTrace();
					} catch (Exception e) {
						handleDecoderException(MediaCodecDecoderRenderer.this, e, null, 0);
					}
				}
				
				@Override
				public void onError(MediaCodec codec, CodecException e) {
					if (e.isTransient()) {
						LimeLog.warning(e.getDiagnosticInfo());
						e.printStackTrace();
					}
					else {
						LimeLog.severe(e.getDiagnosticInfo());
						e.printStackTrace();
					}
				}
			});
		}
		
		videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		
		LimeLog.info("Using hardware decoding");
		
		return true;
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void handleDecoderException(MediaCodecDecoderRenderer dr, Exception e, ByteBuffer buf, int codecFlags) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			if (e instanceof CodecException) {
				CodecException codecExc = (CodecException) e;
				
				if (codecExc.isTransient()) {
					// We'll let transient exceptions go
					LimeLog.warning(codecExc.getDiagnosticInfo());
					return;
				}
				
				LimeLog.severe(codecExc.getDiagnosticInfo());
			}
		}
		
		if (buf != null || codecFlags != 0) {
			throw new RendererException(dr, e, buf, codecFlags);
		}
		else {
			throw new RendererException(dr, e);
		}
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@SuppressWarnings("deprecation")
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
						try {
							for (int i = 0; i < 5; i++) {
								inputIndex = videoDecoder.dequeueInputBuffer(0);
								du = depacketizer.pollNextDecodeUnit();

								// Stop if we can't get a DU or input buffer
								if (du == null || inputIndex == -1) {
									break;
								}
								
								submitDecodeUnit(du, videoDecoderInputBuffers[inputIndex], inputIndex);
								
								du = null;
								inputIndex = -1;
							}
						} catch (Exception e) {
							inputIndex = -1;
							handleDecoderException(MediaCodecDecoderRenderer.this, e, null, 0);
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
							inputIndex = -1;
							handleDecoderException(MediaCodecDecoderRenderer.this, e, null, 0);
						}
					}
					
					// Grab a decode unit if we don't have one already
					if (du == null) {
						du = depacketizer.pollNextDecodeUnit();
					}
					
					// If we've got both a decode unit and an input buffer, we'll
					// submit now. Otherwise, we wait until we have one.
					if (du != null && inputIndex >= 0) {
						submitDecodeUnit(du, videoDecoderInputBuffers[inputIndex], inputIndex);
						
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
						    if (delta >= 0 && delta < 300) {
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
						handleDecoderException(MediaCodecDecoderRenderer.this, e, null, 0);
					}
				}
			}
		};
		rendererThread.setName("Video - Renderer (MediaCodec)");
		rendererThread.setPriority(Thread.MAX_PRIORITY);
		rendererThread.start();
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean start(VideoDepacketizer depacketizer) {
		this.depacketizer = depacketizer;
		
		// Start the decoder
		videoDecoder.start();
		
		// On devices pre-Lollipop, we'll use a rendering thread
		if (!ENABLE_ASYNC_RENDERER || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			videoDecoderInputBuffers = videoDecoder.getInputBuffers();
			startRendererThread();
		}
		return true;
	}

	@Override
	public void stop() {
		if (rendererThread != null) {
			// Halt the rendering thread
			rendererThread.interrupt();
			try {
				rendererThread.join();
			} catch (InterruptedException ignored) { }
		}
		
		// Stop the decoder
		videoDecoder.stop();
	}

	@Override
	public void release() {
		if (videoDecoder != null) {
			videoDecoder.release();
		}
	}
	
	private void queueInputBuffer(int inputBufferIndex, int offset, int length, long timestampUs, int codecFlags) {
		// Try 25 times to submit the input buffer before throwing a real exception
		int i;
		Exception lastException = null;
		
		for (i = 0; i < 25; i++) {
			try {
				videoDecoder.queueInputBuffer(inputBufferIndex,
						0, length,
						timestampUs, codecFlags);
				break;
			} catch (Exception e) {
				handleDecoderException(this, e, null, codecFlags);
				lastException = e;
			}
		}
		
		if (i == 25) {
			throw new RendererException(this, lastException, null, codecFlags);
		}
	}

	@SuppressWarnings("deprecation")
	private void submitDecodeUnit(DecodeUnit decodeUnit, ByteBuffer buf, int inputBufferIndex) {
		long currentTime = System.currentTimeMillis();
		long delta = currentTime-decodeUnit.getReceiveTimestamp();
		if (delta >= 0 && delta < 300) {
		    totalTimeMs += currentTime-decodeUnit.getReceiveTimestamp();
		    totalFrames++;
		}
		
		long timestampUs = currentTime * 1000;
		if (timestampUs <= lastTimestampUs) {
			// We can't submit multiple buffers with the same timestamp
			// so bump it up by one before queuing
			timestampUs = lastTimestampUs + 1;
		}
		lastTimestampUs = timestampUs;
		
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

        boolean needsSpsReplay = false;
		
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
				
				if (needsSpsBitstreamFixup || isExynos4) {
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

                // If we need to hack this SPS to say we're baseline, do so now
                if (needsBaselineSpsHack) {
                    LimeLog.info("Hacking SPS to baseline");
                    sps.profile_idc = 66;
                    savedSps = sps;
                }
				
				// Write the annex B header
				buf.put(header.data, header.offset, 5);
				
				// Write the modified SPS to the input buffer
				sps.write(buf);
				
				queueInputBuffer(inputBufferIndex,
						0, buf.position(),
						timestampUs, codecFlags);
				
				depacketizer.freeDecodeUnit(decodeUnit);
				return;
			} else if (header.data[header.offset+4] == 0x68) {
				numPpsIn++;

                if (needsBaselineSpsHack) {
                    LimeLog.info("Saw PPS; disabling SPS hack");
                    needsBaselineSpsHack = false;

                    // Give the decoder the SPS again with the proper profile now
                    needsSpsReplay = true;
                }
			}
		}

		// Copy data from our buffer list into the input buffer
		for (ByteBufferDescriptor desc : decodeUnit.getBufferList())
		{
			buf.put(desc.data, desc.offset, desc.length);
		}

		queueInputBuffer(inputBufferIndex,
				0, decodeUnit.getDataLength(),
				timestampUs, codecFlags);
		
		depacketizer.freeDecodeUnit(decodeUnit);

        if (needsSpsReplay) {
            replaySps();
        }
	}

    private void replaySps() {
        int inputIndex = videoDecoder.dequeueInputBuffer(-1);
        ByteBuffer inputBuffer = videoDecoderInputBuffers[inputIndex];

        inputBuffer.clear();

        // Write the Annex B header
        inputBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01, 0x67});

        // Switch the H264 profile back to high
        savedSps.profile_idc = 100;

        // Write the SPS data
        savedSps.write(inputBuffer);

        // No need for the SPS anymore
        savedSps = null;

        // Queue the new SPS
        queueInputBuffer(inputIndex,
                0, inputBuffer.position(),
                System.currentTimeMillis() * 1000,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);

        LimeLog.info("SPS replay complete");
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

    @Override
    public String getDecoderName() {
        return decoderName;
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
			
			str += "Is Exynos 4: "+renderer.isExynos4+"\n";
			
			str += "/proc/cpuinfo:\n";
			try {
				str += MediaCodecHelper.readCpuinfo();
			} catch (Exception e) {
				str += e.getMessage();
			}
			
			str += "Full decoder dump:\n";
			try {
				str += MediaCodecHelper.dumpDecoders();
			} catch (Exception e) {
				str += e.getMessage();
			}
			
			str += originalException.toString();
			
			return str;
		}
	}
}
