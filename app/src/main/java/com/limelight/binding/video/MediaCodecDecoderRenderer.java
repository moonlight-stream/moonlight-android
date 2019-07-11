package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.Locale;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.VUIParameters;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.os.Build;
import android.util.Range;
import android.view.SurfaceHolder;

public class MediaCodecDecoderRenderer extends VideoDecoderRenderer {

    private static final boolean USE_FRAME_RENDER_TIME = false;
    private static final boolean FRAME_RENDER_TIME_ONLY = USE_FRAME_RENDER_TIME && false;

    // Used on versions < 5.0
    private ByteBuffer[] legacyInputBuffers;

    private MediaCodecInfo avcDecoder;
    private MediaCodecInfo hevcDecoder;

    private byte[] vpsBuffer;
    private byte[] spsBuffer;
    private byte[] ppsBuffer;
    private boolean submittedCsd;
    private boolean submitCsdNextCall;

    private Context context;
    private MediaCodec videoDecoder;
    private Thread rendererThread;
    private boolean needsSpsBitstreamFixup, isExynos4;
    private boolean adaptivePlayback, directSubmit;
    private boolean constrainedHighProfile;
    private boolean refFrameInvalidationAvc, refFrameInvalidationHevc;
    private boolean refFrameInvalidationActive;
    private int initialWidth, initialHeight;
    private int videoFormat;
    private SurfaceHolder renderTarget;
    private volatile boolean stopping;
    private CrashListener crashListener;
    private boolean reportedCrash;
    private int consecutiveCrashCount;
    private String glRenderer;
    private boolean foreground = true;
    private boolean legacyFrameDropRendering = false;
    private PerfOverlayListener perfListener;

    private boolean needsBaselineSpsHack;
    private SeqParameterSet savedSps;

    private RendererException initialException;
    private long initialExceptionTimestamp;
    private static final int EXCEPTION_REPORT_DELAY_MS = 3000;

    private VideoStats activeWindowVideoStats;
    private VideoStats lastWindowVideoStats;
    private VideoStats globalVideoStats;

    private long lastTimestampUs;
    private int lastFrameNumber;
    private int refreshRate;
    private PreferenceConfiguration prefs;

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

    private MediaCodecInfo findHevcDecoder(PreferenceConfiguration prefs, boolean meteredNetwork, boolean requestedHdr) {
        // Don't return anything if H.265 is forced off
        if (prefs.videoFormat == PreferenceConfiguration.FORCE_H265_OFF) {
            return null;
        }

        // We don't try the first HEVC decoder. We'd rather fall back to hardware accelerated AVC instead
        //
        // We need HEVC Main profile, so we could pass that constant to findProbableSafeDecoder, however
        // some decoders (at least Qualcomm's Snapdragon 805) don't properly report support
        // for even required levels of HEVC.
        MediaCodecInfo decoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);
        if (decoderInfo != null) {
            if (!MediaCodecHelper.decoderIsWhitelistedForHevc(decoderInfo.getName(), meteredNetwork)) {
                LimeLog.info("Found HEVC decoder, but it's not whitelisted - "+decoderInfo.getName());

                // HDR implies HEVC forced on, since HEVCMain10HDR10 is required for HDR
                if (prefs.videoFormat == PreferenceConfiguration.FORCE_H265_ON || requestedHdr) {
                    LimeLog.info("Forcing H265 enabled despite non-whitelisted decoder");
                }
                else {
                    return null;
                }
            }
        }

