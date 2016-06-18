package com.limelight.binding.input.capture;

import android.app.Activity;

import com.limelight.LimeLog;
import com.limelight.binding.input.evdev.EvdevCaptureProvider;
import com.limelight.binding.input.evdev.EvdevListener;

public class InputCaptureManager {
    public static InputCaptureProvider getInputCaptureProvider(Activity activity, EvdevListener rootListener) {
        if (AndroidCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Android N+ native mouse capture");
            return new AndroidCaptureProvider(activity);
        }
        else if (ShieldCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using NVIDIA mouse capture extension");
            return new ShieldCaptureProvider(activity);
        }
        else if (EvdevCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Evdev mouse capture");
            return new EvdevCaptureProvider(activity, rootListener);
        }
        else {
            LimeLog.info("Mouse capture not available");
            return new NullCaptureProvider();
        }
    }
}
