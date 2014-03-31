package com.limelight.binding.input;

import java.util.HashMap;

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
	
	private HashMap<String, ControllerMapping> mappings = new HashMap<String, ControllerMapping>();
	
	private NvConnection conn;
	
	public ControllerHandler(NvConnection conn) {
		this.conn = conn;
	}
	
	private ControllerMapping createMappingForDevice(InputDevice dev) {
		ControllerMapping mapping = new ControllerMapping();
		
		mapping.leftStickXAxis = MotionEvent.AXIS_X;
		mapping.leftStickYAxis = MotionEvent.AXIS_Y;
		
		InputDevice.MotionRange leftTriggerRange = dev.getMotionRange(MotionEvent.AXIS_LTRIGGER);
		InputDevice.MotionRange rightTriggerRange = dev.getMotionRange(MotionEvent.AXIS_RTRIGGER);
		InputDevice.MotionRange brakeRange = dev.getMotionRange(MotionEvent.AXIS_BRAKE);
		InputDevice.MotionRange gasRange = dev.getMotionRange(MotionEvent.AXIS_GAS);
		if (leftTriggerRange != null && rightTriggerRange != null)
		{
			// Some controllers use LTRIGGER and RTRIGGER (like Ouya)
			mapping.leftTriggerAxis = MotionEvent.AXIS_LTRIGGER;
			mapping.rightTriggerAxis = MotionEvent.AXIS_RTRIGGER;
		}
		else if (brakeRange != null && gasRange != null)
		{
			// Others use GAS and BRAKE (like Moga)
			mapping.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
			mapping.rightTriggerAxis = MotionEvent.AXIS_GAS;
		}
		else
		{
			InputDevice.MotionRange rxRange = dev.getMotionRange(MotionEvent.AXIS_RX);
			InputDevice.MotionRange ryRange = dev.getMotionRange(MotionEvent.AXIS_RY);
			if (rxRange != null && ryRange != null) {
				String devName = dev.getName();
				if (devName.contains("Xbox") || devName.contains("XBox") || devName.contains("X-Box")) {
					// Xbox controllers use RX and RY for right stick
					mapping.rightStickXAxis = MotionEvent.AXIS_RX;
					mapping.rightStickYAxis = MotionEvent.AXIS_RY;
					
					// Xbox controllers use Z and RZ for triggers
					mapping.leftTriggerAxis = MotionEvent.AXIS_Z;
					mapping.rightTriggerAxis = MotionEvent.AXIS_RZ;
					mapping.triggersIdleNegative = true;
				}
				else {
					// DS4 controller uses RX and RY for triggers
					mapping.leftTriggerAxis = MotionEvent.AXIS_RX;
					mapping.rightTriggerAxis = MotionEvent.AXIS_RY;
					mapping.triggersIdleNegative = true;
					
					mapping.isDualShock4 = true;
				}
			}
		}
		
		if (mapping.rightStickXAxis == -1 && mapping.rightStickYAxis == -1) {
			InputDevice.MotionRange zRange = dev.getMotionRange(MotionEvent.AXIS_Z);
			InputDevice.MotionRange rzRange = dev.getMotionRange(MotionEvent.AXIS_RZ);
			
			// Most other controllers use Z and RZ for the right stick
			if (zRange != null && rzRange != null) {
				mapping.rightStickXAxis = MotionEvent.AXIS_Z;
				mapping.rightStickYAxis = MotionEvent.AXIS_RZ;
			}
			else {
				InputDevice.MotionRange rxRange = dev.getMotionRange(MotionEvent.AXIS_RX);
				InputDevice.MotionRange ryRange = dev.getMotionRange(MotionEvent.AXIS_RY);
				
				// Try RX and RY now
				if (rxRange != null && ryRange != null) {
					mapping.rightStickXAxis = MotionEvent.AXIS_RX;
					mapping.rightStickYAxis = MotionEvent.AXIS_RY;
				}
			}
		}
		
		// Some devices have "hats" for d-pads
		InputDevice.MotionRange hatXRange = dev.getMotionRange(MotionEvent.AXIS_HAT_X);
		InputDevice.MotionRange hatYRange = dev.getMotionRange(MotionEvent.AXIS_HAT_Y);
		if (hatXRange != null && hatYRange != null) {
			mapping.hatXAxis = MotionEvent.AXIS_HAT_X;
			mapping.hatYAxis = MotionEvent.AXIS_HAT_Y;
			
			mapping.hatXDeadzone = hatXRange.getFlat();
			mapping.hatYDeadzone = hatYRange.getFlat();
		}
		
		if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
			InputDevice.MotionRange lsXRange = dev.getMotionRange(mapping.leftStickXAxis);
			InputDevice.MotionRange lsYRange = dev.getMotionRange(mapping.leftStickYAxis);
			if (lsXRange != null) {
				mapping.leftStickXAxisDeadzone = lsXRange.getFlat();
			}
			if (lsYRange != null) {
				mapping.leftStickYAxisDeadzone = lsYRange.getFlat();
			}
		}
		
		if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
			InputDevice.MotionRange rsXRange = dev.getMotionRange(mapping.rightStickXAxis);
			InputDevice.MotionRange rsYRange = dev.getMotionRange(mapping.rightStickYAxis);
			if (rsXRange != null) {
				mapping.rightStickXAxisDeadzone = rsXRange.getFlat();
			}
			if (rsYRange != null) {
				mapping.rightStickYAxisDeadzone = rsYRange.getFlat();
			}
		}
		
		return mapping;
	}
	
	private ControllerMapping getMappingForDevice(InputDevice dev) {
		// Unknown devices can't be handled
		if (dev == null) {
			return null;
		}
		
		String descriptor = dev.getDescriptor();
		
		// Return the existing mapping if it exists
		ControllerMapping mapping = mappings.get(descriptor);
		if (mapping != null) {
			return mapping;
		}
		
		// Otherwise create a new mapping
		mapping = createMappingForDevice(dev);
		mappings.put(descriptor, mapping);
		
		return mapping;
	}
	
	private void sendControllerInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}
	
	private int handleRemapping(ControllerMapping mapping, int keyCode) {
		if (mapping.isDualShock4) {
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
			}
		}
		
		if (mapping.hatXAxis != -1 && mapping.hatYAxis != -1) {
			switch (keyCode) {
			// These are duplicate dpad events for hat input
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
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
		if (mapping == null) {
			return false;
		}
		
		// Handle left stick events outside of the deadzone
		if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
			float LS_X = event.getAxisValue(mapping.leftStickXAxis);
			float LS_Y = event.getAxisValue(mapping.leftStickYAxis);
			if (LS_X >= -mapping.leftStickXAxisDeadzone && LS_X <= mapping.leftStickXAxisDeadzone) {
				LS_X = 0;
			}
			if (LS_Y >= -mapping.leftStickYAxisDeadzone && LS_Y <= mapping.leftStickYAxisDeadzone) {
				LS_Y = 0;
			}
			leftStickX = (short)Math.round(LS_X * 0x7FFF);
			leftStickY = (short)Math.round(-LS_Y * 0x7FFF);
		}
		
		// Handle right stick events outside of the deadzone
		if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
			float RS_X = event.getAxisValue(mapping.rightStickXAxis);
			float RS_Y = event.getAxisValue(mapping.rightStickYAxis);
			if (RS_X >= -mapping.rightStickXAxisDeadzone && RS_X <= mapping.rightStickXAxisDeadzone) {
				RS_X = 0;
			}
			if (RS_Y >= -mapping.rightStickYAxisDeadzone && RS_Y <= mapping.rightStickYAxisDeadzone) {
				RS_Y = 0;
			}
			rightStickX = (short)Math.round(RS_X * 0x7FFF);
			rightStickY = (short)Math.round(-RS_Y * 0x7FFF);	
		}
		
		// Handle controllers with analog triggers
		if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
			float L2 = event.getAxisValue(mapping.leftTriggerAxis);
			float R2 = event.getAxisValue(mapping.rightTriggerAxis);
			
			if (mapping.triggersIdleNegative) {
				L2 = (L2 + 1) / 2;
				R2 = (R2 + 1) / 2;
			}
			
			leftTrigger = (byte)Math.round(L2 * 0xFF);
			rightTrigger = (byte)Math.round(R2 * 0xFF);
		}

		// Hats emulate d-pad events
		if (mapping.hatXAxis != -1 && mapping.hatYAxis != -1) {
			float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
			float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

			inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
			if (hatX < -(0.5 + mapping.hatXDeadzone)) {
				inputMap |= ControllerPacket.LEFT_FLAG;
			}
			else if (hatX > (0.5 + mapping.hatXDeadzone)) {
				inputMap |= ControllerPacket.RIGHT_FLAG;
			}
			
			inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
			if (hatY < -(0.5 + mapping.hatYDeadzone)) {
				inputMap |= ControllerPacket.UP_FLAG;
			}
			else if (hatY > (0.5 + mapping.hatYDeadzone)) {
				inputMap |= ControllerPacket.DOWN_FLAG;
			}
		}

		sendControllerInputPacket();
		return true;
	}
	
	public boolean handleButtonUp(int keyCode, KeyEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
		if (mapping == null) {
			return false;
		}
		
		keyCode = handleRemapping(mapping, keyCode);
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
		case KeyEvent.KEYCODE_BUTTON_L2:
			leftTrigger = 0;
			break;
		case KeyEvent.KEYCODE_BUTTON_R2:
			rightTrigger = 0;
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
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
		if (mapping == null) {
			return false;
		}
		
		keyCode = handleRemapping(mapping, keyCode);
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
		case KeyEvent.KEYCODE_BUTTON_L2:
			leftTrigger = (byte)0xFF;
			break;
		case KeyEvent.KEYCODE_BUTTON_R2:
			rightTrigger = (byte)0xFF;
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
	
	class ControllerMapping {
		public int leftStickXAxis = -1;
		public float leftStickXAxisDeadzone;
		
		public int leftStickYAxis = -1;
		public float leftStickYAxisDeadzone;

		public int rightStickXAxis = -1;
		public float rightStickXAxisDeadzone;

		public int rightStickYAxis = -1;
		public float rightStickYAxisDeadzone;
		
		public int leftTriggerAxis = -1;
		public int rightTriggerAxis = -1;
		public boolean triggersIdleNegative;
		
		public int hatXAxis = -1;
		public int hatYAxis = -1;
		public float hatXDeadzone;
		public float hatYDeadzone;
		
		public boolean isDualShock4;
	}
}
