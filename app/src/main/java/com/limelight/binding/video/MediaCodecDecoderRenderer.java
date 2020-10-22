package com.limelight.binding.video;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.view.SurfaceHolder;

public class MediaCodecDecoderRenderer extends VideoDecoderRenderer {

    private static final boolean USE_FRAME_RENDER_TIME = false;
    private static final boolean FRAME_RENDER_TIME_ONLY = USE_FRAME_RENDER_TIME && false;

    private MediaCodecInfo avcDecoder;
    private MediaCodecInfo hevcDecoder;

    private Context context;
    private boolean needsSpsBitstreamFixup, isExynos4;
    private boolean adaptivePlayback, directSubmit;
    private boolean lowLatency;
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
    private PerfOverlayListener perfListener;

    private Timer infoTimer;
    private long videoDecoder2;

    private MediaFormat inputFormat;
    private MediaFormat outputFormat;
    private MediaFormat configuredFormat;

    private boolean needsBaselineSpsHack;
    private SeqParameterSet savedSps;

    private RendererException initialException;
    private long initialExceptionTimestamp;
    private static final int EXCEPTION_REPORT_DELAY_MS = 3000;

    private VideoStats activeWindowVideoStats;
    private VideoStats lastWindowVideoStats;
    private VideoStats globalVideoStats;

    private int refreshRate;
    private PreferenceConfiguration prefs;

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

    public boolean isBlacklistedForFrameRate(int frameRate) {
        return avcDecoder != null && MediaCodecHelper.decoderBlacklistedForFrameRate(avcDecoder.getName(), frameRate);
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

            lowLatency = MediaCodecHelper.decoderSupportsLowLatency(avcDecoder, mimeType);
            adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(avcDecoder, mimeType);
        }
        else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
            mimeType = "video/hevc";
            selectedDecoderName = hevcDecoder.getName();

            if (hevcDecoder == null) {
                LimeLog.severe("No available HEVC decoder!");
                return -2;
            }

            refFrameInvalidationActive = refFrameInvalidationHevc;

            lowLatency = MediaCodecHelper.decoderSupportsLowLatency(hevcDecoder, mimeType);
            adaptivePlayback = MediaCodecHelper.decoderSupportsAdaptivePlayback(hevcDecoder, mimeType);
        }
        else {
            // Unknown format
            LimeLog.severe("Unknown format");
            return -3;
        }

        // 变更解码器 begin

        int fps = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fps = prefs.fps;
        }

        boolean maxOperatingRate = MediaCodecHelper.decoderSupportsMaxOperatingRate(selectedDecoderName);

        videoDecoder2 = MoonBridge.createMediaCodec(renderTarget.getSurface(), selectedDecoderName, mimeType, width, height, redrawRate, prefs.fps, lowLatency,
            adaptivePlayback, maxOperatingRate, constrainedHighProfile, refFrameInvalidationActive, isExynos4);

        if (videoDecoder2 == 0) {
            return -4;
        }

        MoonBridge.setBufferCount(videoDecoder2, prefs.bufferCount);

        MoonBridge.startMediaCodec(videoDecoder2);

        // 变更解码器 end
