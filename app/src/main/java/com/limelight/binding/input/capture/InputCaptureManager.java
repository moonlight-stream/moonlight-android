package com.limelight.binding.input.capture;

import android.app.Activity;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.evdev.EvdevCaptureProvider;
import com.limelight.binding.input.evdev.EvdevListener;

public class InputCaptureManager {
    public static InputCaptureProvider getInputCaptureProvider(Activity activity, EvdevListener rootListener) {
        // Shield capture is preferred because it can capture when the cursor is over
        // the system UI. Android N native capture can only capture over views owned
        // by the application.
        if (ShieldCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using NVIDIA mouse capture extension");
            return new ShieldCaptureProvider(activity);
        }
        else if (AndroidNativePointerCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Android O+ native mouse capture");
            return new AndroidNativePointerCaptureProvider(activity.findViewById(R.id.surfaceView));
        }
        else if (EvdevCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Evdev mouse capture");
            return new EvdevCaptureProvider(activity, rootListener);
        }
        else if (AndroidPointerIconCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Android N+ pointer hiding");
            return new AndroidPointerIconCaptureProvider(activity);
        }
        else {
            LimeLog.info("Mouse capture not available");
            return new NullCaptureProvider();
        }
    }
}
