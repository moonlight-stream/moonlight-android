package com.limelight.binding.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;

public class AndroidAudioRenderer implements AudioRenderer {

    private AudioTrack track;

    @Override
    public boolean streamInitialized(int channelCount, int channelMask, int samplesPerFrame, int sampleRate) {
        int channelConfig;
        int bufferSize;
        int bytesPerFrame = (samplesPerFrame * 2);

        switch (channelCount)
        {
        case 1:
            channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            break;
        case 2:
            channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
            break;
        case 4:
            channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
            break;
        case 6:
            channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
            break;
        default:
            LimeLog.severe("Decoder returned unhandled channel count");
            return false;
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
                    sampleRate,
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
            bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                    bytesPerFrame * 2);

            // Round to next frame
            bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);

            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            track.play();
        }

        LimeLog.info("Audio track buffer size: "+bufferSize);

        return true;
    }

    @Override
    public void playDecodedAudio(byte[] audioData, int offset, int length) {
        track.write(audioData, offset, length);
    }

    @Override
    public void streamClosing() {
        if (track != null) {
            track.release();
        }
    }

    @Override
    public int getCapabilities() {
        return 0;
    }
}