        return decoderInfo;
    }

    public void setRenderTarget(SurfaceHolder renderTarget) {
        this.renderTarget = renderTarget;
    }

    public MediaCodecDecoderRenderer(Context context, PreferenceConfiguration prefs,
                                     CrashListener crashListener, int consecutiveCrashCount,
                                     boolean meteredData, boolean requestedHdr,
                                     String glRenderer, PerfOverlayListener perfListener) {
        //dumpDecoders();

        this.context = context;
        this.prefs = prefs;
        this.crashListener = crashListener;
        this.consecutiveCrashCount = consecutiveCrashCount;
        this.glRenderer = glRenderer;
        this.perfListener = perfListener;

        this.activeWindowVideoStats = new VideoStats();
        this.lastWindowVideoStats = new VideoStats();
        this.globalVideoStats = new VideoStats();

        avcDecoder = findAvcDecoder();
        if (avcDecoder != null) {
            LimeLog.info("Selected AVC decoder: "+avcDecoder.getName());
        }
        else {
            LimeLog.warning("No AVC decoder found");
        }

        hevcDecoder = findHevcDecoder(prefs, meteredData, requestedHdr);
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
            adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(avcDecoder);
            refFrameInvalidationAvc = MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(avcDecoder.getName(), prefs.height);
            refFrameInvalidationHevc = MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(avcDecoder.getName());

            if (consecutiveCrashCount % 2 == 1) {
                refFrameInvalidationAvc = refFrameInvalidationHevc = false;
                LimeLog.warning("Disabling RFI due to previous crash");
            }

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

    public boolean is49FpsBlacklisted() {
        return avcDecoder != null && MediaCodecHelper.decoderBlacklistedFor49Fps(avcDecoder.getName());
    }

    public void enableLegacyFrameDropRendering() {
        LimeLog.info("Legacy frame drop rendering enabled");
        legacyFrameDropRendering = true;
    }

    public boolean isHevcMain10Hdr10Supported() {
        if (hevcDecoder == null) {
            return false;
        }

        for (MediaCodecInfo.CodecProfileLevel profileLevel : hevcDecoder.getCapabilitiesForType("video/hevc").profileLevels) {
            if (profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10) {
                LimeLog.info("HEVC decoder "+hevcDecoder.getName()+" supports HEVC Main10 HDR10");
                return true;
            }
        }

        return false;
    }

    public void notifyVideoForeground() {
        foreground = true;
    }

    public void notifyVideoBackground() {
        foreground = false;
    }

    public int getActiveVideoFormat() {
        return this.videoFormat;
    }

    @Override
    public int setup(int format, int width, int height, int redrawRate) {
        this.initialWidth = width;
        this.initialHeight = height;
        this.videoFormat = format;
        this.refreshRate = redrawRate;

        String mimeType;
        String selectedDecoderName;

        if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
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
        else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
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
            videoDecoder.configure(videoFormat, renderTarget.getSurface(), null, 0);
            videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

            if (USE_FRAME_RENDER_TIME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoDecoder.setOnFrameRenderedListener(new MediaCodec.OnFrameRenderedListener() {
                    @Override
                    public void onFrameRendered(MediaCodec mediaCodec, long presentationTimeUs, long renderTimeNanos) {
                        long delta = (renderTimeNanos / 1000000L) - (presentationTimeUs / 1000);
                        if (delta >= 0 && delta < 1000) {
                            if (USE_FRAME_RENDER_TIME) {
                                activeWindowVideoStats.totalTimeMs += delta;
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

    private void handleDecoderException(Exception e, ByteBuffer buf, int codecFlags, boolean throwOnTransient) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (e instanceof CodecException) {
                CodecException codecExc = (CodecException) e;

                if (codecExc.isTransient() && !throwOnTransient) {
                    // We'll let transient exceptions go
                    LimeLog.warning(codecExc.getDiagnosticInfo());
                    return;
                }

                LimeLog.severe(codecExc.getDiagnosticInfo());
            }
        }

        // Only throw if we're not stopping
        if (!stopping) {
            //
            // There seems to be a race condition with decoder/surface teardown causing some
            // decoders to to throw IllegalStateExceptions even before 'stopping' is set.
            // To workaround this while allowing real exceptions to propagate, we will eat the
            // first exception. If we are still receiving exceptions 3 seconds later, we will
            // throw the original exception again.
            //
            if (initialException != null) {
                // This isn't the first time we've had an exception processing video
                if (System.currentTimeMillis() - initialExceptionTimestamp >= EXCEPTION_REPORT_DELAY_MS) {
                    // It's been over 3 seconds and we're still getting exceptions. Throw the original now.
                    if (!reportedCrash) {
                        reportedCrash = true;
                        crashListener.notifyCrash(initialException);
                    }
                    throw initialException;
                }
            }
            else {
                // This is the first exception we've hit
                if (buf != null || codecFlags != 0) {
                    initialException = new RendererException(this, e, buf, codecFlags);
                }
                else {
                    initialException = new RendererException(this, e);
                }
                initialExceptionTimestamp = System.currentTimeMillis();
            }
        }
    }

    private void startRendererThread()
    {
        rendererThread = new Thread() {
            @Override
            public void run() {
                BufferInfo info = new BufferInfo();
                while (!stopping) {
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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                if (legacyFrameDropRendering) {
                                    // Use a PTS that will cause this frame to be dropped if another comes in within
                                    // the same V-sync period
                                    videoDecoder.releaseOutputBuffer(lastIndex, System.nanoTime());
                                }
                                else {
                                    // Use a PTS that will cause this frame to never be dropped if frame dropping
                                    // is disabled
                                    videoDecoder.releaseOutputBuffer(lastIndex, 0);
                                }
                            }
                            else {
                                videoDecoder.releaseOutputBuffer(lastIndex, true);
                            }

                            activeWindowVideoStats.totalFramesRendered++;

                            // Add delta time to the totals (excluding probable outliers)
                            long delta = MediaCodecHelper.getMonotonicMillis() - (presentationTimeUs / 1000);
                            if (delta >= 0 && delta < 1000) {
                                activeWindowVideoStats.decoderTimeMs += delta;
                                if (!USE_FRAME_RENDER_TIME) {
                                    activeWindowVideoStats.totalTimeMs += delta;
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
                        handleDecoderException(e, null, 0, false);
                    }
                }
            }
        };
        rendererThread.setName("Video - Renderer (MediaCodec)");
        rendererThread.setPriority(Thread.NORM_PRIORITY + 2);
        rendererThread.start();
    }

    private int dequeueInputBuffer() {
        int index = -1;
        long startTime;

        startTime = MediaCodecHelper.getMonotonicMillis();

        try {
            while (index < 0 && !stopping) {
                index = videoDecoder.dequeueInputBuffer(10000);
            }
        } catch (Exception e) {
            handleDecoderException(e, null, 0, true);
            return MediaCodec.INFO_TRY_AGAIN_LATER;
        }

        int deltaMs = (int)(MediaCodecHelper.getMonotonicMillis() - startTime);

        if (deltaMs >= 20) {
            LimeLog.warning("Dequeue input buffer ran long: " + deltaMs + " ms");
        }

        if (index < 0) {
            // We've been hung for 5 seconds and no other exception was reported,
            // so generate a decoder hung exception
            if (deltaMs >= 5000 && initialException == null) {
                DecoderHungException decoderHungException = new DecoderHungException(deltaMs);
                if (!reportedCrash) {
                    reportedCrash = true;
                    crashListener.notifyCrash(decoderHungException);
                }
                throw new RendererException(this, decoderHungException);
            }
            return index;
        }

        return index;
    }

    @Override
    public void start() {
        startRendererThread();
    }

    // !!! May be called even if setup()/start() fails !!!
    public void prepareForStop() {
        // Let the decoding code know to ignore codec exceptions now
        stopping = true;

        // Halt the rendering thread
        if (rendererThread != null) {
            rendererThread.interrupt();
        }
    }

    @Override
    public void stop() {
        // May be called already, but we'll call it now to be safe
        prepareForStop();

        // Wait for the renderer thread to shut down
        try {
            rendererThread.join();
        } catch (InterruptedException ignored) { }
    }

    @Override
    public void cleanup() {
        videoDecoder.release();
    }

    private boolean queueInputBuffer(int inputBufferIndex, int offset, int length, long timestampUs, int codecFlags) {
        try {
            videoDecoder.queueInputBuffer(inputBufferIndex,
                    offset, length,
                    timestampUs, codecFlags);
            return true;
        } catch (Exception e) {
            handleDecoderException(e, null, codecFlags, true);
            return false;
        }
    }

    // Using the new getInputBuffer() API on Lollipop allows
    // the framework to do some performance optimizations for us
    private ByteBuffer getEmptyInputBuffer(int inputBufferIndex) {
        ByteBuffer buf;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                buf = videoDecoder.getInputBuffer(inputBufferIndex);
            } catch (Exception e) {
                handleDecoderException(e, null, 0, true);
                return null;
            }
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
        if (sps.profileIdc == 100 && constrainedHighProfile) {
            LimeLog.info("Setting constraint set flags for constrained high profile");
            sps.constraintSet4Flag = true;
            sps.constraintSet5Flag = true;
        }
        else {
            // Force the constraints unset otherwise (some may be set by default)
            sps.constraintSet4Flag = false;
            sps.constraintSet5Flag = false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public int submitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, long receiveTimeMs) {
        if (stopping) {
            // Don't bother if we're stopping
            return MoonBridge.DR_OK;
        }

        if (lastFrameNumber == 0) {
            activeWindowVideoStats.measurementStartTimestamp = System.currentTimeMillis();
        } else if (frameNumber != lastFrameNumber && frameNumber != lastFrameNumber + 1) {
            // We can receive the same "frame" multiple times if it's an IDR frame.
            // In that case, each frame start NALU is submitted independently.
            activeWindowVideoStats.framesLost += frameNumber - lastFrameNumber - 1;
            activeWindowVideoStats.totalFrames += frameNumber - lastFrameNumber - 1;
            activeWindowVideoStats.frameLossEvents++;
        }

        lastFrameNumber = frameNumber;

        // Flip stats windows roughly every second
        if (System.currentTimeMillis() >= activeWindowVideoStats.measurementStartTimestamp + 1000) {
            if (prefs.enablePerfOverlay) {
                VideoStats lastTwo = new VideoStats();
                lastTwo.add(lastWindowVideoStats);
                lastTwo.add(activeWindowVideoStats);
                VideoStatsFps fps = lastTwo.getFps();
                String decoder;

                if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                    decoder = avcDecoder.getName();
                } else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
                    decoder = hevcDecoder.getName();
                } else {
                    decoder = "(unknown)";
                }

                float decodeTimeMs = (float)lastTwo.decoderTimeMs / lastTwo.totalFramesReceived;
                String perfText = context.getString(
                        R.string.perf_overlay_text,
                        initialWidth + "x" + initialHeight,
                        decoder,
                        fps.totalFps,
                        fps.receivedFps,
                        fps.renderedFps,
                        (float)lastTwo.framesLost / lastTwo.totalFrames * 100,
                        ((float)lastTwo.totalTimeMs / lastTwo.totalFramesReceived) - decodeTimeMs,
                        decodeTimeMs);
                perfListener.onPerfUpdate(perfText);
            }

            globalVideoStats.add(activeWindowVideoStats);
            lastWindowVideoStats.copy(activeWindowVideoStats);
            activeWindowVideoStats.clear();
            activeWindowVideoStats.measurementStartTimestamp = System.currentTimeMillis();
        }

        activeWindowVideoStats.totalFramesReceived++;
        activeWindowVideoStats.totalFrames++;

        int inputBufferIndex;
        ByteBuffer buf;

        long timestampUs = System.nanoTime() / 1000;

        if (!FRAME_RENDER_TIME_ONLY) {
            // Count time from first packet received to decode start
            activeWindowVideoStats.totalTimeMs += (timestampUs / 1000) - receiveTimeMs;
        }

        if (timestampUs <= lastTimestampUs) {
            // We can't submit multiple buffers with the same timestamp
            // so bump it up by one before queuing
            timestampUs = lastTimestampUs + 1;
        }

        lastTimestampUs = timestampUs;

        int codecFlags = 0;

        // H264 SPS
        if (decodeUnitData[4] == 0x67) {
            numSpsIn++;

            ByteBuffer spsBuf = ByteBuffer.wrap(decodeUnitData);

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
                if (initialWidth <= 720 && initialHeight <= 480) {
                    // Max 5 buffered frames at 720x480x60
                    LimeLog.info("Patching level_idc to 31");
                    sps.levelIdc = 31;
                }
                else if (initialWidth <= 1280 && initialHeight <= 720) {
                    // Max 5 buffered frames at 1280x720x60
                    LimeLog.info("Patching level_idc to 32");
                    sps.levelIdc = 32;
                }
                else if (initialWidth <= 1920 && initialHeight <= 1080) {
                    // Max 4 buffered frames at 1920x1080x64
                    LimeLog.info("Patching level_idc to 42");
                    sps.levelIdc = 42;
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
                sps.numRefFrames = 1;
            }

            // GFE 2.5.11 changed the SPS to add additional extensions
            // Some devices don't like these so we remove them here.
            sps.vuiParams.videoSignalTypePresentFlag = false;
            sps.vuiParams.colourDescriptionPresentFlag = false;
            sps.vuiParams.chromaLocInfoPresentFlag = false;

            if ((needsSpsBitstreamFixup || isExynos4) && !refFrameInvalidationActive) {
                // The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
                // or max_dec_frame_buffering which increases decoding latency on Tegra.

                // GFE 2.5.11 started sending bitstream restrictions
                if (sps.vuiParams.bitstreamRestriction == null) {
                    LimeLog.info("Adding bitstream restrictions");
                    sps.vuiParams.bitstreamRestriction = new VUIParameters.BitstreamRestriction();
                    sps.vuiParams.bitstreamRestriction.motionVectorsOverPicBoundariesFlag = true;
                    sps.vuiParams.bitstreamRestriction.log2MaxMvLengthHorizontal = 16;
                    sps.vuiParams.bitstreamRestriction.log2MaxMvLengthVertical = 16;
                    sps.vuiParams.bitstreamRestriction.numReorderFrames = 0;
                }
                else {
                    LimeLog.info("Patching bitstream restrictions");
                }

                // Some devices throw errors if maxDecFrameBuffering < numRefFrames
                sps.vuiParams.bitstreamRestriction.maxDecFrameBuffering = sps.numRefFrames;

                // These values are the defaults for the fields, but they are more aggressive
                // than what GFE sends in 2.5.11, but it doesn't seem to cause picture problems.
                sps.vuiParams.bitstreamRestriction.maxBytesPerPicDenom = 2;
                sps.vuiParams.bitstreamRestriction.maxBitsPerMbDenom = 1;

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
                sps.profileIdc = 66;
                savedSps = sps;
            }

            // Patch the SPS constraint flags
            doProfileSpecificSpsPatching(sps);

            // The H264Utils.writeSPS function safely handles
            // Annex B NALUs (including NALUs with escape sequences)
            ByteBuffer escapedNalu = H264Utils.writeSPS(sps, decodeUnitLength);

            // Batch this to submit together with PPS
            spsBuffer = new byte[5 + escapedNalu.limit()];
            System.arraycopy(decodeUnitData, 0, spsBuffer, 0, 5);
            escapedNalu.get(spsBuffer, 5, escapedNalu.limit());
            return MoonBridge.DR_OK;
        }
        else if (decodeUnitType == MoonBridge.BUFFER_TYPE_VPS) {
            numVpsIn++;

            // Batch this to submit together with SPS and PPS per AOSP docs
            vpsBuffer = new byte[decodeUnitLength];
            System.arraycopy(decodeUnitData, 0, vpsBuffer, 0, decodeUnitLength);
            return MoonBridge.DR_OK;
        }
        // Only the HEVC SPS hits this path (H.264 is handled above)
        else if (decodeUnitType == MoonBridge.BUFFER_TYPE_SPS) {
            numSpsIn++;

            // Batch this to submit together with VPS and PPS per AOSP docs
            spsBuffer = new byte[decodeUnitLength];
            System.arraycopy(decodeUnitData, 0, spsBuffer, 0, decodeUnitLength);
            return MoonBridge.DR_OK;
        }
        else if (decodeUnitType == MoonBridge.BUFFER_TYPE_PPS) {
            numPpsIn++;

            // If this is the first CSD blob or we aren't supporting
            // adaptive playback, we will submit the CSD blob in a
            // separate input buffer.
            if (!submittedCsd || !adaptivePlayback) {
                inputBufferIndex = dequeueInputBuffer();
                if (inputBufferIndex < 0) {
                    // We're being torn down now
                    return MoonBridge.DR_NEED_IDR;
                }

                buf = getEmptyInputBuffer(inputBufferIndex);
                if (buf == null) {
                    // We're being torn down now
                    return MoonBridge.DR_NEED_IDR;
                }

                // When we get the PPS, submit the VPS and SPS together with
                // the PPS, as required by AOSP docs on use of MediaCodec.
                if (vpsBuffer != null) {
                    buf.put(vpsBuffer);
                }
                if (spsBuffer != null) {
                    buf.put(spsBuffer);
                }

                // This is the CSD blob
                codecFlags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            }
            else {
                // Batch this to submit together with the next I-frame
                ppsBuffer = new byte[decodeUnitLength];
                System.arraycopy(decodeUnitData, 0, ppsBuffer, 0, decodeUnitLength);

                // Next call will be I-frame data
                submitCsdNextCall = true;

                return MoonBridge.DR_OK;
            }
        }
        else {
            inputBufferIndex = dequeueInputBuffer();
            if (inputBufferIndex < 0) {
                // We're being torn down now
                return MoonBridge.DR_NEED_IDR;
            }

            buf = getEmptyInputBuffer(inputBufferIndex);
            if (buf == null) {
                // We're being torn down now
                return MoonBridge.DR_NEED_IDR;
            }

            if (submitCsdNextCall) {
                if (vpsBuffer != null) {
                    buf.put(vpsBuffer);
                }
                if (spsBuffer != null) {
                    buf.put(spsBuffer);
                }
                if (ppsBuffer != null) {
                    buf.put(ppsBuffer);
                }

                submitCsdNextCall = false;
            }
        }

        if (decodeUnitLength > buf.limit() - buf.position()) {
            IllegalArgumentException exception = new IllegalArgumentException(
                    "Decode unit length "+decodeUnitLength+" too large for input buffer "+buf.limit());
            if (!reportedCrash) {
                reportedCrash = true;
                crashListener.notifyCrash(exception);
            }
            throw new RendererException(this, exception);
        }

        // Copy data from our buffer list into the input buffer
        buf.put(decodeUnitData, 0, decodeUnitLength);

        if (!queueInputBuffer(inputBufferIndex,
                0, buf.position(),
                timestampUs, codecFlags)) {
            return MoonBridge.DR_NEED_IDR;
        }

        if ((codecFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            submittedCsd = true;

            if (needsBaselineSpsHack) {
                needsBaselineSpsHack = false;

                if (!replaySps()) {
                    return MoonBridge.DR_NEED_IDR;
                }

                LimeLog.info("SPS replay complete");
            }
        }

        return MoonBridge.DR_OK;
    }

    private boolean replaySps() {
        int inputIndex = dequeueInputBuffer();
        if (inputIndex < 0) {
            return false;
        }

        ByteBuffer inputBuffer = getEmptyInputBuffer(inputIndex);
        if (inputBuffer == null) {
            return false;
        }

        // Write the Annex B header
        inputBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01, 0x67});

        // Switch the H264 profile back to high
        savedSps.profileIdc = 100;

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
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0;
        }
        return (int)(globalVideoStats.totalTimeMs / globalVideoStats.totalFramesReceived);
    }

    public int getAverageDecoderLatency() {
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0;
        }
        return (int)(globalVideoStats.decoderTimeMs / globalVideoStats.totalFramesReceived);
    }

    static class DecoderHungException extends RuntimeException {
        private int hangTimeMs;

        DecoderHungException(int hangTimeMs) {
            this.hangTimeMs = hangTimeMs;
        }

        public String toString() {
            String str = "";

            str += "Hang time: "+hangTimeMs+" ms\n";
            str += super.toString();

            return str;
        }
    }

    static class RendererException extends RuntimeException {
        private static final long serialVersionUID = 8985937536997012406L;

        private String text;

        RendererException(MediaCodecDecoderRenderer renderer, Exception e) {
            this.text = generateText(renderer, e, null, 0);
        }

        RendererException(MediaCodecDecoderRenderer renderer, Exception e, ByteBuffer currentBuffer, int currentCodecFlags) {
            this.text = generateText(renderer, e, currentBuffer, currentCodecFlags);
        }

        public String toString() {
            return text;
        }

        private String generateText(MediaCodecDecoderRenderer renderer, Exception originalException, ByteBuffer currentBuffer, int currentCodecFlags) {
            String str = "";

            str += "Format: "+String.format("%x", renderer.videoFormat)+"\n";
            str += "AVC Decoder: "+((renderer.avcDecoder != null) ? renderer.avcDecoder.getName():"(none)")+"\n";
            str += "HEVC Decoder: "+((renderer.hevcDecoder != null) ? renderer.hevcDecoder.getName():"(none)")+"\n";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && renderer.avcDecoder != null) {
                Range<Integer> avcWidthRange = renderer.avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();
                str += "AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper()+"\n";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && renderer.hevcDecoder != null) {
                Range<Integer> hevcWidthRange = renderer.hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();
                str += "HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper()+"\n";
            }
            str += "Adaptive playback: "+renderer.adaptivePlayback+"\n";
            str += "GL Renderer: "+renderer.glRenderer+"\n";
            str += "Build fingerprint: "+Build.FINGERPRINT+"\n";
            str += "Foreground: "+renderer.foreground+"\n";
            str += "Consecutive crashes: "+renderer.consecutiveCrashCount+"\n";
            str += "RFI active: "+renderer.refFrameInvalidationActive+"\n";
            str += "Video dimensions: "+renderer.initialWidth+"x"+renderer.initialHeight+"\n";
            str += "FPS target: "+renderer.refreshRate+"\n";
            str += "Bitrate: "+renderer.prefs.bitrate+" Kbps \n";
            str += "In stats: "+renderer.numVpsIn+", "+renderer.numSpsIn+", "+renderer.numPpsIn+"\n";
            str += "Total frames received: "+renderer.globalVideoStats.totalFramesReceived+"\n";
            str += "Total frames rendered: "+renderer.globalVideoStats.totalFramesRendered+"\n";
            str += "Frame losses: "+renderer.globalVideoStats.framesLost+" in "+renderer.globalVideoStats.frameLossEvents+" loss events\n";
            str += "Average end-to-end client latency: "+renderer.getAverageEndToEndLatency()+"ms\n";
            str += "Average hardware decoder latency: "+renderer.getAverageDecoderLatency()+"ms\n";

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (originalException instanceof CodecException) {
                    CodecException ce = (CodecException) originalException;

                    str += "Diagnostic Info: "+ce.getDiagnosticInfo()+"\n";
                    str += "Recoverable: "+ce.isRecoverable()+"\n";
                    str += "Transient: "+ce.isTransient()+"\n";

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        str += "Codec Error Code: "+ce.getErrorCode()+"\n";
                    }
                }
            }

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
