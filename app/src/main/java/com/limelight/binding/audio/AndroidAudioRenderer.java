package com.limelight.binding.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    private AudioTrack track;

    @Override
    public void setup(int audioConfiguration) {
        int channelConfig;
        int bufferSize;
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
                return;
        }

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.
        try {
            // Buffer two frames of audio if possible
            bufferSize = bytesPerFrame * 2;

            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    48000,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            track.play();
        } catch (Exception e) {
            // Try to release the AudioTrack if we got far enough
            try {
                if (track != null) {
                    track.release();
                }
            } catch (Exception ignored) {}

            // Now try the larger buffer size
            bufferSize = Math.max(AudioTrack.getMinBufferSize(48000,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                    bytesPerFrame * 2);

            // Round to next frame
            bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);

            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    48000,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            track.play();
        }

        LimeLog.info("Audio track buffer size: "+bufferSize);
    }

    @Override
    public void playDecodedAudio(byte[] audioData) {
        track.write(audioData, 0, audioData.length);
    }

    @Override
    public void cleanup() {
        if (track != null) {
            track.release();
        }
    }
}
