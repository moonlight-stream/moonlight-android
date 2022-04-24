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
 * Dummy 'button' that redirects input to its context's onTouchEvent
 */
public class VirtualMouse extends View {

    private final Context context;

    public VirtualMouse(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Hand control over to Game.onTouchEvent() to handle mouse movement
        return ( (Activity) context).onTouchEvent(event);
    }
}
