package com.limelight.binding.input;

import java.util.HashMap;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.utils.Vector2d;

public class ControllerHandler {
	private short inputMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	private int emulatingButtonFlags = 0;
	
	// Used for OUYA bumper state tracking since they force all buttons
	// up when the OUYA button goes down. We watch the last time we get
	// a bumper up and compare that to our maximum delay when we receive
	// a Start button press to see if we should activate one of our 
	// emulated button combos.
	private long lastLbUpTime = 0;
	private long lastRbUpTime = 0;
	private static final int MAXIMUM_BUMPER_UP_DELAY_MS = 100;
	
	private static final int MINIMUM_BUTTON_DOWN_TIME_MS = 25;
	
	private static final int EMULATING_SPECIAL = 0x1;
	private static final int EMULATING_SELECT = 0x2;
	
	private static final int EMULATED_SPECIAL_UP_DELAY_MS = 100;
	private static final int EMULATED_SELECT_UP_DELAY_MS = 30;
	
	private Vector2d inputVector = new Vector2d();
	private Vector2d normalizedInputVector = new Vector2d();
	
	private HashMap<String, ControllerMapping> mappings = new HashMap<String, ControllerMapping>();
	
	private NvConnection conn;
	
	public ControllerHandler(NvConnection conn) {
		this.conn = conn;
		
		// We want limelight-common to scale the axis values to match Xinput values
		ControllerPacket.enableAxisScaling = true;
	}
	
	private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
		InputDevice.MotionRange range;
		
		// First get the axis for SOURCE_JOYSTICK
		range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
		if (range == null) {
			// Now try the axis for SOURCE_GAMEPAD
			range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
		}
		
