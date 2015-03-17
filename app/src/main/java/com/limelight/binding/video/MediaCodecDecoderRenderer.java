package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.Locale;

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
    private final boolean needsSpsBitstreamFixup, isExynos4;
    private VideoDepacketizer depacketizer;
    private final boolean adaptivePlayback;
    private int initialWidth, initialHeight;
    private final int dequeueOutputBufferTimeout;

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

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public MediaCodecDecoderRenderer() {
        //dumpDecoders();

        MediaCodecInfo decoder = MediaCodecHelper.findProbableSafeDecoder();
        if (decoder == null) {
            decoder = MediaCodecHelper.findFirstDecoder();
        }
        if (decoder == null) {
            // This case is handled later in setup()
            needsSpsBitstreamFixup = false;
            isExynos4 = false;
            adaptivePlayback = false;
            dequeueOutputBufferTimeout = 0;
            return;
        }

        decoderName = decoder.getName();

        // Set decoder-specific attributes
        dequeueOutputBufferTimeout = MediaCodecHelper.getOptimalOutputBufferDequeueTimeout(decoderName, decoder);
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void handleDecoderException(Exception e, ByteBuffer buf, int codecFlags) {
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
            throw new RendererException(this, e, buf, codecFlags);
        }
        else {
            throw new RendererException(this, e);
        }
    }

    private void startRendererThread()
    {
        rendererThread = new Thread() {
            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                BufferInfo info = new BufferInfo();
                while (!isInterrupted()) {
                    try {
                        // Try to output a frame
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, dequeueOutputBufferTimeout);
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
                            long delta = System.currentTimeMillis() - (presentationTimeUs / 1000);
                            if (delta >= 0 && delta < 1000) {
                                decoderTimeMs += delta;
                                totalTimeMs += delta;
                            }
                        } else {
                            switch (outIndex) {
                                case MediaCodec.INFO_TRY_AGAIN_LATER:
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
                        handleDecoderException(e, null, 0);
                    }
                }
            }
        };
        rendererThread.setName("Video - Renderer (MediaCodec)");
        rendererThread.setPriority(Thread.NORM_PRIORITY + 2);
        rendererThread.start();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean start(VideoDepacketizer depacketizer) {
        this.depacketizer = depacketizer;

        // Start the decoder
        videoDecoder.start();

        videoDecoderInputBuffers = videoDecoder.getInputBuffers();
        startRendererThread();

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
                handleDecoderException(e, null, codecFlags);
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
        if (delta >= 0 && delta < 1000) {
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

                // Some decoders rely on H264 level to decide how many buffers are needed
                // Since we only need one frame buffered, we'll set the level as low as we can
                // for known resolution combinations
                if (initialWidth == 1280 && initialHeight == 720) {
                    // Max 5 buffered frames at 1280x720x60
                    LimeLog.info("Patching level_idc to 32");
                    sps.level_idc = 32;
                }
                else if (initialWidth == 1920 && initialHeight == 1080) {
                    // Max 4 buffered frames at 1920x1080x64
                    LimeLog.info("Patching level_idc to 42");
                    sps.level_idc = 42;
                }
                else {
                    // Leave the profile alone (currently 5.0)
                }

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
        int caps = 0;

        caps |= adaptivePlayback ?
                VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION : 0;

        caps |= VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT;

        return caps;
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

    @Override
    public void directSubmitDecodeUnit(DecodeUnit du) {
        int inputIndex;

        for (;;) {
            try {
                inputIndex = videoDecoder.dequeueInputBuffer(-1);
                break;
            } catch (Exception e) {
                handleDecoderException(e, null, 0);
            }
        }

        if (inputIndex >= 0) {
            submitDecodeUnit(du, videoDecoderInputBuffers[inputIndex], inputIndex);
        }
    }

    public class RendererException extends RuntimeException {
        private static final long serialVersionUID = 8985937536997012406L;

        private final Exception originalException;
        private final MediaCodecDecoderRenderer renderer;
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
