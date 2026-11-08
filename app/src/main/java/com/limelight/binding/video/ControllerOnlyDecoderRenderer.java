package com.limelight.binding.video;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;

/**
 * Accepts and discards video frames without decoding or rendering them.
 * Audio, controller input, and the rest of the stream remain active.
 */
public class ControllerOnlyDecoderRenderer extends VideoDecoderRenderer {
    @Override
    public int setup(int format, int width, int height, int redrawRate) {
        return 0;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public int submitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,
                                int frameNumber, int frameType, char frameHostProcessingLatency,
                                long receiveTimeUs, long enqueueTimeUs) {
        return MoonBridge.DR_OK;
    }

    @Override
    public void cleanup() {
    }

    @Override
    public int getCapabilities() {
        return 0;
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
    }
}
