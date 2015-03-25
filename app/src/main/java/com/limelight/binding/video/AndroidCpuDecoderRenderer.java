package com.limelight.binding.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.graphics.PixelFormat;
import android.os.Build;
import android.view.SurfaceHolder;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

@SuppressWarnings("EmptyCatchBlock")
public class AndroidCpuDecoderRenderer extends EnhancedDecoderRenderer {

    private Thread rendererThread, decoderThread;
    private int targetFps;

    private static final int DECODER_BUFFER_SIZE = 92*1024;
    private ByteBuffer decoderBuffer;

    // Only sleep if the difference is above this value
    private static final int WAIT_CEILING_MS = 5;

    private static final int LOW_PERF = 1;
    private static final int MED_PERF = 2;
    private static final int HIGH_PERF = 3;

    private int totalFrames;
    private long totalTimeMs;

    private final int cpuCount = Runtime.getRuntime().availableProcessors();

    @SuppressWarnings("unused")
    private int findOptimalPerformanceLevel() {
        StringBuilder cpuInfo = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(new File("/proc/cpuinfo")));
            for (;;) {
                int ch = br.read();
                if (ch == -1)
                    break;
                cpuInfo.append((char)ch);
            }

            // Here we're doing very simple heuristics based on CPU model
            String cpuInfoStr = cpuInfo.toString();

            // We order them from greatest to least for proper detection
            // of devices with multiple sets of cores (like Exynos 5 Octa)
            // TODO Make this better (only even kind of works on ARM)
            if (Build.FINGERPRINT.contains("generic")) {
                // Emulator
                return LOW_PERF;
            }
            else if (cpuInfoStr.contains("0xc0f")) {
                // Cortex-A15
                return MED_PERF;
            }
            else if (cpuInfoStr.contains("0xc09")) {
                // Cortex-A9
                return LOW_PERF;
            }
            else if (cpuInfoStr.contains("0xc07")) {
                // Cortex-A7
                return LOW_PERF;
            }
            else {
                // Didn't have anything we're looking for
                return MED_PERF;
            }
        } catch (IOException e) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {}
            }
        }

        // Couldn't read cpuinfo, so assume medium
        return MED_PERF;
    }

    @Override
    public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
        this.targetFps = redrawRate;

        int perfLevel = LOW_PERF; //findOptimalPerformanceLevel();
        int threadCount;

        int avcFlags = 0;
        switch (perfLevel) {
        case HIGH_PERF:
            // Single threaded low latency decode is ideal but hard to acheive
            avcFlags = AvcDecoder.LOW_LATENCY_DECODE;
            threadCount = 1;
            break;

        case LOW_PERF:
            // Disable the loop filter for performance reasons
            avcFlags = AvcDecoder.FAST_BILINEAR_FILTERING;

            // Use plenty of threads to try to utilize the CPU as best we can
            threadCount = cpuCount - 1;
            break;

        default:
        case MED_PERF:
            avcFlags = AvcDecoder.BILINEAR_FILTERING;

            // Only use 2 threads to minimize frame processing latency
            threadCount = 2;
            break;
        }

        // If the user wants quality, we'll remove the low IQ flags
        if ((drFlags & VideoDecoderRenderer.FLAG_PREFER_QUALITY) != 0) {
            // Make sure the loop filter is enabled
            avcFlags &= ~AvcDecoder.DISABLE_LOOP_FILTER;

            // Disable the non-compliant speed optimizations
            avcFlags &= ~AvcDecoder.FAST_DECODE;

            LimeLog.info("Using high quality decoding");
        }

        SurfaceHolder sh = (SurfaceHolder)renderTarget;
        sh.setFormat(PixelFormat.RGBX_8888);

        int err = AvcDecoder.init(width, height, avcFlags, threadCount);
        if (err != 0) {
            throw new IllegalStateException("AVC decoder initialization failure: "+err);
        }

        if (!AvcDecoder.setRenderTarget(sh.getSurface())) {
            return false;
        }

        decoderBuffer = ByteBuffer.allocate(DECODER_BUFFER_SIZE + AvcDecoder.getInputPaddingSize());

        LimeLog.info("Using software decoding (performance level: "+perfLevel+")");

        return true;
    }

    @Override
    public boolean start(final VideoDepacketizer depacketizer) {
        decoderThread = new Thread() {
            @Override
            public void run() {
                DecodeUnit du;
                while (!isInterrupted()) {
                    try {
                        du = depacketizer.takeNextDecodeUnit();
                    } catch (InterruptedException e) {
                        break;
                    }

                    submitDecodeUnit(du);
                    depacketizer.freeDecodeUnit(du);
                }
            }
        };
        decoderThread.setName("Video - Decoder (CPU)");
        decoderThread.setPriority(Thread.MAX_PRIORITY - 1);
        decoderThread.start();

        rendererThread = new Thread() {
            @Override
            public void run() {
                long nextFrameTime = System.currentTimeMillis();
                while (!isInterrupted())
                {
                    long diff = nextFrameTime - System.currentTimeMillis();

                    if (diff > WAIT_CEILING_MS) {
                        try {
                            Thread.sleep(diff - WAIT_CEILING_MS);
                        } catch (InterruptedException e) {
                            return;
                        }
                        continue;
                    }

                    nextFrameTime = computePresentationTimeMs(targetFps);
                    AvcDecoder.redraw();
                }
            }
        };
        rendererThread.setName("Video - Renderer (CPU)");
        rendererThread.setPriority(Thread.MAX_PRIORITY);
        rendererThread.start();
        return true;
    }

    private long computePresentationTimeMs(int frameRate) {
        return System.currentTimeMillis() + (1000 / frameRate);
    }

    @Override
    public void stop() {
        rendererThread.interrupt();
        decoderThread.interrupt();

        try {
            rendererThread.join();
        } catch (InterruptedException e) { }
        try {
            decoderThread.join();
        } catch (InterruptedException e) { }
    }

    @Override
    public void release() {
        AvcDecoder.destroy();
    }

    private boolean submitDecodeUnit(DecodeUnit decodeUnit) {
        byte[] data;

        // Use the reserved decoder buffer if this decode unit will fit
        if (decodeUnit.getDataLength() <= DECODER_BUFFER_SIZE) {
            decoderBuffer.clear();

            for (ByteBufferDescriptor bbd = decodeUnit.getBufferHead();
                 bbd != null; bbd = bbd.nextDescriptor) {
                decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
            }

            data = decoderBuffer.array();
        }
        else {
            data = new byte[decodeUnit.getDataLength()+AvcDecoder.getInputPaddingSize()];

            int offset = 0;
            for (ByteBufferDescriptor bbd = decodeUnit.getBufferHead();
                 bbd != null; bbd = bbd.nextDescriptor) {
                System.arraycopy(bbd.data, bbd.offset, data, offset, bbd.length);
                offset += bbd.length;
            }
        }

        boolean success = (AvcDecoder.decode(data, 0, decodeUnit.getDataLength()) == 0);
        if (success) {
            long timeAfterDecode = System.currentTimeMillis();

            // Add delta time to the totals (excluding probable outliers)
            long delta = timeAfterDecode - decodeUnit.getReceiveTimestamp();
            if (delta >= 0 && delta < 1000) {
                totalTimeMs += delta;
                totalFrames++;
            }
        }

        return success;
    }

    @Override
    public int getCapabilities() {
        return 0;
    }

    @Override
    public int getAverageDecoderLatency() {
        return 0;
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
        return "CPU decoding";
    }
}
