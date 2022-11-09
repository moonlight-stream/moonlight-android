package com.limelight.binding.input.trackpad.Core;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Timer;
import java.util.TimerTask;

public class TrackpadFramework {

    private static final String TAG = "TrackpadFrameWork";

    private final TrackpadListener trackpadListener;

    public static final int TYPE_DELTA = 1;
    public static final int TYPE_POSITION = 2;

    public static final int KEY_DOWN = KeyEvent.ACTION_DOWN;
    public static final int KEY_UP = KeyEvent.ACTION_UP;
    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int BUTTON_X1 = 3;
    public static final int BUTTON_X2 = 4;

    public static final byte MODIFIER_SHIFT = 0x01;
    public static final byte MODIFIER_CTRL = 0x02;
    public static final byte MODIFIER_ALT = 0x04;
    public static final byte MODIFIER_WIN = 0x08;

    public static String getVersion(){
        String Version;
        int majorVersion = 1;
        int minorVersion = 0;
        int revisions = 0;
        Version = majorVersion+"."+minorVersion+"."+revisions;
        return Version;
    }

    public static String directionToString(int direction){
        String sDirection = "";
        switch (direction){
            case KEY_DOWN:
                sDirection = "KEY_DOWN";
                break;
            case KEY_UP:
                sDirection = "KEY_UP";
                break;
        }

        return sDirection;
    }

    public static String buttonToString(int button){
        String sDirection = "";
        switch (button){
            case BUTTON_LEFT:
                sDirection = "BUTTON_LEFT";
                break;
            case BUTTON_RIGHT:
                sDirection = "BUTTON_RIGHT";
                break;
            case BUTTON_MIDDLE:
                sDirection = "BUTTON_MIDDLE";
                break;
            case BUTTON_X1:
                sDirection = "BUTTON_X1";
                break;
            case BUTTON_X2:
                sDirection = "BUTTON_X2";
                break;
        }
        return sDirection;
    }


    private static final short autoMoveWait = 500;
    private static final short autoMoveInterval = 8;
    private static final short autoMoveSense = 3;

    private static final short moveToClickThreshold = 200;
    private static final short clickToDragThreshold = 200;
    private static final short dragToDoubleClickThreshold = 200;

    private Timer buttonTimer;

