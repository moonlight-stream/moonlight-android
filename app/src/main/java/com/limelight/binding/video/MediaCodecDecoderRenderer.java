package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.Locale;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.VUIParameters;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.os.Build;
import android.view.SurfaceHolder;

public class MediaCodecDecoderRenderer extends VideoDecoderRenderer {

    private static final boolean USE_FRAME_RENDER_TIME = false;

    // Used on versions < 5.0
    private ByteBuffer[] legacyInputBuffers;

    private MediaCodecInfo avcDecoder;
    private MediaCodecInfo hevcDecoder;

    // Used for HEVC only
    private byte[] vpsBuffer;
    private byte[] spsBuffer;

    private MediaCodec videoDecoder;
    private Thread rendererThread;
    private Thread[] spinnerThreads;
    private boolean needsSpsBitstreamFixup, isExynos4;
    private boolean adaptivePlayback, directSubmit;
    private boolean constrainedHighProfile;
    private boolean refFrameInvalidationAvc, refFrameInvalidationHevc;
    private boolean refFrameInvalidationActive;
    private int initialWidth, initialHeight;
    private int videoFormat;
    private Object renderTarget;
    private boolean stopping;

    private boolean needsBaselineSpsHack;
    private SeqParameterSet savedSps;

    private long lastTimestampUs;
    private long decoderTimeMs;
    private long totalTimeMs;
    private int totalFrames;

    private int numSpsIn;
    private int numPpsIn;
    private int numVpsIn;

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

    public void setRenderTarget(Object renderTarget) {
        this.renderTarget = renderTarget;
    }

    public MediaCodecDecoderRenderer(int videoFormat) {
        //dumpDecoders();

        spinnerThreads = new Thread[Runtime.getRuntime().availableProcessors()];

        avcDecoder = findAvcDecoder();
        if (avcDecoder != null) {
            LimeLog.info("Selected AVC decoder: "+avcDecoder.getName());
        }
        else {
            LimeLog.warning("No AVC decoder found");
        }

        hevcDecoder = findHevcDecoder(videoFormat);
        if (hevcDecoder != null) {
            LimeLog.info("Selected HEVC decoder: "+hevcDecoder.getName());
        }
        else {
            LimeLog.info("No HEVC decoder found");
        }

        // Set attributes that are queried in getCapabilities(). This must be done here
        // because getCapabilities() may be called before setup() in current versions of the common
        // library. The limitation of this is that we don't know whether we're using HEVC or AVC, so
        // we just assume AVC. This isn't really a problem because the capabilities are usually
        // shared between AVC and HEVC decoders on the same device.
        if (avcDecoder != null) {
            directSubmit = MediaCodecHelper.decoderCanDirectSubmit(avcDecoder.getName());
            adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(avcDecoder.getName());
            refFrameInvalidationAvc = MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(avcDecoder.getName());
            refFrameInvalidationHevc = MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(avcDecoder.getName());

            if (directSubmit) {
                LimeLog.info("Decoder "+avcDecoder.getName()+" will use direct submit");
            }
            if (refFrameInvalidationAvc) {
                LimeLog.info("Decoder "+avcDecoder.getName()+" will use reference frame invalidation for AVC");
            }
            if (refFrameInvalidationHevc) {
                LimeLog.info("Decoder "+avcDecoder.getName()+" will use reference frame invalidation for HEVC");
            }
        }
    }

    public boolean isHevcSupported() {
        return hevcDecoder != null;
    }

    public boolean isAvcSupported() {
        return avcDecoder != null;
    }

    public int getActiveVideoFormat() {
        return this.videoFormat;
    }

