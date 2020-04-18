package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;


// We extend AndroidPointerIconCaptureProvider because we want to also get the
// pointer icon hiding behavior over our stream view just in case pointer capture
// is unavailable on this system (ex: DeX, ChromeOS)
@TargetApi(Build.VERSION_CODES.O)
public class AndroidNativePointerCaptureProvider extends AndroidPointerIconCaptureProvider {
    private View targetView;

    public AndroidNativePointerCaptureProvider(Activity activity, View targetView) {
        super(activity, targetView);
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
    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        return event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    @Override
    public float getRelativeAxisX(MotionEvent event) {
        float x = event.getX();
        for (int i = 0; i < event.getHistorySize(); i++) {
            x += event.getHistoricalX(i);
        }
        return x;
    }

    @Override
    public float getRelativeAxisY(MotionEvent event) {
        float y = event.getY();
        for (int i = 0; i < event.getHistorySize(); i++) {
            y += event.getHistoricalY(i);
        }
        return y;
    }
}