/*
        // Codecs have been known to throw all sorts of crazy runtime exceptions
        // due to implementation problems
        try {
            videoDecoder = MediaCodec.createByCodecName(selectedDecoderName);
        } catch (Exception e) {
            e.printStackTrace();
            return -4;
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);

        // Avoid setting KEY_FRAME_RATE on Lollipop and earlier to reduce compatibility risk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // We use prefs.fps instead of redrawRate here because the low latency hack in Game.java
            // may leave us with an odd redrawRate value like 59 or 49 which might cause the decoder
            // to puke. To be safe, we'll use the unmodified value.
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, prefs.fps);
        }

        // Adaptive playback can also be enabled by the whitelist on pre-KitKat devices
        // so we don't fill these pre-KitKat
        if (adaptivePlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            videoFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
            videoFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lowLatency) {
            videoFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Set the Qualcomm vendor low latency extension if the Android R option is unavailable
            if (MediaCodecHelper.decoderSupportsQcomVendorLowLatency(selectedDecoderName)) {
                // MediaCodec supports vendor-defined format keys using the "vendor.<extension name>.<parameter name>" syntax.
                // These allow access to functionality that is not exposed through documented MediaFormat.KEY_* values.
                // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/common/inc/vidc_vendor_extensions.h;l=67
                //
                // Examples of Qualcomm's vendor extensions for Snapdragon 845:
                // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp
                // https://cs.android.com/android/_/android/platform/hardware/qcom/sm8150/media/+/0621ceb1c1b19564999db8293574a0e12952ff6c
                videoFormat.setInteger("vendor.qti-ext-dec-low-latency.enable", 1);
            }

            if (MediaCodecHelper.decoderSupportsMaxOperatingRate(selectedDecoderName)) {
                videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
            }
        }

        configuredFormat = videoFormat;
        LimeLog.info("Configuring with format: "+configuredFormat);

        try {
            videoDecoder.configure(videoFormat, renderTarget.getSurface(), null, 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // This will contain the actual accepted input format attributes
                inputFormat = videoDecoder.getInputFormat();
                LimeLog.info("Input format: "+inputFormat);
            }

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
*/
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
                if (SystemClock.uptimeMillis() - initialExceptionTimestamp >= EXCEPTION_REPORT_DELAY_MS) {
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
                initialExceptionTimestamp = SystemClock.uptimeMillis();
            }
        }
    }

    private void startRendererThread()
    {
        if (infoTimer != null)
            infoTimer.cancel();
        // if (prefs.enablePerfOverlay) {
        // Always show
        infoTimer = new Timer();
        infoTimer.schedule(new TimerTask(){
            public void run() {
                String format = context.getResources().getString(R.string.perf_overlay_text);
                String info = MoonBridge.formatDecoderInfo(videoDecoder2, format);
                perfListener.onPerfUpdate(info);
            }
        }, 0, 1000);
        //}
    }

    @Override
    public void start() {
        startRendererThread();
    }

    // !!! May be called even if setup()/start() fails !!!
    public void prepareForStop() {
        // Let the decoding code know to ignore codec exceptions now
        stopping = true;

        if (infoTimer != null)
            infoTimer.cancel();

        if (videoDecoder2 != 0) {
            // Fix jni GetFieldID crash
            long decoderTimeMs = globalVideoStats.decoderTimeMs;
            long totalTimeMs = globalVideoStats.totalTimeMs;
            int totalFrames = globalVideoStats.totalFrames;
            int totalFramesReceived = globalVideoStats.totalFramesReceived;
            int totalFramesRendered = globalVideoStats.totalFramesRendered;
            int frameLossEvents = globalVideoStats.frameLossEvents;
            int framesLost = globalVideoStats.framesLost;
            long measurementStartTimestamp = globalVideoStats.measurementStartTimestamp;

            MoonBridge.getVideoStats(videoDecoder2, globalVideoStats);

            MoonBridge.stopMediaCodec(videoDecoder2);
        }
    }

    @Override
    public void stop() {
        // May be called already, but we'll call it now to be safe
        prepareForStop();
    }

    @Override
    public void cleanup() {

        MoonBridge.deleteMediaCodec(videoDecoder2);
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

    public final static int copy(final ByteBuffer from, final int offset1,
                                 final ByteBuffer to, final int offset2, final int len) {
        System.arraycopy(from.array(), offset1, to.array(), offset2, len);
        to.limit(offset2 + len);
        return len;
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
                str += "AVC supported width range: "+avcWidthRange+"\n";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        Range<Double> avcFpsRange = renderer.avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getAchievableFrameRatesFor(renderer.initialWidth, renderer.initialHeight);
                        str += "AVC achievable FPS range: "+avcFpsRange+"\n";
                    } catch (IllegalArgumentException e) {
                        str += "AVC achievable FPS range: UNSUPPORTED!\n";
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && renderer.hevcDecoder != null) {
                Range<Integer> hevcWidthRange = renderer.hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();
                str += "HEVC supported width range: "+hevcWidthRange+"\n";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        Range<Double> hevcFpsRange = renderer.hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getAchievableFrameRatesFor(renderer.initialWidth, renderer.initialHeight);
                        str += "HEVC achievable FPS range: " + hevcFpsRange + "\n";
                    } catch (IllegalArgumentException e) {
                        str += "HEVC achievable FPS range: UNSUPPORTED!\n";
                    }
                }
            }
            str += "Configured format: "+renderer.configuredFormat+"\n";
            str += "Input format: "+renderer.inputFormat+"\n";
            str += "Output format: "+renderer.outputFormat+"\n";
            str += "Adaptive playback: "+renderer.adaptivePlayback+"\n";
            str += "GL Renderer: "+renderer.glRenderer+"\n";
            str += "Build fingerprint: "+Build.FINGERPRINT+"\n";
            str += "Foreground: "+renderer.foreground+"\n";
            str += "Consecutive crashes: "+renderer.consecutiveCrashCount+"\n";
            str += "RFI active: "+renderer.refFrameInvalidationActive+"\n";
            str += "Using modern SPS patching: "+(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)+"\n";
            str += "Low latency mode: "+renderer.lowLatency+"\n";
            str += "Video dimensions: "+renderer.initialWidth+"x"+renderer.initialHeight+"\n";
            str += "FPS target: "+renderer.refreshRate+"\n";
            str += "Bitrate: "+renderer.prefs.bitrate+" Kbps \n";
//            str += "In stats: "+renderer.numVpsIn+", "+renderer.numSpsIn+", "+renderer.numPpsIn+"\n";
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
