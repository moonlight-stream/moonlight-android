package com.limelight.nvstream.jni;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class MoonBridge {
    /* See documentation in Limelight.h for information about these functions and constants */

    public static final int AUDIO_CONFIGURATION_STEREO = 0;
    public static final int AUDIO_CONFIGURATION_51_SURROUND = 1;

    public static final int VIDEO_FORMAT_H264 = 1;
    public static final int VIDEO_FORMAT_H265 = 2;

    public static final int CAPABILITY_DIRECT_SUBMIT = 1;
    public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC = 2;
    public static final int CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC = 4;

    public static final int DR_OK = 0;
    public static final int DR_NEED_IDR = -1;

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

    public static int bridgeDrSetup(int videoFormat, int width, int height, int redrawRate) {
        if (videoRenderer != null) {
            return videoRenderer.setup(videoFormat, width, height, redrawRate);
        }
        else {
            return -1;
        }
    }

    public static void bridgeDrCleanup() {
        if (videoRenderer != null) {
            videoRenderer.cleanup();
        }
    }

    public static int bridgeDrSubmitDecodeUnit(byte[] frameData) {
        if (videoRenderer != null) {
            return videoRenderer.submitDecodeUnit(frameData);
        }
        else {
            return DR_OK;
        }
    }

    public static int bridgeArInit(int audioConfiguration) {
        if (audioRenderer != null) {
            return audioRenderer.setup(audioConfiguration);
        }
        else {
            return -1;
        }
    }

    public static void bridgeArCleanup() {
        if (audioRenderer != null) {
            audioRenderer.cleanup();
        }
    }

    public static void bridgeArPlaySample(byte[] pcmData) {
        if (audioRenderer != null) {
            audioRenderer.playDecodedAudio(pcmData);
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

    public static void bridgeClStageFailed(int stage, long errorCode) {
        if (connectionListener != null) {
            connectionListener.stageFailed(getStageName(stage), errorCode);
        }
    }

    public static void bridgeClConnectionStarted() {
        if (connectionListener != null) {
            connectionListener.connectionStarted();
        }
    }

    public static void bridgeClConnectionTerminated(long errorCode) {
        if (connectionListener != null) {
            connectionListener.connectionTerminated(errorCode);
        }
    }

    public static void bridgeClDisplayMessage(String message) {
        if (connectionListener != null) {
            connectionListener.displayMessage(message);
        }
    }

    public static void bridgeClDisplayTransientMessage(String message) {
        if (connectionListener != null) {
            connectionListener.displayTransientMessage(message);
        }
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
    }

    public static native int startConnection(String address, String appVersion, String gfeVersion,
                                              int width, int height, int fps,
                                              int bitrate, boolean streamingRemotely,
                                              int audioConfiguration, boolean supportsHevc,
                                              byte[] riAesKey, byte[] riAesIv,
                                              int videoCapabilities);

    public static native void stopConnection();

    public static native void sendMouseMove(short deltaX, short deltaY);

    public static native void sendMouseButton(byte buttonEvent, byte mouseButton);

    public static native void sendMultiControllerInput(short controllerNumber,
                                    short activeGamepadMask, short buttonFlags,
                                    byte leftTrigger, byte rightTrigger,
                                    short leftStickX, short leftStickY,
                                    short rightStickX, short rightStickY);

    public static native void sendControllerInput(short buttonFlags,
                                    byte leftTrigger, byte rightTrigger,
                                    short leftStickX, short leftStickY,
                                    short rightStickX, short rightStickY);

    public static native void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier);

    public static native void sendMouseScroll(byte scrollClicks);

    public static native String getStageName(int stage);

    public static native void init();
}