    @Override
    public int setup(int format, int width, int height, int redrawRate) {
        this.initialWidth = width;
        this.initialHeight = height;
        this.videoFormat = format;

        String mimeType;
        String selectedDecoderName;

        if (videoFormat == MoonBridge.VIDEO_FORMAT_H264) {
            mimeType = "video/avc";
            selectedDecoderName = avcDecoder.getName();

            if (avcDecoder == null) {
                LimeLog.severe("No available AVC decoder!");
                return -1;
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

            refFrameInvalidationActive = refFrameInvalidationAvc;
        }
        else if (videoFormat == MoonBridge.VIDEO_FORMAT_H265) {
            mimeType = "video/hevc";
            selectedDecoderName = hevcDecoder.getName();

            if (hevcDecoder == null) {
                LimeLog.severe("No available HEVC decoder!");
                return -2;
            }

            refFrameInvalidationActive = refFrameInvalidationHevc;
        }
        else {
            // Unknown format
            LimeLog.severe("Unknown format");
            return -3;
        }

        // Codecs have been known to throw all sorts of crazy runtime exceptions
        // due to implementation problems
        try {
            videoDecoder = MediaCodec.createByCodecName(selectedDecoderName);
        } catch (Exception e) {
            e.printStackTrace();
            return -4;
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);

        // Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
        // so we don't fill these pre-KitKat
        if (adaptivePlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            videoFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
            videoFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Operate at maximum rate to lower latency as much as possible on
            // some Qualcomm platforms. We could also set KEY_PRIORITY to 0 (realtime)
            // but that will actually result in the decoder crashing if it can't satisfy
            // our (ludicrous) operating rate requirement.
            videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
        }

        try {
            videoDecoder.configure(videoFormat, ((SurfaceHolder)renderTarget).getSurface(), null, 0);
            videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

            if (USE_FRAME_RENDER_TIME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoDecoder.setOnFrameRenderedListener(new MediaCodec.OnFrameRenderedListener() {
                    @Override
                    public void onFrameRendered(MediaCodec mediaCodec, long presentationTimeUs, long renderTimeNanos) {
                        long delta = (renderTimeNanos / 1000000L) - (presentationTimeUs / 1000);
                        if (delta >= 0 && delta < 1000) {
                            if (USE_FRAME_RENDER_TIME) {
                                totalTimeMs += delta;
                            }
                        }
                    }
                }, null);
            }

            LimeLog.info("Using codec "+selectedDecoderName+" for hardware decoding "+mimeType);

            // Start the decoder
            videoDecoder.start();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                legacyInputBuffers = videoDecoder.getInputBuffers();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -5;
        }

        return 0;
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
        // but not if we're stopping
        if (totalFrames > 0 && totalFrames < 20 && !stopping) {
            if (buf != null || codecFlags != 0) {
                throw new RendererException(this, e, buf, codecFlags);
            }
            else {
                throw new RendererException(this, e);
            }
        }
    }

    private void startRendererThread()
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
                                if (!USE_FRAME_RENDER_TIME) {
                                    totalTimeMs += delta;
                                }
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

    private void startSpinnerThreads() {
        LimeLog.info("Using "+spinnerThreads.length+" spinner threads");
        for (int i = 0; i < spinnerThreads.length; i++) {
            spinnerThreads[i] = new Thread() {
                @Override
                public void run() {
                    // This thread exists to keep the CPU at a higher DVFS state on devices
                    // where the governor scales clock speed sporadically, causing dropped frames.
                    while (!isInterrupted()) {
                        try {
                            Thread.sleep(0, 1);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            };
            spinnerThreads[i].setName("Spinner-"+i);
            spinnerThreads[i].setPriority(Thread.MIN_PRIORITY);
            spinnerThreads[i].start();
        }
    }

    private int dequeueInputBuffer() {
        int index = -1;
        long startTime, queueTime;

        startTime = MediaCodecHelper.getMonotonicMillis();

        try {
            while (rendererThread.isAlive() && index < 0 && !stopping) {
                index = videoDecoder.dequeueInputBuffer(10000);
            }
        } catch (Exception e) {
            handleDecoderException(e, null, 0);
            return MediaCodec.INFO_TRY_AGAIN_LATER;
        }

        if (index < 0) {
            return index;
        }

        queueTime = MediaCodecHelper.getMonotonicMillis();

        if (queueTime - startTime >= 20) {
            LimeLog.warning("Queue input buffer ran long: " + (queueTime - startTime) + " ms");
        }

        return index;
    }

    @Override
    public void start() {
        startRendererThread();
        startSpinnerThreads();
    }

    @Override
    public void stop() {
        stopping = true;

        // Halt the rendering thread
        rendererThread.interrupt();

        // Invalidate pending decode buffers
        videoDecoder.flush();

        // Wait for the renderer thread to shut down
        try {
            rendererThread.join();
        } catch (InterruptedException ignored) { }

        // Stop the video decoder
        videoDecoder.stop();

        // Halt the spinner threads
        for (Thread t : spinnerThreads) {
            t.interrupt();
        }
        for (Thread t : spinnerThreads) {
            try {
                t.join();
            } catch (InterruptedException ignored) { }
        }
    }

    @Override
    public void cleanup() {
        videoDecoder.release();
    }

    private boolean queueInputBuffer(int inputBufferIndex, int offset, int length, long timestampUs, int codecFlags) {
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

        if (i == 25 && totalFrames > 0 && totalFrames < 20 && !stopping) {
            throw new RendererException(this, lastException, null, codecFlags);
        }
        else if (i != 25) {
            // Queued input buffer
            return true;
        }
        else {
            // Failed to queue
            return false;
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
    @Override
    public int submitDecodeUnit(byte[] frameData, int frameLength) {
        totalFrames++;

        int inputBufferIndex;
        ByteBuffer buf;

        long timestampUs = System.nanoTime() / 1000;
        if (timestampUs <= lastTimestampUs) {
            // We can't submit multiple buffers with the same timestamp
            // so bump it up by one before queuing
            timestampUs = lastTimestampUs + 1;
        }
        lastTimestampUs = timestampUs;

        int codecFlags = 0;
        boolean needsSpsReplay = false;

        // H264 SPS
        if (frameData[4] == 0x67) {
            numSpsIn++;
            codecFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

            ByteBuffer spsBuf = ByteBuffer.wrap(frameData);

            // Skip to the start of the NALU data
            spsBuf.position(5);

            // The H264Utils.readSPS function safely handles
            // Annex B NALUs (including NALUs with escape sequences)
            SeqParameterSet sps = H264Utils.readSPS(spsBuf);

            // Some decoders rely on H264 level to decide how many buffers are needed
            // Since we only need one frame buffered, we'll set the level as low as we can
            // for known resolution combinations. Reference frame invalidation may need
            // these, so leave them be for those decoders.
            if (!refFrameInvalidationActive) {
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
            }

            // TI OMAP4 requires a reference frame count of 1 to decode successfully. Exynos 4
            // also requires this fixup.
            //
            // I'm doing this fixup for all devices because I haven't seen any devices that
            // this causes issues for. At worst, it seems to do nothing and at best it fixes
            // issues with video lag, hangs, and crashes.
            //
            // It does break reference frame invalidation, so we will not do that for decoders
            // where we've enabled reference frame invalidation.
            if (!refFrameInvalidationActive) {
                LimeLog.info("Patching num_ref_frames in SPS");
                sps.num_ref_frames = 1;
            }

            // GFE 2.5.11 changed the SPS to add additional extensions
            // Some devices don't like these so we remove them here.
            sps.vuiParams.video_signal_type_present_flag = false;
            sps.vuiParams.colour_description_present_flag = false;
            sps.vuiParams.chroma_loc_info_present_flag = false;

            if ((needsSpsBitstreamFixup || isExynos4) && !refFrameInvalidationActive) {
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

            inputBufferIndex = dequeueInputBuffer();
            if (inputBufferIndex < 0) {
                // We're being torn down now
                return MoonBridge.DR_OK;
            }

            buf = getEmptyInputBuffer(inputBufferIndex);

            // Write the annex B header
            buf.put(frameData, 0, 5);

            // The H264Utils.writeSPS function safely handles
            // Annex B NALUs (including NALUs with escape sequences)
            ByteBuffer escapedNalu = H264Utils.writeSPS(sps, frameLength);
            buf.put(escapedNalu);

            if (queueInputBuffer(inputBufferIndex,
                    0, buf.position(),
                    timestampUs, codecFlags)) {
                return MoonBridge.DR_OK;
            }
            else {
                return MoonBridge.DR_NEED_IDR;
            }

            // H264 PPS
        } else if (frameData[4] == 0x68) {
            numPpsIn++;
            codecFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

            inputBufferIndex = dequeueInputBuffer();
            if (inputBufferIndex < 0) {
                // We're being torn down now
                return MoonBridge.DR_OK;
            }

            buf = getEmptyInputBuffer(inputBufferIndex);

            if (needsBaselineSpsHack) {
                LimeLog.info("Saw PPS; disabling SPS hack");
                needsBaselineSpsHack = false;

                // Give the decoder the SPS again with the proper profile now
                needsSpsReplay = true;
            }
        }
        else if (frameData[4] == 0x40) {
            numVpsIn++;

            // Batch this to submit together with SPS and PPS per AOSP docs
            vpsBuffer = new byte[frameLength];
            System.arraycopy(frameData, 0, vpsBuffer, 0, frameLength);
            return MoonBridge.DR_OK;
        }
        else if (frameData[4] == 0x42) {
            numSpsIn++;

            // Batch this to submit together with VPS and PPS per AOSP docs
            spsBuffer = new byte[frameLength];
            System.arraycopy(frameData, 0, spsBuffer, 0, frameLength);
            return MoonBridge.DR_OK;
        }
        else if (frameData[4] == 0x44) {
            numPpsIn++;

            inputBufferIndex = dequeueInputBuffer();
            if (inputBufferIndex < 0) {
                // We're being torn down now
                return MoonBridge.DR_OK;
            }

            buf = getEmptyInputBuffer(inputBufferIndex);

            // When we get the PPS, submit the VPS and SPS together with
            // the PPS, as required by AOSP docs on use of HEVC and MediaCodec.
            if (vpsBuffer != null) {
                buf.put(vpsBuffer);
            }
            if (spsBuffer != null) {
                buf.put(spsBuffer);
            }

            // This is the HEVC CSD blob
            codecFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        }
        else {
            inputBufferIndex = dequeueInputBuffer();
            if (inputBufferIndex < 0) {
                // We're being torn down now
                return MoonBridge.DR_OK;
            }

            buf = getEmptyInputBuffer(inputBufferIndex);
        }

        // Copy data from our buffer list into the input buffer
        buf.put(frameData, 0, frameLength);

        if (!queueInputBuffer(inputBufferIndex,
                0, buf.position(),
                timestampUs, codecFlags)) {
            return MoonBridge.DR_NEED_IDR;
        }

        if (needsSpsReplay) {
            if (!replaySps()) {
                return MoonBridge.DR_NEED_IDR;
            }

            LimeLog.info("SPS replay complete");
        }

        return MoonBridge.DR_OK;
    }

    private boolean replaySps() {
        int inputIndex = dequeueInputBuffer();
        if (inputIndex < 0) {
            return false;
        }

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
        return queueInputBuffer(inputIndex,
                0, inputBuffer.position(),
                System.nanoTime() / 1000,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
    }

    @Override
    public int getCapabilities() {
        int capabilities = 0;

        // We always request 4 slices per frame to speed up decoding on some hardware
        capabilities |= MoonBridge.CAPABILITY_SLICES_PER_FRAME((byte) 4);

        // Enable reference frame invalidation on supported hardware
        if (refFrameInvalidationAvc) {
            capabilities |= MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC;
        }
        if (refFrameInvalidationHevc) {
            capabilities |= MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC;
        }

        // Enable direct submit on supported hardware
        if (directSubmit) {
            capabilities |= MoonBridge.CAPABILITY_DIRECT_SUBMIT;
        }

        return capabilities;
    }

    public int getAverageEndToEndLatency() {
        if (totalFrames == 0) {
            return 0;
        }
        return (int)(totalTimeMs / totalFrames);
    }

    public int getAverageDecoderLatency() {
        if (totalFrames == 0) {
            return 0;
        }
        return (int)(decoderTimeMs / totalFrames);
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
            str += "AVC Decoder: "+((renderer.avcDecoder != null) ? renderer.avcDecoder.getName():"(none)")+"\n";
            str += "HEVC Decoder: "+((renderer.hevcDecoder != null) ? renderer.hevcDecoder.getName():"(none)")+"\n";
            str += "Initial video dimensions: "+renderer.initialWidth+"x"+renderer.initialHeight+"\n";
            str += "In stats: "+renderer.numVpsIn+", "+renderer.numSpsIn+", "+renderer.numPpsIn+"\n";
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
