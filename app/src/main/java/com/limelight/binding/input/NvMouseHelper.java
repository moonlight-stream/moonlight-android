package com.limelight.binding.input;


import android.content.Context;
import android.hardware.input.InputManager;
import android.view.MotionEvent;

import com.limelight.LimeLog;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// NVIDIA extended the Android input APIs with support for using an attached mouse in relative
// mode without having to grab the input device (which requires root). The data comes in the form
// of new AXIS_RELATIVE_X and AXIS_RELATIVE_Y constants in the mouse's MotionEvent objects and
// a new function, InputManager.setCursorVisibility(), that allows the cursor to be hidden.
//
// http://docs.nvidia.com/gameworks/index.html#technologies/mobile/game_controller_handling_mouse.htm

public class NvMouseHelper {
    private static boolean nvExtensionSupported;
    private static Method methodSetCursorVisibility;
    private static int AXIS_RELATIVE_X;
    private static int AXIS_RELATIVE_Y;

    static {
        try {
            methodSetCursorVisibility = InputManager.class.getMethod("setCursorVisibility", boolean.class);

            Field fieldRelX = MotionEvent.class.getField("AXIS_RELATIVE_X");
            Field fieldRelY = MotionEvent.class.getField("AXIS_RELATIVE_Y");

            AXIS_RELATIVE_X = (Integer) fieldRelX.get(null);
            AXIS_RELATIVE_Y = (Integer) fieldRelY.get(null);

            nvExtensionSupported = true;
        } catch (Exception e) {
            LimeLog.info("NvMouseHelper not supported");
            nvExtensionSupported = false;
        }
    }

    public static boolean setCursorVisibility(Context context, boolean visible) {
        if (!nvExtensionSupported) {
            return false;
        }

        try {
            methodSetCursorVisibility.invoke(context.getSystemService(Context.INPUT_SERVICE), visible);
            return true;
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean eventHasRelativeMouseAxes(MotionEvent event) {
        if (!nvExtensionSupported) {
            return false;
        }
        return event.getAxisValue(AXIS_RELATIVE_X) != 0 ||
                event.getAxisValue(AXIS_RELATIVE_Y) != 0;
    }

    public static float getRelativeAxisX(MotionEvent event) {
        return event.getAxisValue(AXIS_RELATIVE_X);
    }

    public static float getRelativeAxisY(MotionEvent event) {
        return event.getAxisValue(AXIS_RELATIVE_Y);
    }
}
