package com.limelight.binding.video;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public abstract class EnhancedDecoderRenderer extends VideoDecoderRenderer {
    public abstract boolean isHevcSupported();
    public abstract boolean isAvcSupported();
}
