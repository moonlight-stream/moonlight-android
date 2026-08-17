package com.limelight.nvstream.jni;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class MoonBridge {
    /* See documentation in Limelight.h for information about these functions and constants */

    public static final AudioConfiguration AUDIO_CONFIGURATION_STEREO = new AudioConfiguration(2, 0x3);
    public static final AudioConfiguration AUDIO_CONFIGURATION_51_SURROUND = new AudioConfiguration(6, 0x3F);
    public static final AudioConfiguration AUDIO_CONFIGURATION_71_SURROUND = new AudioConfiguration(8, 0x63F);
    public static final AudioConfiguration AUDIO_CONFIGURATION_714_SURROUND = new AudioConfiguration(12, 0xF63F);

    // Audio codec values negotiated via the "x-ml-audio.codec" RTSP attribute
    // (Sunshine extension; see LiGetNegotiatedAudioCodec in moonlight-common-c).
    // Defaults to OPUS for backward compatibility with stock GFE / older Sunshine.
    public static final int AUDIO_CODEC_OPUS = 0;
    public static final int AUDIO_CODEC_AC3  = 1;
    public static final int AUDIO_CODEC_EAC3 = 2;
    public static final int AUDIO_CODEC_PCM_S16 = 3;

    public static final int VIDEO_FORMAT_H264 = 0x0001;
    public static final int VIDEO_FORMAT_H265 = 0x0100;
    public static final int VIDEO_FORMAT_H265_MAIN10 = 0x0200;
    public static final int VIDEO_FORMAT_AV1_MAIN8 = 0x1000;
    public static final int VIDEO_FORMAT_AV1_MAIN10 = 0x2000;

    public static final int VIDEO_FORMAT_MASK_H264 = 0x000F;
    public static final int VIDEO_FORMAT_MASK_H265 = 0x0F00;
    public static final int VIDEO_FORMAT_MASK_AV1 = 0xF000;
    public static final int VIDEO_FORMAT_MASK_10BIT = 0x2200;

    public static final int BUFFER_TYPE_PICDATA = 0;
    public static final int BUFFER_TYPE_SPS = 1;
    public static final int BUFFER_TYPE_PPS = 2;
    public static final int BUFFER_TYPE_VPS = 3;

    public static final int FRAME_TYPE_PFRAME = 0;
    public static final int FRAME_TYPE_IDR = 1;

    public static final int COLORSPACE_REC_601 = 0;
    public static final int COLORSPACE_REC_709 = 1;
    public static final int COLORSPACE_REC_2020 = 2;

    public static final int COLOR_RANGE_LIMITED = 0;
    public static final int COLOR_RANGE_FULL = 1;

    // Client-side HDR mode values. HDR_MODE_HDR10_PLUS is a local selection that
    // uses the HDR10/PQ dynamicRangeMode on the wire and additionally enables
    // Android's HDR10+ metadata path.
    public static final int HDR_MODE_SDR = 0;      // SDR (default)
    public static final int HDR_MODE_HDR10 = 1;    // HDR10/PQ (SMPTE ST 2084)
    public static final int HDR_MODE_HLG = 2;      // HLG (Hybrid Log-Gamma, ARIB STD-B67)
    public static final int HDR_MODE_HDR10_PLUS = 3; // HDR10/PQ with ST 2094-40 dynamic metadata

    public static final int CAPABILITY_DIRECT_SUBMIT = 1;
    public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC = 2;
    public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC = 4;
    public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1 = 0x40;
    public static final int CAPABILITY_PRESERVE_HEVC_SEI = 0x80;

    public static final int DR_OK = 0;
    public static final int DR_NEED_IDR = -1;

    public static final int CONN_STATUS_OKAY = 0;
    public static final int CONN_STATUS_POOR = 1;

    public static final int ML_ERROR_GRACEFUL_TERMINATION = 0;
    public static final int ML_ERROR_NO_VIDEO_TRAFFIC = -100;
    public static final int ML_ERROR_NO_VIDEO_FRAME = -101;
    public static final int ML_ERROR_UNEXPECTED_EARLY_TERMINATION = -102;
    public static final int ML_ERROR_PROTECTED_CONTENT = -103;
    public static final int ML_ERROR_FRAME_CONVERSION = -104;

    public static final int ML_PORT_INDEX_TCP_47984 = 0;
    public static final int ML_PORT_INDEX_TCP_47989 = 1;
    public static final int ML_PORT_INDEX_TCP_48010 = 2;
    public static final int ML_PORT_INDEX_UDP_47998 = 8;
    public static final int ML_PORT_INDEX_UDP_47999 = 9;
    public static final int ML_PORT_INDEX_UDP_48000 = 10;
    public static final int ML_PORT_INDEX_UDP_48010 = 11;

    public static final int ML_PORT_FLAG_ALL = 0xFFFFFFFF;
    public static final int ML_PORT_FLAG_TCP_47984 = 0x0001;
    public static final int ML_PORT_FLAG_TCP_47989 = 0x0002;
    public static final int ML_PORT_FLAG_TCP_48010 = 0x0004;
    public static final int ML_PORT_FLAG_UDP_47998 = 0x0100;
    public static final int ML_PORT_FLAG_UDP_47999 = 0x0200;
    public static final int ML_PORT_FLAG_UDP_48000 = 0x0400;
    public static final int ML_PORT_FLAG_UDP_48010 = 0x0800;

    public static final int ML_TEST_RESULT_INCONCLUSIVE = 0xFFFFFFFF;

    public static final byte SS_KBE_FLAG_NON_NORMALIZED = 0x01;

    public static final int LI_ERR_UNSUPPORTED = -5501;

    public static final int LI_FF_TOUCHPAD_FRAME_EVENTS = 0x20;
    public static final int LI_FF_CURSOR_SHAPE = 0x40;

    public static final int LI_CURSOR_UPDATE_FLAG_SHAPE = 0x01;
    public static final int LI_CURSOR_UPDATE_FLAG_VISIBLE = 0x02;

    public static final int LI_CURSOR_MODE_VIDEO = 0;
    public static final int LI_CURSOR_MODE_LOCAL = 1;
    public static final int LI_CURSOR_MODE_OK = 0;
    public static final int LI_CURSOR_MODE_ERR_INVALID = -1;
    public static final int LI_CURSOR_MODE_ERR_UNSUPPORTED = -2;
    public static final int LI_CURSOR_MODE_ERR_NOT_CONNECTED = -3;
    public static final int LI_CURSOR_MODE_ERR_SEND_FAILED = -4;

    public static final byte LI_TOUCH_EVENT_HOVER       = 0x00;
    public static final byte LI_TOUCH_EVENT_DOWN        = 0x01;
    public static final byte LI_TOUCH_EVENT_UP          = 0x02;
    public static final byte LI_TOUCH_EVENT_MOVE        = 0x03;
    public static final byte LI_TOUCH_EVENT_CANCEL      = 0x04;
    public static final byte LI_TOUCH_EVENT_BUTTON_ONLY = 0x05;
    public static final byte LI_TOUCH_EVENT_HOVER_LEAVE = 0x06;
    public static final byte LI_TOUCH_EVENT_CANCEL_ALL  = 0x07;

    public static final byte LI_TOOL_TYPE_UNKNOWN = 0x00;
    public static final byte LI_TOOL_TYPE_PEN = 0x01;
    public static final byte LI_TOOL_TYPE_ERASER = 0x02;

    public static final byte LI_PEN_BUTTON_PRIMARY = 0x01;
    public static final byte LI_PEN_BUTTON_SECONDARY = 0x02;
    public static final byte LI_PEN_BUTTON_TERTIARY = 0x04;

    public static final byte SS_TOUCHPAD_BUTTON_PRIMARY = 0x01;

    public static final byte LI_TILT_UNKNOWN = (byte)0xFF;
    public static final short LI_ROT_UNKNOWN = (short)0xFFFF;

    public static final byte LI_CTYPE_UNKNOWN  = 0x00;
    public static final byte LI_CTYPE_XBOX     = 0x01;
    public static final byte LI_CTYPE_PS       = 0x02;
    public static final byte LI_CTYPE_NINTENDO = 0x03;

    public static final short LI_CCAP_ANALOG_TRIGGERS = 0x01;
    public static final short LI_CCAP_RUMBLE          = 0x02;
    public static final short LI_CCAP_TRIGGER_RUMBLE  = 0x04;
    public static final short LI_CCAP_TOUCHPAD        = 0x08;
    public static final short LI_CCAP_ACCEL           = 0x10;
    public static final short LI_CCAP_GYRO            = 0x20;
    public static final short LI_CCAP_BATTERY_STATE   = 0x40;
    public static final short LI_CCAP_RGB_LED         = 0x80;
    // Foundation Sunshine extension: prefer a DualSense device over DS4 for PS controllers.
    // Foundation extensions claim the top capability bit; upstream moonlight-common-c
    // owns the low bits (currently 0x00FF). Keep in sync with Sunshine's GAMEPAD_CAP_PREFER_DS5.
    public static final short LI_CCAP_PREFER_DS5      = (short) 0x8000;

    public static final byte LI_MOTION_TYPE_ACCEL = 0x01;
    public static final byte LI_MOTION_TYPE_GYRO  = 0x02;

    public static final byte LI_BATTERY_STATE_UNKNOWN      = 0x00;
    public static final byte LI_BATTERY_STATE_NOT_PRESENT  = 0x01;
    public static final byte LI_BATTERY_STATE_DISCHARGING  = 0x02;
    public static final byte LI_BATTERY_STATE_CHARGING     = 0x03;
    public static final byte LI_BATTERY_STATE_NOT_CHARGING = 0x04; // Connected to power but not charging
    public static final byte LI_BATTERY_STATE_FULL         = 0x05;

    public static final byte LI_BATTERY_PERCENTAGE_UNKNOWN = (byte)0xFF;

    // Sunshine clipboard sync. The native protocol packet is opaque — we build/parse
    // the v1 wire frame here in Java to keep moonlight-common-c free of payload
    // semantics. Frame layout (little-endian): u8 version=1, u8 kind, u32 token,
    // u32 length, bytes payload.
    private static final int CLIPBOARD_WIRE_VERSION = 1;
    /** Header size of the v1 wire frame; exposed so callers can size their payload. */
    public static final int CLIPBOARD_WIRE_HEADER = 10;

    public static final byte LI_CLIPBOARD_KIND_TEXT = 1;
    public static final byte LI_CLIPBOARD_KIND_PNG  = 2;
    /**
     * Reference to a payload uploaded out-of-band via the Sunshine HTTPS
     * /api/v1/clipboard/blob endpoint. Used when the inline payload would
     * exceed the per-frame ENet control packet cap (~64 KiB). The frame's
     * payload is a small UTF-8 JSON object: {"type":"ref","id":...,"mime":...,"size":...}.
     */
    public static final byte LI_CLIPBOARD_KIND_REF  = 3;

    /** Listener for inbound clipboard packets coming from the host. */
    public interface ClipboardListener {
        void onClipboardData(byte kind, int token, byte[] data);
    }

    private static volatile ClipboardListener clipboardListener;

    public static void setClipboardListener(ClipboardListener listener) {
        clipboardListener = listener;
    }

    private static AudioRenderer audioRenderer;
    private static VideoDecoderRenderer videoRenderer;
    private static NvConnectionListener connectionListener;

    static {
        System.loadLibrary("moonlight-core");
        init();
    }

    public static int CAPABILITY_SLICES_PER_FRAME(byte slices) {
        return slices << 24;
    }

    public static class AudioConfiguration {
        public final int channelCount;
        public final int channelMask;

        public AudioConfiguration(int channelCount, int channelMask) {
            this.channelCount = channelCount;
            this.channelMask = channelMask;
        }

        // Creates an AudioConfiguration from the integer value returned by moonlight-common-c
        // See CHANNEL_COUNT_FROM_AUDIO_CONFIGURATION() and CHANNEL_MASK_FROM_AUDIO_CONFIGURATION()
        // in Limelight.h
        private AudioConfiguration(int audioConfiguration) {
            // Check the magic byte before decoding to make sure we got something that's actually
            // a MAKE_AUDIO_CONFIGURATION()-based value and not something else like an older version
            // hardcoded AUDIO_CONFIGURATION value from an earlier version of moonlight-common-c.
            if ((audioConfiguration & 0xFF) != 0xCA) {
                throw new IllegalArgumentException("Audio configuration has invalid magic byte!");
            }

            this.channelCount = (audioConfiguration >> 8) & 0xFF;
            this.channelMask = (audioConfiguration >> 16) & 0xFFFF;
        }

        // See SURROUNDAUDIOINFO_FROM_AUDIO_CONFIGURATION() in Limelight.h
        public int getSurroundAudioInfo() {
            return channelMask << 16 | channelCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AudioConfiguration) {
                AudioConfiguration that = (AudioConfiguration)obj;
                return this.toInt() == that.toInt();
            }

            return false;
        }

        @Override
        public int hashCode() {
            return toInt();
        }

        // Returns the integer value expected by moonlight-common-c
        // See MAKE_AUDIO_CONFIGURATION() in Limelight.h
        public int toInt() {
            return ((channelMask) << 16) | (channelCount << 8) | 0xCA;
        }
    }

    public static int bridgeDrSetup(int videoFormat, int width, int height, int redrawRate) {
        if (videoRenderer != null) {
            return videoRenderer.setup(videoFormat, width, height, redrawRate);
        }
        else {
            return -1;
        }
    }

    public static void bridgeDrStart() {
        if (videoRenderer != null) {
            videoRenderer.start();
        }
    }

    public static void bridgeDrStop() {
        if (videoRenderer != null) {
            videoRenderer.stop();
        }
    }

    public static void bridgeDrCleanup() {
        if (videoRenderer != null) {
            videoRenderer.cleanup();
        }
    }

    public static int bridgeDrSubmitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                               int frameNumber, int frameType, char frameHostProcessingLatency,
                                               long receiveTimeUs, long enqueueTimeUs, long hostPresentationTimeUs) {
        if (videoRenderer != null) {
            return videoRenderer.submitDecodeUnit(decodeUnitData, decodeUnitLength,
                    decodeUnitType, frameNumber, frameType, frameHostProcessingLatency, receiveTimeUs, enqueueTimeUs,
                    hostPresentationTimeUs);
        }
        else {
            return DR_OK;
        }
    }

    public static int bridgeArInit(int audioConfiguration, int sampleRate, int samplesPerFrame, int codec, int bitrate) {
        if (audioRenderer != null) {
            return audioRenderer.setup(new AudioConfiguration(audioConfiguration), sampleRate, samplesPerFrame, codec, bitrate);
        }
        else {
            return -1;
        }
    }

    public static void bridgeArStart() {
        if (audioRenderer != null) {
            audioRenderer.start();
        }
    }

    public static void bridgeArStop() {
        if (audioRenderer != null) {
            audioRenderer.stop();
        }
    }

    public static void bridgeArCleanup() {
        if (audioRenderer != null) {
            audioRenderer.cleanup();
        }
    }

    public static void bridgeArPlaySample(short[] pcmData) {
        if (audioRenderer != null) {
            audioRenderer.playDecodedAudio(pcmData);
        }
    }

    /**
     * Called from native (callbacks.c) when the host streams encoded audio frames
     * (AC3 / E-AC3) instead of Opus PCM. Native side bypasses Opus decoding and
     * forwards raw bitstream bytes for the renderer to write to an AudioTrack
     * configured for direct passthrough.
     */
    public static void bridgeArPlayEncodedSample(byte[] encodedData, int length) {
        if (audioRenderer != null) {
            audioRenderer.playEncodedAudio(encodedData, length);
        }
    }

    public static void bridgeClStageStarting(int stage) {
        if (connectionListener != null) {
            connectionListener.stageStarting(getStageName(stage));
        }
    }

    public static void bridgeClStageComplete(int stage) {
        if (connectionListener != null) {
            connectionListener.stageComplete(getStageName(stage));
        }
    }

    public static void bridgeClStageFailed(int stage, int errorCode) {
        if (connectionListener != null) {
            connectionListener.stageFailed(getStageName(stage), getPortFlagsFromStage(stage), errorCode);
        }
    }

    public static void bridgeClConnectionStarted() {
        if (connectionListener != null) {
            connectionListener.connectionStarted();
        }
    }

    public static void bridgeClConnectionTerminated(int errorCode) {
        if (connectionListener != null) {
            connectionListener.connectionTerminated(errorCode);
        }
    }

    public static void bridgeClRumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        if (connectionListener != null) {
            connectionListener.rumble(controllerNumber, lowFreqMotor, highFreqMotor);
        }
    }

    public static void bridgeClConnectionStatusUpdate(int connectionStatus) {
        if (connectionListener != null) {
            connectionListener.connectionStatusUpdate(connectionStatus);
        }
    }

    public static void bridgeClSetHdrMode(boolean enabled, byte[] hdrMetadata) {
        if (connectionListener != null) {
            connectionListener.setHdrMode(enabled, hdrMetadata);
        }
    }

    public static void bridgeClRumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        if (connectionListener != null) {
            connectionListener.rumbleTriggers(controllerNumber, leftTrigger, rightTrigger);
        }
    }

    public static void bridgeClSetAdaptiveTriggers(short controllerNumber, byte eventFlags,
                                                   byte typeLeft, byte typeRight,
                                                   byte[] left, byte[] right) {
        if (connectionListener != null) {
            connectionListener.setAdaptiveTriggers(
                    controllerNumber, eventFlags, typeLeft, typeRight, left, right);
        }
    }

    public static void bridgeClSetMotionEventState(short controllerNumber, byte eventType, short sampleRateHz) {
        if (connectionListener != null) {
            connectionListener.setMotionEventState(controllerNumber, eventType, sampleRateHz);
        }
    }

    public static void bridgeClSetControllerLED(short controllerNumber, byte r, byte g, byte b) {
        if (connectionListener != null) {
            connectionListener.setControllerLED(controllerNumber, r, g, b);
        }
    }

    public static void bridgeClResolutionChanged(int width, int height) {
        if (connectionListener != null) {
            connectionListener.onResolutionChanged(width, height);
        }
    }

    /**
     * Invoked from native (callbacks.c) on the control receive thread when the host
     * sends a Sunshine clipboard sync packet. The payload is the raw v1 frame; we
     * parse it here and dispatch to the listener.
     */
    public static void bridgeClClipboardData(byte[] frame) {
        ClipboardListener l = clipboardListener;
        if (l == null || frame == null || frame.length < CLIPBOARD_WIRE_HEADER) return;
        if ((frame[0] & 0xFF) != CLIPBOARD_WIRE_VERSION) return;
        byte kind = frame[1];
        int token =  (frame[2] & 0xFF)
                  | ((frame[3] & 0xFF) << 8)
                  | ((frame[4] & 0xFF) << 16)
                  | ((frame[5] & 0xFF) << 24);
        int length =  (frame[6] & 0xFF)
                   | ((frame[7] & 0xFF) << 8)
                   | ((frame[8] & 0xFF) << 16)
                   | ((frame[9] & 0xFF) << 24);
        if (length < 0 || length > frame.length - CLIPBOARD_WIRE_HEADER) return;
        byte[] payload = new byte[length];
        System.arraycopy(frame, CLIPBOARD_WIRE_HEADER, payload, 0, length);
        l.onClipboardData(kind, token, payload);
    }

    public static void bridgeClCursorUpdate(int flags, int shapeId, int width, int height,
                                            int hotspotX, int hotspotY, byte[] bgraPixels) {
        if (connectionListener != null) {
            connectionListener.onCursorUpdate(flags, shapeId, width, height,
                    hotspotX, hotspotY, bgraPixels);
        }
    }

    /**
     * Encode a v1 clipboard frame and send it to the host. Returns 0 on success;
     * negative on failure (-1 invalid args, -2 unsupported by host, -3 transport).
     */
    public static int sendClipboardData(byte kind, int token, byte[] payload) {
        if (payload == null) payload = new byte[0];
        // 16-bit packet length on the wire — cap a touch below 65535 to leave
        // room for the v2 ENet header that wraps the payload.
        if (payload.length > 65500 - CLIPBOARD_WIRE_HEADER) return -1;
        byte[] frame = new byte[CLIPBOARD_WIRE_HEADER + payload.length];
        frame[0] = CLIPBOARD_WIRE_VERSION;
        frame[1] = kind;
        frame[2] = (byte)(token & 0xFF);
        frame[3] = (byte)((token >>> 8) & 0xFF);
        frame[4] = (byte)((token >>> 16) & 0xFF);
        frame[5] = (byte)((token >>> 24) & 0xFF);
        int len = payload.length;
        frame[6] = (byte)(len & 0xFF);
        frame[7] = (byte)((len >>> 8) & 0xFF);
        frame[8] = (byte)((len >>> 16) & 0xFF);
        frame[9] = (byte)((len >>> 24) & 0xFF);
        System.arraycopy(payload, 0, frame, CLIPBOARD_WIRE_HEADER, len);
        return sendClipboardFrameNative(frame);
    }

    public static void setupBridge(VideoDecoderRenderer videoRenderer, AudioRenderer audioRenderer, NvConnectionListener connectionListener) {
        MoonBridge.videoRenderer = videoRenderer;
        MoonBridge.audioRenderer = audioRenderer;
        MoonBridge.connectionListener = connectionListener;
    }

    public static void cleanupBridge() {
        MoonBridge.videoRenderer = null;
        MoonBridge.audioRenderer = null;
        MoonBridge.connectionListener = null;
        MoonBridge.clipboardListener = null;
    }

    public static native int startConnection(String address, String appVersion, String gfeVersion,
                                              String rtspSessionUrl, int serverCodecModeSupport,
                                              int width, int height, int fps,
                                              int bitrate, int packetSize, int streamingRemotely,
                                              int audioConfiguration, int supportedVideoFormats,
                                              int clientRefreshRateX100,
                                              byte[] riAesKey, byte[] riAesIv,
                                              int videoCapabilities,
                                              int colorSpace, int colorRange, int hdrMode,
                                              boolean enableMic, boolean controlOnly,
                                              int audioCodec, int audioBitrate);

    public static native void stopConnection();

    public static native void interruptConnection();

    public static native void sendMouseMove(short deltaX, short deltaY);

    public static native void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight);

    public static native void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight);

    public static native void sendMouseButton(byte buttonEvent, byte mouseButton);

    public static native void sendMultiControllerInput(short controllerNumber,
                                    short activeGamepadMask, int buttonFlags,
                                    byte leftTrigger, byte rightTrigger,
                                    short leftStickX, short leftStickY,
                                    short rightStickX, short rightStickY);

    public static native int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressure,
                                            float contactAreaMajor, float contactAreaMinor, short rotation);

    public static native int sendTouchpadEvent(byte eventType, int pointerId, float x, float y, float pressure,
                                               float contactAreaMajor, float contactAreaMinor, short rotation,
                                               short deviceWidthMm, short deviceHeightMm, byte buttonState);

    public static native int sendTouchpadFrameEvent(byte contactCount, byte[] eventTypes, int[] pointerIds,
                                                    float[] x, float[] y, float[] pressure, short rotation,
                                                    short deviceWidthMm, short deviceHeightMm, byte buttonState);

    public static native int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y,
                                          float pressure, float contactAreaMajor, float contactAreaMinor,
                                          short rotation, byte tilt);

    public static native int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type, int supportedButtonFlags, short capabilities);

    public static native int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId, float x, float y, float pressure);

    public static native int sendControllerMotionEvent(byte controllerNumber, byte motionType, float x, float y, float z);

    public static native int sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage);

    public static native void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags);

    public static native void sendMouseHighResScroll(short scrollAmount);

    public static native void sendMouseHighResHScroll(short scrollAmount);

    public static native void sendUtf8Text(String text);

    /**
     * Native opaque clipboard transport. The argument is the already-encoded v1
     * wire frame; see {@link #sendClipboardData(byte, int, byte[])} for the
     * helper that builds it.
     */
    private static native int sendClipboardFrameNative(byte[] frame);

    public static native String getStageName(int stage);

    public static native String findExternalAddressIP4(String stunHostName, int stunPort);

    public static native int getPendingAudioDuration();

    public static native int getPendingVideoFrames();

    public static native int testClientConnectivity(String testServerHostName, int referencePort, int testFlags);

    public static native int getPortFlagsFromStage(int stage);

    public static native int getPortFlagsFromTerminationErrorCode(int errorCode);

    public static native String stringifyPortFlags(int portFlags, String separator);

    // The RTT is in the top 32 bits, and the RTT variance is in the bottom 32 bits
    public static native long getEstimatedRttInfo();

    public static native String getLaunchUrlQueryParameters();

    public static native byte guessControllerType(int vendorId, int productId);

    public static native boolean guessControllerHasPaddles(int vendorId, int productId);

    public static native boolean guessControllerHasShareButton(int vendorId, int productId);

    public static native void init();

    // This function returns any extended feature flags supported by the host.
    public static native int getHostFeatureFlags();

    /** Selects host-composited or locally-rendered cursor mode for this session. */
    public static native int setCursorMode(int cursorMode);

    /** @return Negotiated audio codec for the active connection (AUDIO_CODEC_OPUS/AC3/EAC3). */
    public static native int getNegotiatedAudioCodec();

    /** @return Negotiated audio bitrate (bits/sec) for AC3/E-AC3, 0 for Opus. */
    public static native int getNegotiatedAudioBitrate();
    
    public static native int getMicPortNumber();
    
    public static native boolean isMicrophoneRequested();
    
    public static native int sendMicrophoneOpusData(byte[] opusData);
    
    public static native boolean isMicrophoneEncryptionEnabled();

    // Project-owned audio haptics SDK control
    public static native void setAudioHapticsSceneMode(int mode);
    public static native void setAudioHapticsOutputEnabled(boolean enabled);
    public static native void setAudioHapticsSessionHandle(long handle);

    // Surface DataSpace control for HDR color space
    // Uses ANativeWindow_setBuffersDataSpace() via JNI (API 28+)

    // DataSpace constants from android/data_space.h: STANDARD_BT2020 | TRANSFER | RANGE
    public static final int DATASPACE_BT2020_HLG_FULL = 0x0A060000;
    public static final int DATASPACE_BT2020_HLG_LIMITED = 0x12060000;
    public static final int DATASPACE_BT2020_PQ_FULL = 0x09C60000;
    public static final int DATASPACE_BT2020_PQ_LIMITED = 0x11C60000;

    /**
     * Set the DataSpace on a Surface for HDR content.
     * @return 0 on success, -1 if API unavailable, -2 if surface invalid
     */
    public static native int nativeSetSurfaceDataSpace(android.view.Surface surface, int dataSpace);
    public static native int nativeGetSurfaceDataSpace(android.view.Surface surface);
}
