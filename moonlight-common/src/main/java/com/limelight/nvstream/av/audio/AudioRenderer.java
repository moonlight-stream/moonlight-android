package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
	int setup(int audioConfiguration);

	void start();

	void stop();
	
	void playDecodedAudio(short[] audioData);
	
	void cleanup();
}
