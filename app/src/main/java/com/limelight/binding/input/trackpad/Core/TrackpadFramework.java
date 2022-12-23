package com.limelight.binding.input.trackpad.Core;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Timer;
import java.util.TimerTask;

public class TrackpadFramework {

    private static final String TAG = "TrackpadFramework";

    private final TrackpadListener trackpadListener;

    public static final int KEY_DOWN = KeyEvent.ACTION_DOWN;
    public static final int KEY_UP = KeyEvent.ACTION_UP;
    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int BUTTON_X1 = 3;
    public static final int BUTTON_X2 = 4;

    private static final short autoMoveWait = 500;
    private static final short autoMoveInterval = 8;
    private static final short autoMoveSense = 3;

    private static final short moveToClickThreshold = 200;
    private static final short clickToDragThreshold = 200;
    private static final short dragToDoubleClickThreshold = 200;

    private final Handler buttonHandler = new Handler();

    private static short autoMoveSpaceThreshold = 100;

    private final int maxXThreshold;
    private final int minXThreshold;
    private final int maxYThreshold;
    private final int minYThreshold;

    public TrackpadFramework(TrackpadListener listener)
    {
        trackpadListener = listener;
        String deviceHead = Build.MODEL.substring(0,5);

        Log.i(TAG,"DeviceName : "+ deviceHead);

        if("SM-X9".equals(deviceHead)){ // Tab S8 Ultra
            maxXThreshold = 1763;
            minXThreshold = 0;
            maxYThreshold = 1599;
            minYThreshold = 528;
            autoMoveSpaceThreshold = 100;
        }else if("SM-T9".equals(deviceHead)||"SM-X8".equals(deviceHead)){ // Tab S7-S8 PLUS
            maxXThreshold = 1559;
            minXThreshold = 0;
            maxYThreshold = 1599;
            minYThreshold = 780;
            autoMoveSpaceThreshold = 100;
        }else {
            Log.i(TAG,"Not listed : AutoMove function is not available.");
            maxXThreshold = minXThreshold = maxYThreshold = minYThreshold = autoMoveSpaceThreshold = -1;
        }
    }

    public boolean trackpadHandler(MotionEvent motionEvent){
        pointerManager(motionEvent);
        switch (pointerMode){
            case 0:
                onePointer(motionEvent);
                break;
            case 1:
                doublePointer(motionEvent);
                break;
        }
        return true;
    }

    private float[][] axisManager(MotionEvent motionEvent){
        float[][] raw = new float[2][5];
        for(int i = 0; i< motionEvent.getPointerCount() && i < 5; i++){
            raw[MotionEvent.AXIS_X][i] = motionEvent.getY(i)-minXThreshold;
            raw[MotionEvent.AXIS_Y][i] = motionEvent.getX(i)-minYThreshold;
        }
        return raw;
    }

    // Time
    private final long[] actionDownTime = new long[4];
    private final long[] actionUpTime = new long[4];

    private int pointerMode=0;

