package com.limelight;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.NvController;
import com.limelight.nvstream.input.NvInputPacket;

import tv.ouya.console.api.OuyaController;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.MediaController;
import android.widget.VideoView;


public class Game extends Activity {
	private short inputMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	
	private NvConnection conn;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_game);
		
		OuyaController.init(this);
		
		conn = new NvConnection(Game.this.getIntent().getStringExtra("host"), Game.this);
		conn.start();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		switch (keyCode) {
		case OuyaController.BUTTON_MENU:
			inputMap |= NvInputPacket.BACK_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			inputMap |= NvInputPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			inputMap |= NvInputPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			inputMap |= NvInputPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			inputMap |= NvInputPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A:
			inputMap |= NvInputPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			inputMap |= NvInputPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			inputMap |= NvInputPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			inputMap |= NvInputPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			inputMap |= NvInputPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			inputMap |= NvInputPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			inputMap |= NvInputPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
			inputMap |= NvInputPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyDown(keyCode, event);
		}
		sendInputPacket();
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case OuyaController.BUTTON_MENU:
			inputMap &= ~NvInputPacket.BACK_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			inputMap &= ~NvInputPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			inputMap &= ~NvInputPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			inputMap &= ~NvInputPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			inputMap &= ~NvInputPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A:
			inputMap &= ~NvInputPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			inputMap &= ~NvInputPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			inputMap &= ~NvInputPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			inputMap &= ~NvInputPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			inputMap &= ~NvInputPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			inputMap &= ~NvInputPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			inputMap &= ~NvInputPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
			inputMap &= ~NvInputPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyUp(keyCode, event);
		}
		sendInputPacket();
		return true;	
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
		}
		
	    float L2 = event.getAxisValue(OuyaController.AXIS_L2);
	    float R2 = event.getAxisValue(OuyaController.AXIS_R2);
	    
	    leftTrigger = (byte)Math.round(L2 * 0xFF);
	    rightTrigger = (byte)Math.round(R2 * 0xFF);
	    
	    sendInputPacket();
	    
	    return true;
	}
	
	
	private void sendInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}
	
}
