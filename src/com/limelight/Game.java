package com.limelight;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.NvControllerPacket;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;


public class Game extends Activity {
	private short inputMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;
	
	private NvConnection conn;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.activity_game);
				
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		conn = new NvConnection(Game.this.getIntent().getStringExtra("host"), Game.this, sv.getHolder().getSurface());
		conn.start();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		System.exit(0);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap |= NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap |= NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap |= NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap |= NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap |= NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap |= NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap |= NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap |= NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap |= NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap |= NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap |= NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap |= NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap |= NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap |= NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyDown(keyCode, event);
		}
		sendControllerInputPacket();
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap &= ~NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap &= ~NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap &= ~NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap &= ~NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap &= ~NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap &= ~NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap &= ~NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap &= ~NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap &= ~NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap &= ~NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap &= ~NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap &= ~NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap &= ~NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap &= ~NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyUp(keyCode, event);
		}
		sendControllerInputPacket();
		return true;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{
			switch (event.getActionMasked())
			{
			case MotionEvent.ACTION_DOWN:
				conn.sendMouseButtonDown();
				break;
			case MotionEvent.ACTION_UP:
				conn.sendMouseButtonUp();
				break;
			default:
				return super.onTouchEvent(event);
			}
			return true;
		}
		return super.onTouchEvent(event);
	}
	
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		InputDevice dev = event.getDevice();
		
		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}
		
	    if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
		    float LS_X = event.getAxisValue(MotionEvent.AXIS_X);
		    float LS_Y = event.getAxisValue(MotionEvent.AXIS_Y);
		    
		    float RS_X, RS_Y, L2, R2;
		    
		    InputDevice.MotionRange l2Range = dev.getMotionRange(MotionEvent.AXIS_LTRIGGER);
		    InputDevice.MotionRange r2Range = dev.getMotionRange(MotionEvent.AXIS_RTRIGGER);
		    if (l2Range != null && r2Range != null)
		    {
		    	// Ouya controller
		    	RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
		    	RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
		    	L2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
		    	R2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
		    }
		    else
		    {
		    	// Xbox controller
		    	RS_X = event.getAxisValue(MotionEvent.AXIS_RX);
		    	RS_Y = event.getAxisValue(MotionEvent.AXIS_RY);
		    	L2 = (event.getAxisValue(MotionEvent.AXIS_Z) + 1) / 2;
		    	R2 = (event.getAxisValue(MotionEvent.AXIS_RZ) + 1) / 2;
		    }
	    	
		    leftStickX = (short)Math.round(LS_X * 0x7FFF);
		    leftStickY = (short)Math.round(-LS_Y * 0x7FFF);
		    
		    rightStickX = (short)Math.round(RS_X * 0x7FFF);
		    rightStickY = (short)Math.round(-RS_Y * 0x7FFF);
		    
		    leftTrigger = (byte)Math.round(L2 * 0xFF);
		    rightTrigger = (byte)Math.round(R2 * 0xFF);
		    
		    sendControllerInputPacket();
		    return true;
		}
	    else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{
			int eventX = (int)event.getX();
			int eventY = (int)event.getY();
			
			// Send a mouse move update (if neccessary)
			updateMousePosition(eventX, eventY);
			return true;
		}
	    
	    return super.onGenericMotionEvent(event);
	}
	
	private void updateMousePosition(int eventX, int eventY) {
		// Send a mouse move if we already have a mouse location
		// and the mouse coordinates change
		if (lastMouseX != Integer.MIN_VALUE &&
			lastMouseY != Integer.MIN_VALUE &&
			!(lastMouseX == eventX && lastMouseY == eventY))
		{
			conn.sendMouseMove((short)(eventX - lastMouseX),
					(short)(eventY - lastMouseY));
		}
		
		// Update pointer location for delta calculation next time
		lastMouseX = eventX;
		lastMouseY = eventY;
	}
	
	private void sendControllerInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}
	
}
