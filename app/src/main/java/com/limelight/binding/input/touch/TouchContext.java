package com.limelight.binding.input.touch;

public interface TouchContext {
    int getActionIndex();
    void setPointerCount(int pointerCount);
    boolean touchDownEvent(int eventX, int eventY, boolean isNewFinger);
    boolean touchMoveEvent(int eventX, int eventY);
    void touchUpEvent(int eventX, int eventY);
    void cancelTouch();
    boolean isCancelled();
}
