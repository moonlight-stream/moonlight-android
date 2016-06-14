package com.limelight.binding.input.capture;

import android.view.MotionEvent;

public abstract class InputCaptureProvider {
    public abstract void enableCapture();
    public abstract void disableCapture();
    public void destroy() {}

    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        return false;
    }

    public float getRelativeAxisX(MotionEvent event) {
        return 0;
    }

    public float getRelativeAxisY(MotionEvent event) {
        return 0;
    }
}
