package com.limelight.binding.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.limelight.nvstream.av.audio.AudioRenderer;

public class AndroidAudioRenderer implements AudioRenderer {

	private AudioTrack track;

	@Override
	public void streamInitialized(int channelCount, int sampleRate) {
		int channelConfig;

		switch (channelCount)
		{
		case 1:
			channelConfig = AudioFormat.CHANNEL_OUT_MONO;
			break;
		case 2:
			channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
			break;
		default:
			throw new IllegalArgumentException("Decoder returned unhandled channel count");
		}

		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate,
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT,
				1024, // 1KB buffer
				AudioTrack.MODE_STREAM);

		track.play();
	}

	@Override
	public void playDecodedAudio(short[] audioData, int offset, int length) {
		track.write(audioData, offset, length);
	}

	@Override
	public void streamClosing() {
		if (track != null) {
			track.release();
		}
	}
}
