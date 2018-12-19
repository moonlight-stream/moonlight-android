package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;

@TargetApi(Build.VERSION_CODES.O)
public class AndroidNativePointerCaptureProvider extends InputCaptureProvider {
    private Context context;
    private View targetView;
    private ViewGroup rootViewGroup;

    public AndroidNativePointerCaptureProvider(Activity activity, View targetView) {
        this.context = activity;
        this.targetView = targetView;
        this.rootViewGroup = (ViewGroup) activity.getWindow().getDecorView();
    }

    public static boolean isCaptureProviderSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    // DeX on Android 8.1 doesn't properly hide the mouse pointer when running in
    // windowed mode, even though the cursor is properly captured (and thus doesn't move anymore).
    // To work around this issue, hide the pointer icon when requesting pointer capture.
    private void setPointerIconOnAllViews(PointerIcon icon) {
        for (int i = 0; i < rootViewGroup.getChildCount(); i++) {
            View view = rootViewGroup.getChildAt(i);
            view.setPointerIcon(icon);
        }
        rootViewGroup.setPointerIcon(icon);
    }

    @Override
    public void enableCapture() {
        super.enableCapture();
        setPointerIconOnAllViews(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL));
        targetView.requestPointerCapture();
    }

    @Override
    public void disableCapture() {
        super.disableCapture();
        targetView.releasePointerCapture();
        setPointerIconOnAllViews(null);
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
