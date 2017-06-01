package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;

@TargetApi(Build.VERSION_CODES.N)
public class AndroidPointerIconCaptureProvider extends InputCaptureProvider {
    private ViewGroup rootViewGroup;
    private Context context;

    public AndroidPointerIconCaptureProvider(Activity activity) {
        this.context = activity;
        this.rootViewGroup = (ViewGroup) activity.getWindow().getDecorView();
    }

    public static boolean isCaptureProviderSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

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
    }

    @Override
    public void disableCapture() {
        super.disableCapture();
        setPointerIconOnAllViews(null);
    }

    @Override
    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        return event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) != 0 ||
                event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) != 0;
    }

    @Override
    public float getRelativeAxisX(MotionEvent event) {
        return event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
    }

    @Override
    public float getRelativeAxisY(MotionEvent event) {
        return event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
    }
}
