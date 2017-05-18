package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
	int setup(int audioConfiguration);
	
	void playDecodedAudio(byte[] audioData);
	
	void cleanup();
}
