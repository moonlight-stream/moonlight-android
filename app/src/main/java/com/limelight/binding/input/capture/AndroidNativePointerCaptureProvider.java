package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

@TargetApi(Build.VERSION_CODES.O)
public class AndroidNativePointerCaptureProvider extends InputCaptureProvider {

    private View targetView;

    public AndroidNativePointerCaptureProvider(View targetView) {
        this.targetView = targetView;
    }

    public static boolean isCaptureProviderSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    @Override
    public void enableCapture() {
        super.enableCapture();
        targetView.requestPointerCapture();
    }

    @Override
    public void disableCapture() {
        super.disableCapture();
        targetView.releasePointerCapture();
    }

    @Override
    public boolean isCapturingActive() {
        return targetView.hasPointerCapture();
    }

    @Override
    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        return event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    @Override
    public float getRelativeAxisX(MotionEvent event) {
        return event.getX();
    }

    @Override
    public float getRelativeAxisY(MotionEvent event) {
        return event.getY();
    }
}
