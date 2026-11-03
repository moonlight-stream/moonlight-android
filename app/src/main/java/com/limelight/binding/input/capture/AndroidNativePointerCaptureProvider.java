package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;


// We extend AndroidPointerIconCaptureProvider because we want to also get the
// pointer icon hiding behavior over our stream view just in case pointer capture
// is unavailable on this system (ex: DeX, ChromeOS)
@TargetApi(Build.VERSION_CODES.O)
public class AndroidNativePointerCaptureProvider extends AndroidPointerIconCaptureProvider implements InputManager.InputDeviceListener {
    private static final long[] RECAPTURE_RETRY_DELAYS_MS = {250, 500, 1000, 2000, 3500};
    private static final long UNCAPTURED_RECAPTURE_INTERVAL_MS = 200;

    private final InputManager inputManager;
    private final View targetView;
    private final Handler recaptureHandler = new Handler(Looper.getMainLooper());
    private final Runnable recaptureRunnable = new Runnable() {
        @Override
        public void run() {
            requestCaptureIfNeeded();
        }
    };

    // Set once pointer capture has worked on this device. Until then we never drop
    // uncaptured mouse events, so devices without working capture keep absolute input.
    private boolean captureHasWorked;
    private long lastUncapturedRecaptureMs;

    public AndroidNativePointerCaptureProvider(Activity activity, View targetView) {
        super(activity, targetView);
        this.inputManager = activity.getSystemService(InputManager.class);
        this.targetView = targetView;
    }

