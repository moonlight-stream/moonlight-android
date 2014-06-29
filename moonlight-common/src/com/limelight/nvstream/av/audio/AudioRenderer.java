package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
	// playDecodedAudio() is lightweight, so don't use an extra thread for playback
	public static final int CAPABILITY_DIRECT_SUBMIT = 0x1;
	
	public int getCapabilities();
	
	public boolean streamInitialized(int channelCount, int sampleRate);
	
	public void playDecodedAudio(byte[] audioData, int offset, int length);
	
	public void streamClosing();
}