    private static short autoMoveSpaceThreshold = 100;

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
            Log.i(TAG,"Not listed : Calibration is required to use AutoMove.");
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
            case 2:
                triplePointer(motionEvent);
                break;
        }
        return true;
    }

    private float[][] axisManager(MotionEvent motionEvent){
        float[][] raw = new float[2][5];
        for(int i = 0; i< motionEvent.getPointerCount(); i++){
            raw[0][i] = motionEvent.getRawY(i)-minXThreshold;
            raw[1][i] = motionEvent.getRawX(i)-minYThreshold;
        }
        return raw;
    }


    private int maxXThreshold;
    private int minXThreshold;
    private int maxYThreshold;
    private int minYThreshold;

    private void trackPadCalibration(MotionEvent motionEvent){
        float x = motionEvent.getRawY();
        float y = motionEvent.getRawX();
        maxXThreshold = minXThreshold = maxYThreshold = minYThreshold = 1000;
        if(maxXThreshold <x|| maxYThreshold <y|| minXThreshold >x|| minYThreshold >y){
            if(maxXThreshold <x) maxXThreshold = (int) x;
            if(maxYThreshold <y) maxYThreshold = (int) y;
            if(minXThreshold >x) minXThreshold = (int) x;
            if(minYThreshold >y) minYThreshold = (int) y;
        }
    }

    // Time
    private final long[] actionDownTime = new long[4];
    private final long[] actionUpTime = new long[4];

    private int pointerMode=0;

    private void pointerManager(MotionEvent motionEvent){
        long upTime = SystemClock.uptimeMillis();
        int pointerIndex = motionEvent.getPointerId(motionEvent.getActionIndex());
        int pointerCount = motionEvent.getPointerCount();

        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:

                recodeFirstHistorical(motionEvent);
                actionDownTime[pointerIndex] = upTime;

                pointerMode = pointerCount-1;
                switch (pointerMode){
                    case 1: // One -> Two
                        resetOnePointer();
                        break;
                    case 2: // Two -> Triple
                        isRight = false;
                        resetDoublePointer();
                        break;
                }

                break;
            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                break;
            case MotionEvent.ACTION_MOVE:
                getRelative(motionEvent);
                break;

            case MotionEvent.ACTION_UP:
                switch (pointerMode){
                    case 0:
                        break;
                    case 1:
                        resetDoublePointer();
                        break;
                    case 2:
                        isRight = false;
                        resetTriplePointer();
                        break;
                }

                pointerMode = 0;
                actionUpTime[pointerIndex] = upTime;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                switch (pointerMode){
                    case 1:  // Double -> Reset
                        resetDoublePointer();
                        break;
                    case 2: // Triple -> Reset
                        isRight = false;
                        resetTriplePointer();
                        break;
                }

                pointerMode = -1; // Block
                isRight = false;
                actionUpTime[pointerIndex] = upTime;
                break;

            default:
                System.out.println("New Action!! : "+MotionEvent.actionToString(motionEvent.getAction())+
                        " : "+motionEvent.getAction());
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
        float[] rawX = axisManager(motionEvent)[0];
        float[] rawY = axisManager(motionEvent)[1];

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
        float[] rawX = axisManager(motionEvent)[0];
        float[] rawY = axisManager(motionEvent)[1];

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
    private Timer clickTimer;
    private boolean isRight =false;

    private int upDown = 0;
    private int leftRight = 0;

    private void onePointer(MotionEvent motionEvent){
        long eventTime = SystemClock.uptimeMillis();
        long actionUpTime = this.actionUpTime[0];
        long actionDownTime = this.actionDownTime[0];
        float x = axisManager(motionEvent)[0][0];
        float y = axisManager(motionEvent)[1][0];
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
                        if (clickTimer != null) { // TapClick Timer cancel
                            clickTimer.cancel();
                            clickTimer = null;
                        }
                    }
                }
                break;

            // Button Action
            case MotionEvent.ACTION_BUTTON_PRESS:
                switch (motionEvent.getActionButton()){
                    case MotionEvent.BUTTON_PRIMARY:
                        buttonTimer = new Timer(true);
                        buttonTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                trackpadListener.mouseButton(KEY_DOWN,BUTTON_LEFT);
                            }
                        },25);
                        clickTimer = new Timer(true);
                        break;
                    case MotionEvent.BUTTON_SECONDARY:
                        buttonTimer = new Timer(true);
                        buttonTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                trackpadListener.mouseButton(KEY_DOWN,BUTTON_RIGHT);
                            }
                        },100);

                        clickTimer = new Timer(true);
                        break;
                }

                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                switch (motionEvent.getActionButton()){
                    case MotionEvent.BUTTON_PRIMARY:
                        Timer t = new Timer(true);
                        t.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
                            }
                        },100);

                        break;
                    case MotionEvent.BUTTON_SECONDARY:
                        Timer ts = new Timer(true);
                        ts.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT);
                            }
                        },100);
                        break;
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
                            if(clickTimer == null){
                                clickTimer = new Timer(true);
                                clickTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
                                        single_Queue = false;
                                        clickTimer = null;
                                    }
                                }, clickToDragThreshold);
                            }
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
                System.out.println("New Action!! : "+MotionEvent.actionToString(motionEvent.getAction())+" : "+motionEvent.getAction());
                break;
        }
    }

    // DoublePointer
    private float StartPTP;
    private float historicalPTP;

    private boolean doubleOneTime = false;
    private short double_Mode = 0;

    private float doubleActionDownX;

    private float startHorizontalX;
    private short double_Mode_1 = 0;

    private boolean isMiddle =false;
    private void doublePointer(MotionEvent motionEvent){
        long eventTime = SystemClock.uptimeMillis();
        long actionDownTime = this.actionDownTime[1];
        float[] x = axisManager(motionEvent)[0];
        float[] y = axisManager(motionEvent)[1];
        float doubleX = (float) (x[0]+ x[1])/2;
        float doubleRelativeX = (float) (axis_Relative_X[0]+axis_Relative_X[1])/2;
        float doubleRelativeY = (float) (axis_Relative_Y[0]+axis_Relative_Y[1])/2;
        float pointToPoint = (float) Math.sqrt(Math.pow(x[0] - x[1], 2) + Math.pow(y[0] - y[1], 2));

        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_POINTER_DOWN:
                StartPTP = historicalPTP = pointToPoint;
                doubleActionDownX = doubleX;

                isRight = true; // Tap Right
                Timer t = new Timer(true);
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        isRight = false;
                    }
                }, 100);
                break;

            case MotionEvent.ACTION_BUTTON_PRESS:
                buttonTimer = new Timer(true);
                buttonTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        isMiddle = true;
                        trackpadListener.mouseButton(KEY_DOWN,BUTTON_MIDDLE);
                    }
                },100);
                break;

            case MotionEvent.ACTION_BUTTON_RELEASE:
                break;

            case MotionEvent.ACTION_MOVE:
                if(isMiddle){
                    trackpadListener.mouseMove(doubleRelativeX,doubleRelativeY);
                }
                else {
                    switch (double_Mode){ // Determine the trackpad mode
                        case 0: // Vertical Scroll Mode
                            trackpadListener.mouseScroll((doubleRelativeY * 5));

                            if (Math.abs(doubleX - doubleActionDownX) > 200) { // Vertical -> Horizontal Mode
                                isRight = false;
                                startHorizontalX = doubleX;
                                double_Mode = 1;

                            }

                            if (Math.abs(StartPTP - pointToPoint) > 200) { // Vertical -> Zoom in out Mode
                                isRight = false;
                                double_Mode = 2;
                            }

                            break;

                        case 1: // Horizontal Mode
                            switch (double_Mode_1){
                                case 0:
                                    if((eventTime - actionDownTime)> 100) {
                                        float speed = Math.abs(startHorizontalX - doubleX);
                                        if(speed < 50) double_Mode_1 = 1;
                                        else double_Mode_1 = 2;
                                    }
                                    break;
                                case 1: // Horizontal Scroll
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,(byte) 0);
                                    trackpadListener.mouseScroll((doubleRelativeX * 5));
                                    break;
                                case 2:
                                    if (!doubleOneTime){
                                        if (doubleRelativeX < 0) { // Forward
                                            trackpadListener.mouseButton(KEY_DOWN,BUTTON_X1);
                                            trackpadListener.mouseButton(KEY_UP,BUTTON_X1);
                                        } else { // Backward
                                            trackpadListener.mouseButton(KEY_DOWN,BUTTON_X2);
                                            trackpadListener.mouseButton(KEY_UP,BUTTON_X2);
                                        }
                                        doubleOneTime = true;
                                    }
                                    break;
                            }

                            break;

                        case 2: // Zoom in out
                            if(!doubleOneTime){
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_CTRL_LEFT,(byte)0);
                                doubleOneTime = true;
                            }
                            trackpadListener.mouseScroll((pointToPoint - historicalPTP));
                            break;
                    }
                    historicalPTP = pointToPoint;
                    break;
                }

            default:
                System.out.println("New Action!! : "+MotionEvent.actionToString(motionEvent.getAction())+
                        " : "+motionEvent.getAction());
                break;

        }
    }

    // triplePointer
    private float triple_StartRawX;
    private float triple_StartRawY;
    private boolean triple_OneTime = false;
    private short triple_Mode = 0;
    private short triple_UpDown = 0;

    private void triplePointer(MotionEvent motionEvent){

        float[] rawX = axisManager(motionEvent)[0];
        float[] rawY = axisManager(motionEvent)[1];

        float tripleX = (rawX[0]+rawX[1]+rawX[2])/3;
        float tripleY = (rawY[0]+rawY[1]+rawY[2])/3;

        if(motionEvent.getButtonState()==1||motionEvent.getButtonState()==2){
            triple_Mode = 3;
        }

        switch (motionEvent.getActionMasked()){

            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                triple_StartRawX = tripleX;
                triple_StartRawY = tripleY;
                break;

            case MotionEvent.ACTION_BUTTON_PRESS:
                triple_Mode = 3;
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                break;


            case MotionEvent.ACTION_MOVE:
                switch (triple_Mode){
                    case 0:// Determine the trackpad mode
                        if (Math.abs(tripleY - triple_StartRawY) > 100) { // UpDown
                            triple_Mode = 1;
                        }
                        if (Math.abs(tripleX - triple_StartRawX) > 100) { // Left Right
                            triple_Mode = 2;
                        }
                        break;
                    case 1:
                        if(!triple_OneTime){
                            trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_META_LEFT,(byte)0x08);
                            if (triple_UpDown == 0) {
                                if ((triple_StartRawY - tripleY) < 0) { // Up
                                    trackpadListener.onKeyEvent(KEY_DOWN, KeyEvent.KEYCODE_TAB, MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP, KeyEvent.KEYCODE_TAB, MODIFIER_WIN);
                                    triple_UpDown = 1;
                                } else { // Down
                                    trackpadListener.onKeyEvent(KEY_DOWN, KeyEvent.KEYCODE_D, MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP, KeyEvent.KEYCODE_D, MODIFIER_WIN);
                                    triple_UpDown = 2;
                                }
                                triple_StartRawY = tripleY;
                                trackpadListener.onKeyEvent(KEY_UP, KeyEvent.KEYCODE_META_LEFT, (byte) 0);
                            }
                            triple_OneTime = true;
                        }
                        switch (triple_UpDown){
                            case 1:
                                if ((triple_StartRawY - tripleY) > 100)  { // Down
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_META_LEFT,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_TAB,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_TAB,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_META_LEFT,(byte) 0);
                                    triple_UpDown = -1;
                                }
                                break;

                            case 2:
                                if ((triple_StartRawY - tripleY) < -100)  { //
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_META_LEFT,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_D,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_D,MODIFIER_WIN);
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_META_LEFT,(byte) 0);
                                    triple_UpDown = -1;
                                }
                                break;
                        }
                        break;

                    case 2: // App Change
                        if (Math.abs((tripleX - triple_StartRawX)) > 100) {
                            if (!triple_OneTime) {
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_ALT_LEFT,MODIFIER_ALT);
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_TAB,MODIFIER_ALT);
                                trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_TAB,MODIFIER_ALT);

                                triple_OneTime = true;
                            }
                            if ((tripleX - triple_StartRawX) < 0) {
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,MODIFIER_ALT);
                                trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_DPAD_LEFT,MODIFIER_ALT);
                            }
                            else {
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,MODIFIER_ALT);
                                trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_DPAD_RIGHT,MODIFIER_ALT);
                            }
                            triple_StartRawX = tripleX;
                        }
                        break;
                    case 3: // Desktop Change
                        if (Math.abs((tripleX - triple_StartRawX)) > 200) {
                            if (!triple_OneTime) {
                                trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_ALT_LEFT,MODIFIER_ALT);
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_META_LEFT,MODIFIER_WIN);
                                trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_CTRL_LEFT,(byte) (MODIFIER_WIN+MODIFIER_CTRL));
                                if ((tripleX - triple_StartRawX) < 0) {
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,(byte) (MODIFIER_WIN+MODIFIER_CTRL));
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_DPAD_RIGHT,(byte) 0);
                                }
                                else {
                                    trackpadListener.onKeyEvent(KEY_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,(byte) (MODIFIER_WIN+MODIFIER_CTRL));
                                    trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_DPAD_LEFT,(byte) 0);
                                }
                                triple_OneTime = true;
                            }
                        }
                        break;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                break;

            default:
                System.out.println("New Action!! : "+MotionEvent.actionToString(motionEvent.getAction())+
                        " : "+motionEvent.getAction());
                break;

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
        if(clickTimer != null){
            clickTimer.cancel();
            clickTimer = null;
        }

        if(buttonTimer != null){
            buttonTimer.cancel();
            buttonTimer = null;
        }

        trackpadListener.mouseButton(KEY_UP,BUTTON_LEFT);
        trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT);
    }

    private void resetDoublePointer(){
        StartPTP = historicalPTP = doubleActionDownX = startHorizontalX = 0;
        doubleOneTime = false;

        switch (double_Mode){
            case 1:
                if (double_Mode_1 == 1) {
                    trackpadListener.onKeyEvent(KEY_UP, KeyEvent.KEYCODE_SHIFT_LEFT, (byte) 0);
                }
                break;
            case 2:
                trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_CTRL_LEFT,(byte) 0);
                break;
        }

        double_Mode = double_Mode_1 = 0;

        if(buttonTimer!=null){
            buttonTimer.cancel();
            buttonTimer = null;
        }

        if(isMiddle){
            trackpadListener.mouseButton(KEY_UP,BUTTON_MIDDLE);
            isMiddle =false;
        }


        if(isRight){
            if(clickTimer!=null){
                clickTimer.cancel();
                clickTimer =null;
            }
            trackpadListener.mouseButton(KEY_DOWN,BUTTON_RIGHT);
            trackpadListener.mouseButton(KEY_UP,BUTTON_RIGHT);
        }
    }

    private void resetTriplePointer(){
        triple_StartRawX = triple_StartRawY = triple_Mode = triple_UpDown = 0;
        triple_OneTime = false;
        trackpadListener.onKeyEvent(KEY_UP,KeyEvent.KEYCODE_ALT_LEFT,(byte) 0);
    }
}