		return range;
	}
	
	private ControllerMapping createMappingForDevice(InputDevice dev) {
		ControllerMapping mapping = new ControllerMapping();
		
		mapping.leftStickXAxis = MotionEvent.AXIS_X;
		mapping.leftStickYAxis = MotionEvent.AXIS_Y;
		
		InputDevice.MotionRange leftTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_LTRIGGER);
		InputDevice.MotionRange rightTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RTRIGGER);
		InputDevice.MotionRange brakeRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_BRAKE);
		InputDevice.MotionRange gasRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_GAS);
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
			InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
			InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);
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
					mapping.isXboxController = true;
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
			InputDevice.MotionRange zRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z);
			InputDevice.MotionRange rzRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ);
			
			// Most other controllers use Z and RZ for the right stick
			if (zRange != null && rzRange != null) {
				mapping.rightStickXAxis = MotionEvent.AXIS_Z;
				mapping.rightStickYAxis = MotionEvent.AXIS_RZ;
			}
			else {
				InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
				InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);
				
				// Try RX and RY now
				if (rxRange != null && ryRange != null) {
					mapping.rightStickXAxis = MotionEvent.AXIS_RX;
					mapping.rightStickYAxis = MotionEvent.AXIS_RY;
				}
			}
		}
		
		// Some devices have "hats" for d-pads
		InputDevice.MotionRange hatXRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_X);
		InputDevice.MotionRange hatYRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_Y);
		if (hatXRange != null && hatYRange != null) {
			mapping.hatXAxis = MotionEvent.AXIS_HAT_X;
			mapping.hatYAxis = MotionEvent.AXIS_HAT_Y;
			
			mapping.hatXDeadzone = hatXRange.getFlat();
			mapping.hatYDeadzone = hatYRange.getFlat();
		}
		
		if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
			InputDevice.MotionRange lsXRange = getMotionRangeForJoystickAxis(dev, mapping.leftStickXAxis);
			InputDevice.MotionRange lsYRange = getMotionRangeForJoystickAxis(dev, mapping.leftStickYAxis);
			if (lsXRange != null && lsYRange != null) {
				// The flat values should never be negative but we'll deal with it if they are
				mapping.leftStickDeadzoneRadius = Math.max(Math.abs(lsXRange.getFlat()),
						Math.abs(lsYRange.getFlat()));
				
				// Some devices (certain OUYAs at least) report a deadzone that's larger
				// than the entire range of their axis likely due to some system software bug.
				// If we see a very large deadzone, simply ignore the value and use our default.
				if (mapping.leftStickDeadzoneRadius > 0.5f) {
					mapping.leftStickDeadzoneRadius = 0;
				}
				
				// If there isn't a (reasonable) deadzone at all, use 20%
				if (mapping.leftStickDeadzoneRadius < 0.02f) {
					mapping.leftStickDeadzoneRadius = 0.20f;
				}
				// Check that the deadzone is 15% at minimum
				else if (mapping.leftStickDeadzoneRadius < 0.15f) {
					mapping.leftStickDeadzoneRadius = 0.15f;
				}
			}
		}
		
		if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
			InputDevice.MotionRange rsXRange = getMotionRangeForJoystickAxis(dev, mapping.rightStickXAxis);
			InputDevice.MotionRange rsYRange = getMotionRangeForJoystickAxis(dev, mapping.rightStickYAxis);
			if (rsXRange != null && rsYRange != null) {
				// The flat values should never be negative but we'll deal with it if they are
				mapping.rightStickDeadzoneRadius = Math.max(Math.abs(rsXRange.getFlat()),
						Math.abs(rsYRange.getFlat()));
				
				// Some devices (certain OUYAs at least) report a deadzone that's larger
				// than the entire range of their axis likely due to some system software bug.
				// If we see a very large deadzone, simply ignore the value and use our default.
				if (mapping.rightStickDeadzoneRadius > 0.5f) {
					mapping.rightStickDeadzoneRadius = 0;
				}
				
				// If there isn't a (reasonable) deadzone at all, use 20%
				if (mapping.rightStickDeadzoneRadius < 0.02f) {
					mapping.rightStickDeadzoneRadius = 0.20f;
				}
				// Check that the deadzone is 15% at minimum
				else if (mapping.rightStickDeadzoneRadius < 0.15f) {
					mapping.rightStickDeadzoneRadius = 0.15f;
				}
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
	
	private static int handleRemapping(ControllerMapping mapping, KeyEvent event) {
		if (mapping.isDualShock4) {
			switch (event.getKeyCode()) {
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
			switch (event.getKeyCode()) {
			// These are duplicate dpad events for hat input
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				return 0;
			}
		}
		else if (mapping.hatXAxis == -1 &&
				 mapping.hatYAxis == -1 &&
				 mapping.isXboxController &&
				 event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
			// If there's not a proper Xbox controller mapping, we'll translate the raw d-pad
			// scan codes into proper key codes
			switch (event.getScanCode())
			{
			case 704:
				return KeyEvent.KEYCODE_DPAD_LEFT;
			case 705:
				return KeyEvent.KEYCODE_DPAD_RIGHT;
			case 706:
				return KeyEvent.KEYCODE_DPAD_UP;
			case 707:
				return KeyEvent.KEYCODE_DPAD_DOWN;
			}
		}
		
		return event.getKeyCode();
	}
	
	private Vector2d handleDeadZone(float x, float y, float deadzoneRadius) {
		// Reinitialize our cached Vector2d object
		inputVector.initialize(x, y);
		
		if (inputVector.getMagnitude() <= deadzoneRadius) {
			// Deadzone -- return the zero vector
			return Vector2d.ZERO;
		}
		else {			
			// Scale the input based on the distance from the deadzone
			inputVector.getNormalized(normalizedInputVector);
			normalizedInputVector.scalarMultiply((inputVector.getMagnitude() - deadzoneRadius) / (1.0f - deadzoneRadius));
		
			// Bound the X value to -1.0 to 1.0
			if (normalizedInputVector.getX() > 1.0f) {
				normalizedInputVector.setX(1.0f);
			}
			else if (normalizedInputVector.getX() < -1.0f) {
				normalizedInputVector.setX(-1.0f);
			}
			
			// Bound the Y value to -1.0 to 1.0
			if (normalizedInputVector.getY() > 1.0f) {
				normalizedInputVector.setY(1.0f);
			}
			else if (normalizedInputVector.getY() < -1.0f) {
				normalizedInputVector.setY(-1.0f);
			}
			
			return normalizedInputVector;
		}
	}
	
	public boolean handleMotionEvent(MotionEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
		if (mapping == null) {
			return false;
		}
		
		// Handle left stick events outside of the deadzone
		if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
			Vector2d leftStickVector = handleDeadZone(event.getAxisValue(mapping.leftStickXAxis),
					event.getAxisValue(mapping.leftStickYAxis), mapping.leftStickDeadzoneRadius);
			
			leftStickX = (short)(leftStickVector.getX() * 0x7FFE);
			leftStickY = (short)(-leftStickVector.getY() * 0x7FFE);
		}
		
		// Handle right stick events outside of the deadzone
		if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
			Vector2d rightStickVector = handleDeadZone(event.getAxisValue(mapping.rightStickXAxis),
					event.getAxisValue(mapping.rightStickYAxis), mapping.rightStickDeadzoneRadius);
			
			rightStickX = (short)(rightStickVector.getX() * 0x7FFE);
			rightStickY = (short)(-rightStickVector.getY() * 0x7FFE);
		}
		
		// Handle controllers with analog triggers
		if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
			float L2 = event.getAxisValue(mapping.leftTriggerAxis);
			float R2 = event.getAxisValue(mapping.rightTriggerAxis);
			
			if (mapping.triggersIdleNegative) {
				L2 = (L2 + 1) / 2;
				R2 = (R2 + 1) / 2;
			}
			
			leftTrigger = (byte)(L2 * 0xFF);
			rightTrigger = (byte)(R2 * 0xFF);
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
		
		keyCode = handleRemapping(mapping, event);
		if (keyCode == 0) {
			return true;
		}
		
		// If the button hasn't been down long enough, sleep for a bit before sending the up event
		// This allows "instant" button presses (like OUYA's virtual menu button) to work. This
		// path should not be triggered during normal usage.
		if (SystemClock.uptimeMillis() - event.getDownTime() < ControllerHandler.MINIMUM_BUTTON_DOWN_TIME_MS)
		{
			// Since our sleep time is so short (10 ms), it shouldn't cause a problem doing this in the
			// UI thread.
			try {
				Thread.sleep(ControllerHandler.MINIMUM_BUTTON_DOWN_TIME_MS);
			} catch (InterruptedException ignored) {}
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
		case KeyEvent.KEYCODE_DPAD_CENTER:
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
			lastLbUpTime = SystemClock.uptimeMillis();
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap &= ~ControllerPacket.RB_FLAG;
			lastRbUpTime = SystemClock.uptimeMillis();
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
		
		// Check if we're emulating the select button
		if ((emulatingButtonFlags & ControllerHandler.EMULATING_SELECT) != 0)
		{
			// If either start or LB is up, select comes up too
			if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
				(inputMap & ControllerPacket.LB_FLAG) == 0)
			{
				inputMap &= ~ControllerPacket.BACK_FLAG;
				
				emulatingButtonFlags &= ~ControllerHandler.EMULATING_SELECT;
				
				try {
					Thread.sleep(EMULATED_SELECT_UP_DELAY_MS);
				} catch (InterruptedException ignored) {}
			}
		}
		
		// Check if we're emulating the special button
		if ((emulatingButtonFlags & ControllerHandler.EMULATING_SPECIAL) != 0)
		{
			// If either start or select and RB is up, the special button comes up too
			if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
				((inputMap & ControllerPacket.BACK_FLAG) == 0 &&
				 (inputMap & ControllerPacket.RB_FLAG) == 0))
			{
				inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
				
				emulatingButtonFlags &= ~ControllerHandler.EMULATING_SPECIAL;
				
				try {
					Thread.sleep(EMULATED_SPECIAL_UP_DELAY_MS);
				} catch (InterruptedException ignored) {}
			}
		}
		
		sendControllerInputPacket();
		return true;
	}
	
	public boolean handleButtonDown(int keyCode, KeyEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
		if (mapping == null) {
			return false;
		}
		
		keyCode = handleRemapping(mapping, event);
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
		case KeyEvent.KEYCODE_DPAD_CENTER:
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
		
		// Start+LB acts like select for controllers with one button
		if ((inputMap & ControllerPacket.PLAY_FLAG) != 0 &&
			((inputMap & ControllerPacket.LB_FLAG) != 0 ||
			  SystemClock.uptimeMillis() - lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
		{
			inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.LB_FLAG);
			inputMap |= ControllerPacket.BACK_FLAG;
			
			emulatingButtonFlags |= ControllerHandler.EMULATING_SELECT;
		}
		
		// We detect select+start or start+RB as the special button combo
		if (((inputMap & ControllerPacket.RB_FLAG) != 0 ||
			 (SystemClock.uptimeMillis() - lastRbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS) ||
			 (inputMap & ControllerPacket.BACK_FLAG) != 0) &&
			(inputMap & ControllerPacket.PLAY_FLAG) != 0)
		{
			inputMap &= ~(ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.RB_FLAG);
			inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
			
			emulatingButtonFlags |= ControllerHandler.EMULATING_SPECIAL;
		}
		
		sendControllerInputPacket();
		return true;
	}
	
	class ControllerMapping {
		public int leftStickXAxis = -1;		
		public int leftStickYAxis = -1;
		public float leftStickDeadzoneRadius;

		public int rightStickXAxis = -1;
		public int rightStickYAxis = -1;
		public float rightStickDeadzoneRadius;
		
		public int leftTriggerAxis = -1;
		public int rightTriggerAxis = -1;
		public boolean triggersIdleNegative;
		
		public int hatXAxis = -1;
		public int hatYAxis = -1;
		public float hatXDeadzone;
		public float hatYDeadzone;
		
		public boolean isDualShock4;
		public boolean isXboxController;
	}
}
