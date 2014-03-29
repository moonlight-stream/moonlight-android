package com.limelight.binding.input;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;

public class ControllerHandler {
	private short inputMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	
	private NvConnection conn;
	
	public ControllerHandler(NvConnection conn) {
		this.conn = conn;
	}
	
	private void sendControllerInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}
	
	private static boolean isDualShock4(InputDevice dev) {
		return (dev.getMotionRange(MotionEvent.AXIS_RX) != null &&
				dev.getMotionRange(MotionEvent.AXIS_RY) != null &&
				dev.getMotionRange(MotionEvent.AXIS_HAT_X) != null &&
				dev.getMotionRange(MotionEvent.AXIS_HAT_Y) != null &&
				dev.getMotionRange(MotionEvent.AXIS_RZ) != null &&
				dev.getMotionRange(MotionEvent.AXIS_RY) != null);
	}
	
	private int handleRemapping(InputDevice dev, int keyCode) {
		if (isDualShock4(dev)) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_BUTTON_Y:
				return KeyEvent.KEYCODE_BUTTON_L1;
				
			case KeyEvent.KEYCODE_BUTTON_Z:
				return KeyEvent.KEYCODE_BUTTON_R1;
				
			case KeyEvent.KEYCODE_BUTTON_C:
				return KeyEvent.KEYCODE_BUTTON_B;
				
			case KeyEvent.KEYCODE_BUTTON_X:
				return KeyEvent.KEYCODE_BUTTON_Y;
				
			case KeyEvent.KEYCODE_BUTTON_B:
				return KeyEvent.KEYCODE_BUTTON_A;
				
			case KeyEvent.KEYCODE_BUTTON_A:
				return KeyEvent.KEYCODE_BUTTON_X;
				
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				return KeyEvent.KEYCODE_BUTTON_THUMBL;
				
			case KeyEvent.KEYCODE_BUTTON_START:
				return KeyEvent.KEYCODE_BUTTON_THUMBR;
				
			case KeyEvent.KEYCODE_BUTTON_L2:
				return KeyEvent.KEYCODE_BUTTON_SELECT;
				
			case KeyEvent.KEYCODE_BUTTON_R2:
				return KeyEvent.KEYCODE_BUTTON_START;
				
			// These are duplicate trigger events
			case KeyEvent.KEYCODE_BUTTON_R1:
			case KeyEvent.KEYCODE_BUTTON_L1:
				return 0;
				
			// These are duplicate dpad events
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				return 0;
			}
		}
		
		return keyCode;
	}
	
	public boolean handleMotionEvent(MotionEvent event) {
		InputDevice dev = event.getDevice();
		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}
		
		float LS_X = event.getAxisValue(MotionEvent.AXIS_X);
		float LS_Y = event.getAxisValue(MotionEvent.AXIS_Y);

		float RS_X, RS_Y, L2, R2;

		InputDevice.MotionRange leftTriggerRange = dev.getMotionRange(MotionEvent.AXIS_LTRIGGER);
		InputDevice.MotionRange rightTriggerRange = dev.getMotionRange(MotionEvent.AXIS_RTRIGGER);
		if (leftTriggerRange != null && rightTriggerRange != null)
		{
			// Ouya controller
			L2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
			R2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
			RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
			RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
		}
		else
		{
			InputDevice.MotionRange brakeRange = dev.getMotionRange(MotionEvent.AXIS_BRAKE);
			InputDevice.MotionRange gasRange = dev.getMotionRange(MotionEvent.AXIS_GAS);
			InputDevice.MotionRange rxRange = dev.getMotionRange(MotionEvent.AXIS_RX);
			InputDevice.MotionRange ryRange = dev.getMotionRange(MotionEvent.AXIS_RY);
			if (brakeRange != null && gasRange != null)
			{
				// Moga controller
				RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
				RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
				L2 = event.getAxisValue(MotionEvent.AXIS_BRAKE);
				R2 = event.getAxisValue(MotionEvent.AXIS_GAS);
			}
			else if (rxRange != null && ryRange != null)
			{
				// DS4 controller
				RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
				RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
				L2 = (event.getAxisValue(MotionEvent.AXIS_RX) + 1) / 2;
				R2 = (event.getAxisValue(MotionEvent.AXIS_RY) + 1) / 2;
			}
			else
			{
				// Xbox controller
				RS_X = event.getAxisValue(MotionEvent.AXIS_RX);
				RS_Y = event.getAxisValue(MotionEvent.AXIS_RY);
				L2 = (event.getAxisValue(MotionEvent.AXIS_Z) + 1) / 2;
				R2 = (event.getAxisValue(MotionEvent.AXIS_RZ) + 1) / 2;
			}
		}

		InputDevice.MotionRange hatXRange = dev.getMotionRange(MotionEvent.AXIS_HAT_X);
		InputDevice.MotionRange hatYRange = dev.getMotionRange(MotionEvent.AXIS_HAT_Y);
		if (hatXRange != null && hatYRange != null)
		{
			// Xbox and DS4 D-pad
			float hatX, hatY;

			hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
			hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

			inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
			inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
			if (hatX < -0.5) {
				inputMap |= ControllerPacket.LEFT_FLAG;
			}
			if (hatX > 0.5) {
				inputMap |= ControllerPacket.RIGHT_FLAG;
			}
			if (hatY < -0.5) {
				inputMap |= ControllerPacket.UP_FLAG;
			}
			if (hatY > 0.5) {
				inputMap |= ControllerPacket.DOWN_FLAG;
			}
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
	
	public boolean handleButtonUp(int keyCode, KeyEvent event) {
		InputDevice dev = event.getDevice();
		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}
		
		keyCode = handleRemapping(dev, keyCode);
		if (keyCode == 0) {
			return true;
		}
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_MODE:
			inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap &= ~ControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap &= ~ControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap &= ~ControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap &= ~ControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap &= ~ControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap &= ~ControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap &= ~ControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap &= ~ControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap &= ~ControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap &= ~ControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap &= ~ControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap &= ~ControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap &= ~ControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap &= ~ControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return false;
		}
		
		// If one of the two is up, the special button comes up too
		if ((inputMap & ControllerPacket.BACK_FLAG) == 0 ||
			(inputMap & ControllerPacket.PLAY_FLAG) == 0)
		{
			inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
		}
		
		sendControllerInputPacket();
		return true;
	}
	
	public boolean handleButtonDown(int keyCode, KeyEvent event) {
		InputDevice dev = event.getDevice();
		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}
		
		keyCode = handleRemapping(dev, keyCode);
		if (keyCode == 0) {
			return true;
		}
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_MODE:
			inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap |= ControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap |= ControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap |= ControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap |= ControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap |= ControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap |= ControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap |= ControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap |= ControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap |= ControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap |= ControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap |= ControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap |= ControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap |= ControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap |= ControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return false;
		}
		
		// We detect back+start as the special button combo
		if ((inputMap & ControllerPacket.BACK_FLAG) != 0 &&
			(inputMap & ControllerPacket.PLAY_FLAG) != 0)
		{
			inputMap &= ~(ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG);
			inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
		}
		
		sendControllerInputPacket();
		return true;
	}
}
