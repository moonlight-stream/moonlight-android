package com.limelight.nvstream.jni;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

/** Owned by a single opted-in connection. Close before native connection teardown. */
public final class NativeMicCapture implements AutoCloseable {
    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SAMPLES = 240;

    static {
        System.loadLibrary("moonlight-core");
    }

    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Thread worker;
    public interface Listener {
        void onMicrophoneStateChanged(boolean active);
    }

    private NativeMicCapture(AudioRecord input, long handle, Listener listener) {
        worker = new Thread(() -> capture(input, handle, listener), "Moonlight microphone");
        worker.start();
    }

    public static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** Call after successful native connection start, while foreground and opted in. */
    public static NativeMicCapture start(Context context, Listener listener) {
        if (!hasPermission(context)) {
            return null;
        }
        long handle = create();
        if (handle == 0) {
            return null;
        }

        AudioRecord input = null;
        boolean handedOff = false;
        try {
            int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                return null;
            }
            input = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum, FRAME_SAMPLES * Short.BYTES * 4));
            if (input.getState() != AudioRecord.STATE_INITIALIZED) {
                return null;
            }
            NativeMicCapture capture = new NativeMicCapture(input, handle, listener);
            handedOff = true;
            return capture;
        } catch (IllegalArgumentException | SecurityException e) {
            return null;
        } finally {
            if (!handedOff) {
                try {
                    if (input != null) {
                        input.release();
                    }
                } finally {
                    destroy(handle);
                }
            }
        }
    }

    private void capture(AudioRecord input, long handle, Listener listener) {
        try {
            short[] frame = new short[FRAME_SAMPLES];
            int filled = 0;
            if (stopping.get()) {
                return;
            }
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                return;
            }
            listener.onMicrophoneStateChanged(true);
            while (!stopping.get()) {
                int read = input.read(frame, filled, FRAME_SAMPLES - filled,
                        AudioRecord.READ_NON_BLOCKING);
                if (read < 0) {
                    return;
                }
                if (read == 0) {
                    Thread.sleep(2);
                    continue;
                }
                filled += read;
                if (filled == FRAME_SAMPLES) {
                    if (stopping.get() || send(handle, frame) != 0) {
                        return;
                    }
                    filled = 0;
                }
            }
        } catch (IllegalStateException | SecurityException | InterruptedException e) {
            // Device loss, permission change or owner shutdown ends this capture.
        } finally {
            try {
                input.release();
            } finally {
                destroy(handle);
                listener.onMicrophoneStateChanged(false);
            }
        }
    }

    @Override
    public void close() {
        stopping.set(true);
        worker.interrupt();
        boolean interrupted = false;
        for (;;) {
            try {
                worker.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static native long create();
    private static native int send(long handle, short[] frame);
    private static native void destroy(long handle);
}
