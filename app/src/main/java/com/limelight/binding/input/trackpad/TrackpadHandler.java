package com.limelight.binding.input.trackpad;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.trackpad.Core.TrackpadFramework;
import com.limelight.binding.input.trackpad.Core.TrackpadListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TrackpadHandler implements TrackpadListener {

    private final KeyboardTranslator keyboardTranslator = new KeyboardTranslator();

    private final NvConnection conn;
    private final float sense;

    // Bring the Core Class
    private final TrackpadFramework framework;

    // You should call it from a class that requires trackpad input.
    public TrackpadHandler(NvConnection conn,float sense){
        framework = new TrackpadFramework(this);
        this.conn = conn;
        this.sense = sense;
    }

    // Send a Motion Event signal to this method
    public void trackPadHandler(MotionEvent motionEvent){
        framework.trackpadHandler(motionEvent);
    }

    private final FloatDataHandler dataHandler = new FloatDataHandler();

    // Call the returned value from the method below
    @Override
    public void mouseMove(float x, float y) {
        short X = dataHandler.floatToShort(0,x*sense);
        short Y = dataHandler.floatToShort(1,y*sense);
        if (X != 0 || Y != 0) conn.sendMouseMove(X,Y);
    }

    @Override
    public void mouseButton(int direction, int button) {
        byte mButton = 0;
        switch (button){
            case TrackpadFramework.BUTTON_MIDDLE:
                mButton = MouseButtonPacket.BUTTON_MIDDLE;
                break;
            case TrackpadFramework.BUTTON_LEFT:
                mButton = MouseButtonPacket.BUTTON_LEFT;
                break;
            case TrackpadFramework.BUTTON_RIGHT:
                mButton = MouseButtonPacket.BUTTON_RIGHT;
                break;
            case TrackpadFramework.BUTTON_X1:
                mButton = MouseButtonPacket.BUTTON_X1;
                break;
            case TrackpadFramework.BUTTON_X2:
                mButton = MouseButtonPacket.BUTTON_X2;
                break;
        }
        switch (direction){
            case TrackpadFramework.KEY_DOWN:
                conn.sendMouseButtonDown(mButton);
                break;
            case TrackpadFramework.KEY_UP:
                conn.sendMouseButtonUp(mButton);
                break;
        }
    }

    @Override
    public void mouseScroll(float y) {
        conn.sendMouseHighResScroll((short) y);
    }

    @Override
    public void onKeyEvent(int direction, int key, byte modify) {
        // Keyboard Event
        byte mDirection = 0;
        byte mModify = 0;

        short translated = keyboardTranslator.translate(key,0);

        switch (direction){
            case TrackpadFramework.KEY_DOWN:
                mDirection = KeyboardPacket.KEY_DOWN;
                break;
            case TrackpadFramework.KEY_UP:
                mDirection = KeyboardPacket.KEY_UP;
                break;
        }
        switch (modify){
            case TrackpadFramework.MODIFIER_SHIFT:
                mModify = KeyboardPacket.MODIFIER_SHIFT;
                break;
            case TrackpadFramework.MODIFIER_CTRL:
                mModify = KeyboardPacket.MODIFIER_CTRL;
                break;
            case TrackpadFramework.MODIFIER_ALT:
                mModify = KeyboardPacket.MODIFIER_ALT;
                break;
            case TrackpadFramework.MODIFIER_WIN:
                mModify = (byte) 0x08;
                break;
        }
        conn.sendKeyboardInput(translated, mDirection,modify);
    }
}
