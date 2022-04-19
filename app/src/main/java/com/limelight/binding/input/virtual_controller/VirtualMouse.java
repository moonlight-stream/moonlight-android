/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.limelight.binding.input.touch.TouchContext;

/**
 * This is a copy of the full-screen touchpad that lives under a VirtualController
 *
 */
public class VirtualMouse extends View {

    protected VirtualController virtualController;
    private static final int THREE_FINGER_TAP_THRESHOLD = 300;
    private final Context context;
    private long threeFingerDownTime = 0;

    // For the VirtualMouse object, TouchContext needs to be passed from a different function
    public TouchContext[] touchContextMap;

    public VirtualMouse(VirtualController controller, Context context) {
        super(context);

        this.virtualController = controller;
        this.context = context;
    }
    private TouchContext getTouchContext(int actionIndex)
    {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        }
        else {
            return null;
        }
    }
    public void showKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            int actionIndex = event.getActionIndex();

            int eventX = (int)event.getX(actionIndex);
            int eventY = (int)event.getY(actionIndex);

            // Special handling for 3 finger gesture
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN &&
                    event.getPointerCount() == 3) {
                // Three fingers down
                threeFingerDownTime = SystemClock.uptimeMillis();

                // Cancel the first and second touches to avoid
                // erroneous events
                for (TouchContext aTouchContext : touchContextMap) {
                    aTouchContext.cancelTouch();
                }

                return true;
            }

            // Unfortunately, there is currently no way to get the touch context
            TouchContext context = getTouchContext(actionIndex);
            if (context == null) {
                return true;
            }

            switch (event.getActionMasked())
            {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount());
                    }
                    context.touchDownEvent(eventX, eventY, true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    if (event.getPointerCount() == 1) {
                        // All fingers up
                        if (SystemClock.uptimeMillis() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                            // This is a 3 finger tap to bring up the keyboard
                            showKeyboard();
                            return true;
                        }
                    }
                    context.touchUpEvent(eventX, eventY);
                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount() - 1);
                    }
                    /*
                    if (actionIndex == 0 && event.getPointerCount() > 1 && !context.isCancelled()) {
                        // The original secondary touch now becomes primary
                        context.touchDownEvent((int)event.getX(1), (int)event.getY(1), false);
                    }*/
                    break;
                case MotionEvent.ACTION_MOVE:
                    // ACTION_MOVE is special because it always has actionIndex == 0
                    // We'll call the move handlers for all indexes manually

                    // First process the historical events
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        (int)event.getHistoricalX(aTouchContextMap.getActionIndex(), i),
                                        (int)event.getHistoricalY(aTouchContextMap.getActionIndex(), i));
                            }
                        }
                    }

                    // Now process the current values
                    for (TouchContext aTouchContextMap : touchContextMap) {
                        if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                        {
                            aTouchContextMap.touchMoveEvent(
                                    (int)event.getX(aTouchContextMap.getActionIndex()),
                                    (int)event.getY(aTouchContextMap.getActionIndex()));
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                        aTouchContext.setPointerCount(0);
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }
}
