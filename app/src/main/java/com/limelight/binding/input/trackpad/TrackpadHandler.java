package com.limelight.binding.input.trackpad;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.trackpad.Core.TrackpadFramework;
import com.limelight.binding.input.trackpad.Core.TrackpadListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TrackpadHandler implements TrackpadListener {

    private final KeyboardTranslator keyboardTranslator = new KeyboardTranslator();

    private final NvConnection conn;

    // Bring the Core Class
    private final TrackpadFramework framework;

    private final boolean absolute;
    private final View view;

    // You should call it from a class that requires trackpad input.
    public TrackpadHandler(NvConnection conn,boolean absolute,View view){
        framework = new TrackpadFramework(this);
        this.conn = conn;
        this.absolute = absolute;
        this.view = view;
    }

    // Send a Motion Event signal to this method
    public void trackPadHandler(MotionEvent motionEvent){
        framework.trackpadHandler(motionEvent);
    }

    private final FloatDataHandler dataHandler = new FloatDataHandler();

    // Call the returned value from the method below
    @Override
    public void mouseMove(float x, float y) {
        short X = dataHandler.floatToShort(0,x);
        short Y = dataHandler.floatToShort(1,y);
        if (X != 0 || Y != 0) {

            if(absolute) conn.sendMouseMoveAsMousePosition(X,Y,(short) view.getWidth(),(short) view.getHeight());
            else conn.sendMouseMove(X,Y);
        }
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
}
