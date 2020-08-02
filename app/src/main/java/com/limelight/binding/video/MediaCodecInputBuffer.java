package com.limelight.binding.video;

import java.nio.ByteBuffer;

public class MediaCodecInputBuffer {

    public int index;
    public ByteBuffer buffer;
    public long timestampUs;
    public int codecFlags;
}
