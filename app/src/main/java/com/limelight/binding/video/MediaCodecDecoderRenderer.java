package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.VUIParameters;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;
import com.limelight.preferences.PreferenceConfiguration;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.os.Build;
import android.view.SurfaceHolder;

public class MediaCodecDecoderRenderer extends EnhancedDecoderRenderer {

    // Used on versions < 5.0
    private ByteBuffer[] legacyInputBuffers;

    private String avcDecoderName;
    private String hevcDecoderName;

    private MediaCodec videoDecoder;
    private Thread rendererThread;
    private boolean needsSpsBitstreamFixup, isExynos4;
    private VideoDepacketizer depacketizer;
    private boolean adaptivePlayback, directSubmit;
    private boolean constrainedHighProfile;
    private int initialWidth, initialHeight;
    private VideoFormat videoFormat;

    private boolean needsBaselineSpsHack;
    private SeqParameterSet savedSps;

    private long lastTimestampUs;
    private long totalTimeMs;
    private long decoderTimeMs;
    private int totalFrames;

    private int numSpsIn;
    private int numPpsIn;
    private int numVpsIn;
    private int numIframeIn;

    private MediaCodecInfo findAvcDecoder() {
        MediaCodecInfo decoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        if (decoder == null) {
            decoder = MediaCodecHelper.findFirstDecoder("video/avc");
        }
        return decoder;
    }

    private MediaCodecInfo findHevcDecoder(int videoFormat) {
        // Don't return anything if H.265 is forced off
        if (videoFormat == PreferenceConfiguration.FORCE_H265_OFF) {
            return null;
        }

        // We don't try the first HEVC decoder. We'd rather fall back to hardware accelerated AVC instead
        //
        // We need HEVC Main profile, so we could pass that constant to findProbableSafeDecoder, however
        // some decoders (at least Qualcomm's Snapdragon 805) don't properly report support
        // for even required levels of HEVC.
        MediaCodecInfo decoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);
        if (decoderInfo != null) {
            if (!MediaCodecHelper.decoderIsWhitelistedForHevc(decoderInfo.getName())) {
                LimeLog.info("Found HEVC decoder, but it's not whitelisted - "+decoderInfo.getName());

                if (videoFormat == PreferenceConfiguration.FORCE_H265_ON) {
                    LimeLog.info("Forcing H265 enabled despite non-whitelisted decoder");
                }
                else {
                    return null;
                }
            }
        }

