package com.limelight.binding.input.trackpad.Core;

public interface TrackpadListener {
    void mouseMove(float x, float y);
    void mouseButton(int direction,int button);
    void mouseScroll(float y);
}