    public static boolean isCaptureProviderSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    // We only capture the pointer if we have a compatible InputDevice
    // present. This is a workaround for an Android 12 regression causing
    // incorrect mouse input when using the SPen.
    // https://github.com/moonlight-stream/moonlight-android/issues/1030
    private boolean hasCaptureCompatibleInputDevice() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) {
                continue;
            }

            // Skip touchscreens when considering compatible capture devices.
            // Samsung devices on Android 12 will report a sec_touchpad device
            // with SOURCE_TOUCHSCREEN, SOURCE_KEYBOARD, and SOURCE_MOUSE.
            // Upon enabling pointer capture, that device will switch to
            // SOURCE_KEYBOARD and SOURCE_TOUCHPAD.
            // Only skip on non ChromeOS devices cause the ChromeOS pointer else
            // gets disabled removing relative mouse capabilities
            // on Chromebooks with touchscreens
            if (device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) && !targetView.getContext().getPackageManager().hasSystemFeature("org.chromium.arc.device_management")) {
                continue;
            }

            if (device.supportsSource(InputDevice.SOURCE_MOUSE) ||
                    device.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
                    device.supportsSource(InputDevice.SOURCE_TOUCHPAD)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void showCursor() {
        super.showCursor();

        // It is important to unregister the listener *before* releasing pointer capture,
        // because releasing pointer capture can cause an onInputDeviceChanged() callback
        // for devices with a touchpad (like a DS4 controller).
        inputManager.unregisterInputDeviceListener(this);
        targetView.releasePointerCapture();
    }

    @Override
    public void hideCursor() {
        super.hideCursor();

        // Listen for device events to enable/disable capture
        inputManager.registerInputDeviceListener(this, null);

        // Capture now if we have a capture-capable device
        if (hasCaptureCompatibleInputDevice()) {
            targetView.requestPointerCapture();
        }
    }

    // Should we currently own the pointer? Android pointer capture can't be held
    // while the cursor is visible, so that case is excluded.
    private boolean wantsCapture() {
        return isCapturing && !isCursorVisible;
    }

    private void requestCaptureIfNeeded() {
        if (wantsCapture() && targetView.hasWindowFocus() &&
                !targetView.hasPointerCapture() && hasCaptureCompatibleInputDevice()) {
            targetView.requestPointerCapture();
        }
    }

    // Retry capture a few times after focus comes back. A single attempt is not enough on
    // Meta Quest: the window gets focus back while the system menu is still closing, and
    // the request is lost (or capture is revoked again right after).
    private void scheduleRecaptureRetries() {
        recaptureHandler.removeCallbacks(recaptureRunnable);
        for (long delay : RECAPTURE_RETRY_DELAYS_MS) {
            recaptureHandler.postDelayed(recaptureRunnable, delay);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focusActive) {
        if (!focusActive) {
            recaptureHandler.removeCallbacks(recaptureRunnable);
            return;
        }

        // NB: We have to check cursor visibility here because Android pointer capture
        // doesn't support capturing the cursor while it's visible. Enabling pointer
        // capture implicitly hides the cursor.
        if (!wantsCapture()) {
            return;
        }

        // Recapture the pointer if focus was regained. On Android Q,
        // we have to delay a bit before requesting capture because otherwise
        // we'll hit the "requestPointerCapture called for a window that has no focus"
        // error and it will not actually capture the cursor.
        scheduleRecaptureRetries();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        if (hasCapture) {
            captureHasWorked = true;
            recaptureHandler.removeCallbacks(recaptureRunnable);
        }
        else if (wantsCapture() && targetView.hasWindowFocus()) {
            // Capture was taken away while we still have focus (seen on Meta Quest after
            // the system menu closes). Try to take it back.
            scheduleRecaptureRetries();
        }
    }

    @Override
    public boolean onAbsoluteMouseEvent(MotionEvent event) {
        // Only physical mice. Touchscreens, styluses and touchpads keep their normal handling.
        if (!wantsCapture() || event.getSource() != InputDevice.SOURCE_MOUSE ||
                event.getPointerCount() < 1 || event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) {
            return false;
        }

        if (targetView.hasPointerCapture()) {
            // With capture held, real mouse input arrives as SOURCE_MOUSE_RELATIVE. Meta Quest
            // still sends hover enter/exit at the frozen system cursor position; forwarding those
            // makes the remote cursor snap back to that spot over and over.
            return true;
        }

        // The mouse is moving the system cursor instead of being captured by us.
        // Ask for capture again (rate limited, since this runs for every mouse event).
        long now = SystemClock.uptimeMillis();
        if (now - lastUncapturedRecaptureMs >= UNCAPTURED_RECAPTURE_INTERVAL_MS) {
            lastUncapturedRecaptureMs = now;
            if (hasCaptureCompatibleInputDevice()) {
                targetView.requestPointerCapture();
            }
        }

        // Drop the absolute position while we recapture, otherwise the remote cursor snaps
        // back to wherever the system cursor is sitting. Only do this on devices where capture
        // has worked before, so systems without pointer capture (DeX, ChromeOS) still get
        // absolute mouse input.
        return captureHasWorked;
    }

    @Override
    public void destroy() {
        recaptureHandler.removeCallbacks(recaptureRunnable);
        super.destroy();
    }

    @Override
    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        // SOURCE_MOUSE_RELATIVE is how SOURCE_MOUSE appears when our view has pointer capture.
        // SOURCE_TOUCHPAD will have relative axes populated iff our view has pointer capture.
        // See https://developer.android.com/reference/android/view/View#requestPointerCapture()
        int eventSource = event.getSource();
        boolean relative = (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
                (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture());
        if (relative) {
            captureHasWorked = true;
        }
        return relative;
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

    @Override
    public void onInputDeviceAdded(int deviceId) {
        // Check if we've added a capture-compatible device
        if (!targetView.hasPointerCapture() && hasCaptureCompatibleInputDevice()) {
            targetView.requestPointerCapture();
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        // Check if the capture-compatible device was removed
        if (targetView.hasPointerCapture() && !hasCaptureCompatibleInputDevice()) {
            targetView.releasePointerCapture();
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        // Emulating a remove+add should be sufficient for our purposes.
        //
        // Note: This callback must be handled carefully because it can happen as a result of
        // calling requestPointerCapture(). This can cause trackpad devices to gain SOURCE_MOUSE_RELATIVE
        // and re-enter this callback.
        onInputDeviceRemoved(deviceId);
        onInputDeviceAdded(deviceId);
    }
}
