/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
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
        // Hand control over to Game.onTouchEvent() to handle mouse movement
        return ( (Activity) context).onTouchEvent(event);
    }
}
