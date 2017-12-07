package com.limelight.binding.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    private AudioTrack track;

    private AudioTrack createAudioTrack(int channelConfig, int bufferSize, boolean lowLatency) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return new AudioTrack(AudioManager.STREAM_MUSIC,
                    48000,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
        }
        else {
            AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME);
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(channelConfig)
                    .build();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Use FLAG_LOW_LATENCY on L through N
                if (lowLatency) {
                    attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                        .setAudioFormat(format)
                        .setAudioAttributes(attributesBuilder.build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferSize);

                // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
                if (lowLatency) {
                    trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
                }

                return trackBuilder.build();
            }
            else {
                return new AudioTrack(attributesBuilder.build(),
                        format,
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE);
            }
        }
    }

    @Override
    public int setup(int audioConfiguration) {
        int channelConfig;
        int bytesPerFrame;

        switch (audioConfiguration)
        {
            case MoonBridge.AUDIO_CONFIGURATION_STEREO:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                bytesPerFrame = 2 * 240 * 2;
                break;
            case MoonBridge.AUDIO_CONFIGURATION_51_SURROUND:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                bytesPerFrame = 6 * 240 * 2;
                break;
            default:
                LimeLog.severe("Decoder returned unhandled channel count");
                return -1;
        }

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (int i = 0; i < 4; i++) {
            boolean lowLatency;
            int bufferSize;

            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            switch (i) {
                case 0:
                case 1:
                    lowLatency = true;
                    break;
                case 2:
                case 3:
                    lowLatency = false;
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            switch (i) {
                case 0:
                case 2:
                    bufferSize = bytesPerFrame * 2;
                    break;

                case 1:
                case 3:
                    // Try the larger buffer size
                    bufferSize = Math.max(AudioTrack.getMinBufferSize(48000,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                            bytesPerFrame * 2);

                    // Round to next frame
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            // Skip low latency options if hardware sample rate isn't 48000Hz
            if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != 48000 && lowLatency) {
                continue;
            }

            try {
                track = createAudioTrack(channelConfig, bufferSize, lowLatency);
                track.play();

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: "+bufferSize+" "+lowLatency);
                break;
            } catch (Exception e) {
                // Try to release the AudioTrack if we got far enough
                e.printStackTrace();
                try {
                    if (track != null) {
                        track.release();
                        track = null;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        return 0;
    }

    @Override
    public void playDecodedAudio(byte[] audioData) {
        track.write(audioData, 0, audioData.length);
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void cleanup() {
        // Immediately drop all pending data
        track.pause();
        track.flush();

        track.release();
    }
}
