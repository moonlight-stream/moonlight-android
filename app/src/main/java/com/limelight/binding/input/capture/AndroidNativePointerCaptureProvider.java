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
        // SOURCE_MOUSE_RELATIVE is how SOURCE_MOUSE appears when our view has pointer capture.
        // SOURCE_TOUCHPAD will have relative axes populated iff our view has pointer capture.
        // See https://developer.android.com/reference/android/view/View#requestPointerCapture()
        int eventSource = event.getSource();
        return eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture());
    }

    @Override
    public float getRelativeAxisX(MotionEvent event) {
        int axis = (event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE) ?
                MotionEvent.AXIS_X : MotionEvent.AXIS_RELATIVE_X;
        float x = event.getAxisValue(axis);
        for (int i = 0; i < event.getHistorySize(); i++) {
            x += event.getHistoricalAxisValue(axis, i);
        }
        return x;
    }

    @Override
    public float getRelativeAxisY(MotionEvent event) {
        int axis = (event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE) ?
                MotionEvent.AXIS_Y : MotionEvent.AXIS_RELATIVE_Y;
        float y = event.getAxisValue(axis);
        for (int i = 0; i < event.getHistorySize(); i++) {
            y += event.getHistoricalAxisValue(axis, i);
        }
        return y;
    }
}
