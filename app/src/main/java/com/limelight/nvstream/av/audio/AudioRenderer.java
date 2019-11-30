package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
    int setup(int audioConfiguration, int sampleRate, int samplesPerFrame);

    void start();

    void stop();
    
    void playDecodedAudio(short[] audioData);
    
    void cleanup();
}
