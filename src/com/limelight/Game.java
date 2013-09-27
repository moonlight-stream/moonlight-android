package com.limelight;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.NvControllerPacket;

import tv.ouya.console.api.OuyaController;
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
		
		OuyaController.init(this);
		
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
		case OuyaController.BUTTON_MENU:
			System.out.println("Menu Pressed");
			inputMap |= NvControllerPacket.BACK_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			inputMap |= NvControllerPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			inputMap |= NvControllerPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			inputMap |= NvControllerPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			inputMap |= NvControllerPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A:
			inputMap |= NvControllerPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			inputMap |= NvControllerPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			inputMap |= NvControllerPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			inputMap |= NvControllerPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			inputMap |= NvControllerPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			inputMap |= NvControllerPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			inputMap |= NvControllerPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
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
		case OuyaController.BUTTON_MENU:
			inputMap &= ~NvControllerPacket.BACK_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			inputMap &= ~NvControllerPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			inputMap &= ~NvControllerPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			inputMap &= ~NvControllerPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			inputMap &= ~NvControllerPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A:
			inputMap &= ~NvControllerPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			inputMap &= ~NvControllerPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			inputMap &= ~NvControllerPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			inputMap &= ~NvControllerPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			inputMap &= ~NvControllerPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			inputMap &= ~NvControllerPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			inputMap &= ~NvControllerPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
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
	    if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
			//Get all the axis for the event
		    float LS_X = event.getAxisValue(OuyaController.AXIS_LS_X);
		    float LS_Y = event.getAxisValue(OuyaController.AXIS_LS_Y);
		    float RS_X = event.getAxisValue(OuyaController.AXIS_RS_X);
		    float RS_Y = event.getAxisValue(OuyaController.AXIS_RS_Y);
	    	
		    if (LS_X * LS_X + LS_Y * LS_Y < OuyaController.STICK_DEADZONE * OuyaController.STICK_DEADZONE) {
		    	LS_X = LS_Y = 0.0f;
		    }
		    
		    if (RS_X * RS_X + RS_Y * RS_Y < OuyaController.STICK_DEADZONE * OuyaController.STICK_DEADZONE) {
		    	RS_X = RS_Y = 0.0f;
		    }
		    
		    leftStickX = (short)Math.round(LS_X * 0x7FFF);
		    leftStickY = (short)Math.round(-LS_Y * 0x7FFF);
		    
		    rightStickX = (short)Math.round(RS_X * 0x7FFF);
		    rightStickY = (short)Math.round(-RS_Y * 0x7FFF);
		    
		    float L2 = event.getAxisValue(OuyaController.AXIS_L2);
		    float R2 = event.getAxisValue(OuyaController.AXIS_R2);
		    
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