        return decoderInfo;
    }

    public MediaCodecDecoderRenderer(int videoFormat) {
        //dumpDecoders();

        MediaCodecInfo avcDecoder = findAvcDecoder();
        if (avcDecoder != null) {
            avcDecoderName = avcDecoder.getName();
            LimeLog.info("Selected AVC decoder: "+avcDecoderName);
        }
        else {
            LimeLog.warning("No AVC decoder found");
        }

        MediaCodecInfo hevcDecoder = findHevcDecoder(videoFormat);
        if (hevcDecoder != null) {
            hevcDecoderName = hevcDecoder.getName();
            LimeLog.info("Selected HEVC decoder: "+hevcDecoderName);
        }
        else {
            LimeLog.info("No HEVC decoder found");
        }
    }

    @Override
    public boolean isHevcSupported() {
        return hevcDecoderName != null;
    }

    @Override
    public boolean isAvcSupported() {
        return avcDecoderName != null;
    }

    @Override
    public boolean setup(VideoDecoderRenderer.VideoFormat format, int width, int height, int redrawRate, Object renderTarget, int drFlags) {
        this.initialWidth = width;
        this.initialHeight = height;
        this.videoFormat = format;

        String mimeType;
        String selectedDecoderName;

        if (videoFormat == VideoFormat.H264) {
            mimeType = "video/avc";
            selectedDecoderName = avcDecoderName;

            if (avcDecoderName == null) {
                LimeLog.severe("No available AVC decoder!");
                return false;
            }

            // These fixups only apply to H264 decoders
            needsSpsBitstreamFixup = MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(selectedDecoderName);
            needsBaselineSpsHack = MediaCodecHelper.decoderNeedsBaselineSpsHack(selectedDecoderName);
            constrainedHighProfile = MediaCodecHelper.decoderNeedsConstrainedHighProfile(selectedDecoderName);
            isExynos4 = MediaCodecHelper.isExynos4Device();
            if (needsSpsBitstreamFixup) {
                LimeLog.info("Decoder "+selectedDecoderName+" needs SPS bitstream restrictions fixup");
            }
            if (needsBaselineSpsHack) {
                LimeLog.info("Decoder "+selectedDecoderName+" needs baseline SPS hack");
            }
            if (constrainedHighProfile) {
                LimeLog.info("Decoder "+selectedDecoderName+" needs constrained high profile");
            }
            if (isExynos4) {
                LimeLog.info("Decoder "+selectedDecoderName+" is on Exynos 4");
            }
        }
        else if (videoFormat == VideoFormat.H265) {
            mimeType = "video/hevc";
            selectedDecoderName = hevcDecoderName;

            if (hevcDecoderName == null) {
                LimeLog.severe("No available HEVC decoder!");
                return false;
            }
        }
        else {
            // Unknown format
            return false;
        }

        // Set decoder-specific attributes
        directSubmit = MediaCodecHelper.decoderCanDirectSubmit(selectedDecoderName);
        adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(selectedDecoderName);

        if (directSubmit) {
            LimeLog.info("Decoder "+selectedDecoderName+" will use direct submit");
        }

        // Codecs have been known to throw all sorts of crazy runtime exceptions
        // due to implementation problems
        try {
            videoDecoder = MediaCodec.createByCodecName(selectedDecoderName);
        } catch (Exception e) {
            return false;
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);

        // Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
        // so we don't fill these pre-KitKat
        if (adaptivePlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            videoFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
            videoFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        }

        videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
        videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

        LimeLog.info("Using codec "+selectedDecoderName+" for hardware decoding "+mimeType);

        return true;
    }

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

        // Only throw if this happens at the beginning of a stream
        if (totalFrames < 60) {
            if (buf != null || codecFlags != 0) {
                throw new RendererException(this, e, buf, codecFlags);
            }
            else {
                throw new RendererException(this, e);
            }
        }
    }

    private void startDirectSubmitRendererThread()
    {
        rendererThread = new Thread() {
            @Override
            public void run() {
                BufferInfo info = new BufferInfo();
                while (!isInterrupted()) {
                    try {
                        // Try to output a frame
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, 50000);
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
                            long delta = MediaCodecHelper.getMonotonicMillis() - (presentationTimeUs / 1000);
                            if (delta >= 0 && delta < 1000) {
                                decoderTimeMs += delta;
                                totalTimeMs += delta;
                            }
                        } else {
                            switch (outIndex) {
                                case MediaCodec.INFO_TRY_AGAIN_LATER:
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

    private int dequeueInputBuffer(boolean wait, boolean infiniteWait) {
        int index;
        long startTime, queueTime;

        startTime = MediaCodecHelper.getMonotonicMillis();

        index = videoDecoder.dequeueInputBuffer(wait ? (infiniteWait ? -1 : 3000) : 0);
        if (index < 0) {
            return index;
        }

        queueTime = MediaCodecHelper.getMonotonicMillis();

        if (queueTime - startTime >= 20) {
            LimeLog.warning("Queue input buffer ran long: " + (queueTime - startTime) + " ms");
        }

        return index;
    }

    private void startLegacyRendererThread()
    {
        rendererThread = new Thread() {
            @Override
            public void run() {
                BufferInfo info = new BufferInfo();
                DecodeUnit du = null;
                int inputIndex = -1;
                long lastDuDequeueTime = 0;
                while (!isInterrupted())
                {
                    // In order to get as much data to the decoder as early as possible,
                    // try to submit up to 5 decode units at once without blocking.
                    if (inputIndex == -1 && du == null) {
                        try {
                            for (int i = 0; i < 5; i++) {
                                inputIndex = dequeueInputBuffer(false, false);
                                du = depacketizer.pollNextDecodeUnit();
                                if (du != null) {
                                    lastDuDequeueTime = MediaCodecHelper.getMonotonicMillis();
                                    notifyDuReceived(du);
                                }

                                // Stop if we can't get a DU or input buffer
                                if (du == null || inputIndex == -1) {
                                    break;
                                }

                                submitDecodeUnit(du, inputIndex);

                                du = null;
                                inputIndex = -1;
                            }
                        } catch (Exception e) {
                            inputIndex = -1;
                            handleDecoderException(e, null, 0);
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
                            inputIndex = dequeueInputBuffer(du != null, false);
                        } catch (Exception e) {
                            inputIndex = -1;
                            handleDecoderException(e, null, 0);
                        }
                    }

                    // Grab a decode unit if we don't have one already
                    if (du == null) {
                        du = depacketizer.pollNextDecodeUnit();
                        if (du != null) {
                            lastDuDequeueTime = MediaCodecHelper.getMonotonicMillis();
                            notifyDuReceived(du);
                        }
                    }

                    // If we've got both a decode unit and an input buffer, we'll
                    // submit now. Otherwise, we wait until we have one.
                    if (du != null && inputIndex >= 0) {
                        long submissionTime = MediaCodecHelper.getMonotonicMillis();
                        if (submissionTime - lastDuDequeueTime >= 20) {
                            LimeLog.warning("Receiving an input buffer took too long: "+(submissionTime - lastDuDequeueTime)+" ms");
                        }

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
                            long delta = MediaCodecHelper.getMonotonicMillis()-(presentationTimeUs/1000);
                            if (delta >= 0 && delta < 1000) {
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
        rendererThread.setPriority(Thread.MAX_PRIORITY);
        rendererThread.start();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean start(VideoDepacketizer depacketizer) {
        this.depacketizer = depacketizer;

        // Start the decoder
        videoDecoder.start();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            legacyInputBuffers = videoDecoder.getInputBuffers();
        }

        if (directSubmit) {
            startDirectSubmitRendererThread();
        }
        else {
            startLegacyRendererThread();
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
                        offset, length,
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

    // Using the new getInputBuffer() API on Lollipop allows
    // the framework to do some performance optimizations for us
    private ByteBuffer getEmptyInputBuffer(int inputBufferIndex) {
        ByteBuffer buf;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            buf = videoDecoder.getInputBuffer(inputBufferIndex);
        }
        else {
            buf = legacyInputBuffers[inputBufferIndex];

            // Clear old input data pre-Lollipop
            buf.clear();
        }

        return buf;
    }

    private void doProfileSpecificSpsPatching(SeqParameterSet sps) {
        // Some devices benefit from setting constraint flags 4 & 5 to make this Constrained
        // High Profile which allows the decoder to assume there will be no B-frames and
        // reduce delay and buffering accordingly. Some devices (Marvell, Exynos 4) don't
        // like it so we only set them on devices that are confirmed to benefit from it.
        if (sps.profile_idc == 100 && constrainedHighProfile) {
            LimeLog.info("Setting constraint set flags for constrained high profile");
            sps.constraint_set_4_flag = true;
            sps.constraint_set_5_flag = true;
        }
        else {
            // Force the constraints unset otherwise (some may be set by default)
            sps.constraint_set_4_flag = false;
            sps.constraint_set_5_flag = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void submitDecodeUnit(DecodeUnit decodeUnit, int inputBufferIndex) {
        long timestampUs = System.nanoTime() / 1000;
        if (timestampUs <= lastTimestampUs) {
            // We can't submit multiple buffers with the same timestamp
            // so bump it up by one before queuing
            timestampUs = lastTimestampUs + 1;
        }
        lastTimestampUs = timestampUs;

        ByteBuffer buf = getEmptyInputBuffer(inputBufferIndex);

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
            ByteBufferDescriptor header = decodeUnit.getBufferHead();

            // H264 SPS
            if (header.data[header.offset+4] == 0x67) {
                numSpsIn++;

                ByteBuffer spsBuf = ByteBuffer.wrap(header.data);

                // Skip to the start of the NALU data
                spsBuf.position(header.offset+5);

                // The H264Utils.readSPS function safely handles
                // Annex B NALUs (including NALUs with escape sequences)
                SeqParameterSet sps = H264Utils.readSPS(spsBuf);

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

                // GFE 2.5.11 changed the SPS to add additional extensions
                // Some devices don't like these so we remove them here.
                sps.vuiParams.video_signal_type_present_flag = false;
                sps.vuiParams.colour_description_present_flag = false;
                sps.vuiParams.chroma_loc_info_present_flag = false;

                if (needsSpsBitstreamFixup || isExynos4) {
                    // The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
                    // or max_dec_frame_buffering which increases decoding latency on Tegra.

                    // GFE 2.5.11 started sending bitstream restrictions
                    if (sps.vuiParams.bitstreamRestriction == null) {
                        LimeLog.info("Adding bitstream restrictions");
                        sps.vuiParams.bitstreamRestriction = new VUIParameters.BitstreamRestriction();
                        sps.vuiParams.bitstreamRestriction.motion_vectors_over_pic_boundaries_flag = true;
                        sps.vuiParams.bitstreamRestriction.log2_max_mv_length_horizontal = 16;
                        sps.vuiParams.bitstreamRestriction.log2_max_mv_length_vertical = 16;
                        sps.vuiParams.bitstreamRestriction.num_reorder_frames = 0;
                    }
                    else {
                        LimeLog.info("Patching bitstream restrictions");
                    }

                    // Some devices throw errors if max_dec_frame_buffering < num_ref_frames
                    sps.vuiParams.bitstreamRestriction.max_dec_frame_buffering = sps.num_ref_frames;

                    // These values are the defaults for the fields, but they are more aggressive
                    // than what GFE sends in 2.5.11, but it doesn't seem to cause picture problems.
                    sps.vuiParams.bitstreamRestriction.max_bytes_per_pic_denom = 2;
                    sps.vuiParams.bitstreamRestriction.max_bits_per_mb_denom = 1;

                    // log2_max_mv_length_horizontal and log2_max_mv_length_vertical are set to more
                    // conservative values by GFE 2.5.11. We'll let those values stand.
                }
                else {
                    // Devices that didn't/couldn't get bitstream restrictions before GFE 2.5.11
                    // will continue to not receive them now
                    sps.vuiParams.bitstreamRestriction = null;
                }

                // If we need to hack this SPS to say we're baseline, do so now
                if (needsBaselineSpsHack) {
                    LimeLog.info("Hacking SPS to baseline");
                    sps.profile_idc = 66;
                    savedSps = sps;
                }

                // Patch the SPS constraint flags
                doProfileSpecificSpsPatching(sps);

                // Write the annex B header
                buf.put(header.data, header.offset, 5);

                // The H264Utils.writeSPS function safely handles
                // Annex B NALUs (including NALUs with escape sequences)
                ByteBuffer escapedNalu = H264Utils.writeSPS(sps, header.length);
                buf.put(escapedNalu);

                queueInputBuffer(inputBufferIndex,
                        0, buf.position(),
                        timestampUs, codecFlags);

                depacketizer.freeDecodeUnit(decodeUnit);
                return;

                // H264 PPS
            } else if (header.data[header.offset+4] == 0x68) {
                numPpsIn++;

                if (needsBaselineSpsHack) {
                    LimeLog.info("Saw PPS; disabling SPS hack");
                    needsBaselineSpsHack = false;

                    // Give the decoder the SPS again with the proper profile now
                    needsSpsReplay = true;
                }
            }
            else if (header.data[header.offset+4] == 0x40) {
                numVpsIn++;
            }
            else if (header.data[header.offset+4] == 0x42) {
                numSpsIn++;
            }
            else if (header.data[header.offset+4] == 0x44) {
                numPpsIn++;
            }
        }

        // Copy data from our buffer list into the input buffer
        for (ByteBufferDescriptor desc = decodeUnit.getBufferHead();
             desc != null; desc = desc.nextDescriptor) {
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
        int inputIndex = dequeueInputBuffer(true, true);
        ByteBuffer inputBuffer = getEmptyInputBuffer(inputIndex);

        // Write the Annex B header
        inputBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01, 0x67});

        // Switch the H264 profile back to high
        savedSps.profile_idc = 100;

        // Patch the SPS constraint flags
        doProfileSpecificSpsPatching(savedSps);

        // The H264Utils.writeSPS function safely handles
        // Annex B NALUs (including NALUs with escape sequences)
        ByteBuffer escapedNalu = H264Utils.writeSPS(savedSps, 128);
        inputBuffer.put(escapedNalu);

        // No need for the SPS anymore
        savedSps = null;

        // Queue the new SPS
        queueInputBuffer(inputIndex,
                0, inputBuffer.position(),
                System.nanoTime() / 1000,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);

        LimeLog.info("SPS replay complete");
    }

    @Override
    public int getCapabilities() {
        int caps = 0;

        caps |= adaptivePlayback ?
                VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION : 0;

        caps |= directSubmit ?
                VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT : 0;

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

    private void notifyDuReceived(DecodeUnit du) {
        long currentTime = MediaCodecHelper.getMonotonicMillis();
        long delta = currentTime-du.getReceiveTimestamp();
        if (delta >= 0 && delta < 1000) {
            totalTimeMs += currentTime-du.getReceiveTimestamp();
            totalFrames++;
        }
    }

    @Override
    public void directSubmitDecodeUnit(DecodeUnit du) {
        int inputIndex;

        notifyDuReceived(du);

        for (;;) {
            try {
                inputIndex = dequeueInputBuffer(true, true);
                break;
            } catch (Exception e) {
                handleDecoderException(e, null, 0);
            }
        }

        if (inputIndex >= 0) {
            submitDecodeUnit(du, inputIndex);
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

            str += "Format: "+renderer.videoFormat+"\n";
            str += "AVC Decoder: "+renderer.avcDecoderName+"\n";
            str += "HEVC Decoder: "+renderer.hevcDecoderName+"\n";
            str += "Initial video dimensions: "+renderer.initialWidth+"x"+renderer.initialHeight+"\n";
            str += "In stats: "+renderer.numVpsIn+", "+renderer.numSpsIn+", "+renderer.numPpsIn+", "+renderer.numIframeIn+"\n";
            str += "Total frames: "+renderer.totalFrames+"\n";
            str += "Average end-to-end client latency: "+getAverageEndToEndLatency()+"ms\n";
            str += "Average hardware decoder latency: "+getAverageDecoderLatency()+"ms\n";

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
