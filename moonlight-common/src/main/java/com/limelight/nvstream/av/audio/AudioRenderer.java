package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
	int getCapabilities();
	
	void setup(int audioConfiguration);
	
	void playDecodedAudio(byte[] audioData);
	
	void cleanup();
}
