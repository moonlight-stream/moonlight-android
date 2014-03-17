package com.limelight.binding.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;

public class AndroidAudioRenderer implements AudioRenderer {

	public static final int FRAME_SIZE = 960;
	
	private AudioTrack track;

	@Override
	public void streamInitialized(int channelCount, int sampleRate) {
		int channelConfig;
		int bufferSize;

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

		bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT),
				FRAME_SIZE * 2);
		
		// Round to next frame
		bufferSize = (((bufferSize + (FRAME_SIZE - 1)) / FRAME_SIZE) * FRAME_SIZE);
		
		LimeLog.info("Audio track buffer size: "+bufferSize);
		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate,
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferSize,
				AudioTrack.MODE_STREAM);

		track.play();
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