    private void pointerManager(MotionEvent motionEvent){
        long upTime = motionEvent.getEventTime();
        int pointerIndex = motionEvent.getPointerId(motionEvent.getActionIndex());
        int pointerCount = motionEvent.getPointerCount();

        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:

                recodeFirstHistorical(motionEvent);
                actionDownTime[pointerIndex] = upTime;

                pointerMode = pointerCount-1;
                if (pointerMode == 1) { // One -> Two
                    resetOnePointer();
                }

                break;
            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                break;
            case MotionEvent.ACTION_MOVE:
                getRelative(motionEvent);
                break;

            case MotionEvent.ACTION_UP:
                if (pointerMode == 1) {
                    resetDoublePointer();
                }

                pointerMode = 0;
                actionUpTime[pointerIndex] = upTime;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (pointerMode == 1) {  // Double -> Reset
                    resetDoublePointer();
                }

                pointerMode = -1; // Block
                isRight = false;
                actionUpTime[pointerIndex] = upTime;
                break;

            default:
                Log.d(TAG,"New Action!! : "+motionEvent.getAction());
                break;
        }
    }

    private final float[] firstX = new float[4];
    private final float[] firstY = new float[4];
    private final float[] historicalAxisX = new float[4];
    private final float[] historicalAxisY = new float[4];
    private final float[] lastX = new float[4];
    private final float[] lastY = new float[4];

    private final float[] axis_Relative_X = new float[4];
    private final float[] axis_Relative_Y = new float[4];

    private void recodeFirstHistorical(MotionEvent motionEvent){
        float[] rawX = axisManager(motionEvent)[MotionEvent.AXIS_X];
        float[] rawY = axisManager(motionEvent)[MotionEvent.AXIS_Y];

        for(int i=0;i<motionEvent.getPointerCount();i++){
            firstX[i] = rawX[i];
            firstY[i] = rawY[i];
            lastX[i] = historicalAxisX[i];
            lastY[i] = historicalAxisY[i];
            historicalAxisX[i] = rawX[i];
            historicalAxisY[i] = rawY[i];
            axis_Relative_X[i] = 0;
            axis_Relative_Y[i] = 0;
        }
    }

    // Relative
    private void getRelative(MotionEvent motionEvent){
        float[] rawX = axisManager(motionEvent)[MotionEvent.AXIS_X];
        float[] rawY = axisManager(motionEvent)[MotionEvent.AXIS_Y];

        for(int i = 0; i< motionEvent.getPointerCount(); i++){
            axis_Relative_X[i] = (rawX[i]-historicalAxisX[i]);
            historicalAxisX[i] = rawX[i];
            axis_Relative_Y[i] = (historicalAxisY[i]-rawY[i]);
            historicalAxisY[i] = rawY[i];
        }
    }

    // onePointer
    private boolean isDrag = false;
    private boolean single_Queue = false;

    private Timer autoMoveTimer;
    private final Handler clickHandler = new Handler();

    private boolean isRight =false;

    private int upDown = 0;
    private int leftRight = 0;

    private void onePointer(MotionEvent motionEvent){
        long eventTime = motionEvent.getEventTime();
        long actionUpTime = this.actionUpTime[0];
        long actionDownTime = this.actionDownTime[0];
        float x = axisManager(motionEvent)[MotionEvent.AXIS_X][0];
        float y = axisManager(motionEvent)[MotionEvent.AXIS_Y][0];
        float firstX = this.firstX[0];
        float firstY = this.firstY[0];
        float lastX = this.lastX[0];
        float lastY = this.lastY[0];
        float RelativeX = axis_Relative_X[0];
        float RelativeY = axis_Relative_Y[0];

        switch (motionEvent.getActionMasked()){
            // Touch Action
            // Move -> Click -> Drag -> Double Click
            case MotionEvent.ACTION_DOWN:
                // When there is an action UP event before
                if (single_Queue && !distanceExceeds((int)(lastX-x), (int)(lastY-y),150)) {
                    if ((eventTime - actionUpTime) < clickToDragThreshold) { // Click -> Drag
                        isDrag = true;
                        clickHandler.removeCallbacksAndMessages(null);
                    }
                }
                break;

            // Button Action
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    switch (motionEvent.getActionButton()){
                        case MotionEvent.BUTTON_PRIMARY:
                            buttonHandler.postDelayed(() -> trackpadListener.mouseButton(KEY_DOWN,BUTTON_LEFT),25);
                            break;
                        case MotionEvent.BUTTON_SECONDARY:
                            buttonHandler.postDelayed(() -> trackpadListener.mouseButton(KEY_DOWN,BUTTON_RIGHT),100);
                            break;
                    }
                }

                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Handler handler = new Handler();
                    switch (motionEvent.getActionButton()){
                        case MotionEvent.BUTTON_PRIMARY:
                            handler.postDelayed(() -> trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT),100);
                            break;
                        case MotionEvent.BUTTON_SECONDARY:
                            handler.postDelayed(() -> trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT),100);
                            break;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                trackpadListener.mouseMove(RelativeX,RelativeY);

                if(isDrag){
                    int maxX = (maxXThreshold - minXThreshold) - autoMoveSpaceThreshold;
                    int minX = autoMoveSpaceThreshold;
                    int maxY = (maxYThreshold - minYThreshold) - autoMoveSpaceThreshold;
                    int minY = autoMoveSpaceThreshold;
                    // rawX : R : 0 ~ L : 1763  // rawY : U : 1592 ~ D:528
                    if((x < minX || x > maxX) || (y > maxY || y < minY)){
                        if((x < minX) || (x > maxX)){
                            if(x < minX) leftRight = -autoMoveSense;
                            if(x > maxX) leftRight = autoMoveSense;
                        }else leftRight = 0;

                        if((y>maxY)||(y<minY)){
                            if(y > maxY) upDown = -autoMoveSense;
                            if(y < minY) upDown = autoMoveSense;
                        }else upDown = 0;

                        if(autoMoveTimer == null){
                            autoMoveTimer = new Timer(true);
                            autoMoveTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    trackpadListener.mouseMove(leftRight,upDown);
                                }
                            }, autoMoveWait,autoMoveInterval);
                        }
                    }
                    else {
                        if(autoMoveTimer !=null){
                            autoMoveTimer.cancel();
                            autoMoveTimer = null;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (!single_Queue) {
                    if ((eventTime - actionDownTime) < moveToClickThreshold) { // Move -> Click
                        if(!distanceExceeds((int)(firstX - x), (int)(firstY - y),25)){
                            single_Queue = true;
                            trackpadListener.mouseButton(KEY_DOWN,BUTTON_LEFT);

                            clickHandler.postDelayed(() -> {
                                trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
                                single_Queue = false;
                            },clickToDragThreshold);
                        }
                    }
                }

                // When there is an action Down event before
                else if (isDrag){
                    trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
                    if ((eventTime - actionDownTime) < dragToDoubleClickThreshold) { // Drag -> Double Click
                        trackpadListener.mouseButton(KEY_DOWN,BUTTON_LEFT);
                        trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
                    }
                    single_Queue = false;
                    isDrag =false;
                }

                if(autoMoveTimer !=null){
                    autoMoveTimer.cancel();
                    autoMoveTimer = null;
                }

                break;

            default:
                break;
        }
    }

    // DoublePointer
    private boolean isMiddle =false;
    private void doublePointer(MotionEvent motionEvent){
        float doubleRelativeX = (axis_Relative_X[0]+axis_Relative_X[1])/2;
        float doubleRelativeY = (axis_Relative_Y[0]+axis_Relative_Y[1])/2;

        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_POINTER_DOWN:
                isRight = true; // Tap Right
                Handler handler = new Handler();
                handler.postDelayed(() -> isRight = false,100);
                break;

            case MotionEvent.ACTION_BUTTON_PRESS:
                isMiddle = true;
                trackpadListener.mouseButton(KEY_DOWN,BUTTON_MIDDLE);
                break;

            case MotionEvent.ACTION_MOVE:
                if(isMiddle){
                    trackpadListener.mouseMove(doubleRelativeX,doubleRelativeY);
                } else { // Vertical Scroll Mode
                    trackpadListener.mouseScroll((doubleRelativeY * 5));
                }

        }
    }

    private boolean distanceExceeds(int deltaX, int deltaY, double limit) {
        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2)) > limit;
    }

    private void resetOnePointer(){
        isDrag = false;
        single_Queue = false;

        if(autoMoveTimer != null){
            autoMoveTimer.cancel();
            autoMoveTimer = null;
        }

        clickHandler.removeCallbacksAndMessages(null);
        buttonHandler.removeCallbacksAndMessages(null);

        trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
        trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT);
    }

    private void resetDoublePointer(){
        buttonHandler.removeCallbacksAndMessages(null);

        if(isMiddle){
            trackpadListener.mouseButton(KEY_UP,BUTTON_MIDDLE);
            isMiddle =false;
        }

        if(isRight){
            clickHandler.removeCallbacksAndMessages(null);
            trackpadListener.mouseButton(KEY_DOWN,BUTTON_RIGHT);
            trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT);
        }
    }
}
