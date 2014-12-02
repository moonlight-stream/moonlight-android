package com.limelight.binding.input;

import java.util.HashMap;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.LimeLog;
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
	
	private HashMap<String, ControllerMapping> mappings = new HashMap<String, ControllerMapping>();
	
	private NvConnection conn;
    private double stickDeadzone;
    private final ControllerMapping defaultMapping = new ControllerMapping();
    private boolean hasGameController;
	
	public ControllerHandler(NvConnection conn, int deadzonePercentage) {
		this.conn = conn;

        // HACK: For now we're hardcoding a 10% deadzone. Some deadzone
        // is required for controller batching support to work.
        deadzonePercentage = 10;

        int[] ids = InputDevice.getDeviceIds();
        for (int i = 0; i < ids.length; i++) {
            InputDevice dev = InputDevice.getDevice(ids[i]);
            if ((dev.getSources() & InputDevice.SOURCE_JOYSTICK) != 0 ||
                    (dev.getSources() & InputDevice.SOURCE_GAMEPAD) != 0) {
                // This looks like a gamepad, but we'll check X and Y to be sure
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                    getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null) {
                    // This is a gamepad
                    hasGameController = true;
                }
            }
        }

        // 1% is the lowest possible deadzone we support
        if (deadzonePercentage <= 0) {
            deadzonePercentage = 1;
        }

        this.stickDeadzone = (double)deadzonePercentage / 100.0;

        // Initialize the default mapping for events with no device
        defaultMapping.leftStickXAxis = MotionEvent.AXIS_X;
        defaultMapping.leftStickYAxis = MotionEvent.AXIS_Y;
        defaultMapping.leftStickDeadzoneRadius = (float) stickDeadzone;
        defaultMapping.rightStickXAxis = MotionEvent.AXIS_Z;
        defaultMapping.rightStickYAxis = MotionEvent.AXIS_RZ;
        defaultMapping.rightStickDeadzoneRadius = (float) stickDeadzone;
        defaultMapping.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
        defaultMapping.rightTriggerAxis = MotionEvent.AXIS_GAS;
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
        String devName = dev.getName();

        LimeLog.info("Creating controller mapping for device: "+devName);

        mapping.leftStickXAxis = MotionEvent.AXIS_X;
		mapping.leftStickYAxis = MotionEvent.AXIS_Y;
        if (getMotionRangeForJoystickAxis(dev, mapping.leftStickXAxis) != null &&
                getMotionRangeForJoystickAxis(dev, mapping.leftStickYAxis) != null) {
            // This is a gamepad
            hasGameController = true;
            mapping.hasJoystickAxes = true;
        }
		
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
			if (rxRange != null && ryRange != null && devName != null) {
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
		}
		
		if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
            mapping.leftStickDeadzoneRadius = (float) stickDeadzone;
		}
		
		if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
            mapping.rightStickDeadzoneRadius = (float) stickDeadzone;
        }

        if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
            InputDevice.MotionRange ltRange = getMotionRangeForJoystickAxis(dev, mapping.leftTriggerAxis);
            InputDevice.MotionRange rtRange = getMotionRangeForJoystickAxis(dev, mapping.rightTriggerAxis);

            // It's important to have a valid deadzone so controller packet batching works properly
            mapping.triggerDeadzone = Math.max(Math.abs(ltRange.getFlat()), Math.abs(rtRange.getFlat()));

            // For triggers without (valid) deadzones, we'll use 13% (around XInput's default)
            if (mapping.triggerDeadzone < 0.13f ||
                mapping.triggerDeadzone > 0.30f)
            {
                mapping.triggerDeadzone = 0.13f;
            }
        }

        if (devName != null) {
            // For the Nexus Player (and probably other ATV devices), we should
            // use the back button as start since it doesn't have a start/menu button
            // on the controller
            if (devName.contains("ASUS Gamepad")) {
                // We can only do this check on KitKat or higher, but it doesn't matter since ATV
                // is Android 5.0 anyway
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    boolean[] hasStartKey = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, 0);
                    if (!hasStartKey[0] && !hasStartKey[1]) {
                        mapping.backIsStart = true;
                    }
                }

                // The ASUS Gamepad has triggers that sit far forward and are prone to false presses
                // so we increase the deadzone on them to minimize this
                mapping.triggerDeadzone = 0.30f;
            }
            // Classify this device as a remote by name
            else if (devName.contains("Fire TV Remote") || devName.contains("Nexus Remote")) {
                // It's only a remote if it doesn't any sticks
                if (!mapping.hasJoystickAxes) {
                    mapping.isRemote = true;
                }
            }
            // NYKO Playpad has a fake hat that mimics the left stick for some reason
            else if (devName.contains("NYKO PLAYPAD")) {
                mapping.hatXAxis = -1;
                mapping.hatYAxis = -1;
            }
        }

        LimeLog.info("Analog stick deadzone: "+mapping.leftStickDeadzoneRadius+" "+mapping.rightStickDeadzoneRadius);
        LimeLog.info("Trigger deadzone: "+mapping.triggerDeadzone);
		
		return mapping;
	}
	
	private ControllerMapping getMappingForDevice(InputDevice dev) {
		// Unknown devices use the default mapping
		if (dev == null) {
			return defaultMapping;
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

    // Return a valid keycode, 0 to consume, or -1 to not consume the event
    // Device MAY BE NULL
	private int handleRemapping(ControllerMapping mapping, KeyEvent event) {
        // For remotes, don't capture the back button
		if (mapping.isRemote) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return -1;
            }
        }

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

        // Past here we can fixup the keycode and potentially trigger
        // another special case so we need to remember what keycode we're using
        int keyCode = event.getKeyCode();

        // This is a hack for (at least) the "Tablet Remote" app
        // which sends BACK with META_ALT_ON instead of KEYCODE_BUTTON_B
        if (keyCode == KeyEvent.KEYCODE_BACK &&
                !event.hasNoModifiers() &&
                (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0)
        {
            keyCode = KeyEvent.KEYCODE_BUTTON_B;
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                keyCode == KeyEvent.KEYCODE_MENU) {
            // Ensure that we never use back as start if we have a real start
            mapping.backIsStart = false;
        }
        else if (mapping.backIsStart && keyCode == KeyEvent.KEYCODE_BACK) {
            // Emulate the start button with back
            return KeyEvent.KEYCODE_BUTTON_START;
        }
		
		return keyCode;
	}

    private Vector2d populateCachedVector(float x, float y) {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y);
        return inputVector;
    }
	
	private void handleDeadZone(Vector2d stickVector, float deadzoneRadius) {
		if (stickVector.getMagnitude() <= deadzoneRadius) {
			// Deadzone
            stickVector.initialize(0, 0);
		}

        // We're not normalizing here because we let the computer handle the deadzones.
        // Normalizing can make the deadzones larger than they should be after the computer also
        // evaluates the deadzone.
    }

    private void handleAxisSet(ControllerMapping mapping, float lsX, float lsY, float rsX,
                               float rsY, float lt, float rt, float hatX, float hatY) {

        if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
            Vector2d leftStickVector = populateCachedVector(lsX, lsY);

            handleDeadZone(leftStickVector, mapping.leftStickDeadzoneRadius);

            leftStickX = (short) (leftStickVector.getX() * 0x7FFE);
            leftStickY = (short) (-leftStickVector.getY() * 0x7FFE);
        }

        if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
            Vector2d rightStickVector = populateCachedVector(rsX, rsY);

            handleDeadZone(rightStickVector, mapping.rightStickDeadzoneRadius);

            rightStickX = (short) (rightStickVector.getX() * 0x7FFE);
            rightStickY = (short) (-rightStickVector.getY() * 0x7FFE);
        }

        if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
            if (mapping.triggersIdleNegative) {
                lt = (lt + 1) / 2;
                rt = (rt + 1) / 2;
            }

            if (lt <= mapping.triggerDeadzone) {
                lt = 0;
            }
            if (rt <= mapping.triggerDeadzone) {
                rt = 0;
            }

            leftTrigger = (byte)(lt * 0xFF);
            rightTrigger = (byte)(rt * 0xFF);
        }

        if (mapping.hatXAxis != -1 && mapping.hatYAxis != -1) {
            inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
            if (hatX < -0.5) {
                inputMap |= ControllerPacket.LEFT_FLAG;
            }
            else if (hatX > 0.5) {
                inputMap |= ControllerPacket.RIGHT_FLAG;
            }

            inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
            if (hatY < -0.5) {
                inputMap |= ControllerPacket.UP_FLAG;
            }
            else if (hatY > 0.5) {
                inputMap |= ControllerPacket.DOWN_FLAG;
            }
        }

        sendControllerInputPacket();
    }
	
	public boolean handleMotionEvent(MotionEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());
        float lsX = 0, lsY = 0, rsX = 0, rsY = 0, rt = 0, lt = 0, hatX = 0, hatY = 0;

        // We purposefully ignore the historical values in the motion event as it makes
        // the controller feel sluggish for some users.

        if (mapping.leftStickXAxis != -1 && mapping.leftStickYAxis != -1) {
            lsX = event.getAxisValue(mapping.leftStickXAxis);
            lsY = event.getAxisValue(mapping.leftStickYAxis);
        }

        if (mapping.rightStickXAxis != -1 && mapping.rightStickYAxis != -1) {
            rsX = event.getAxisValue(mapping.rightStickXAxis);
            rsY = event.getAxisValue(mapping.rightStickYAxis);
        }

        if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
            lt = event.getAxisValue(mapping.leftTriggerAxis);
            rt = event.getAxisValue(mapping.rightTriggerAxis);
        }

        if (mapping.hatXAxis != -1 && mapping.hatYAxis != -1) {
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        }

        handleAxisSet(mapping, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY);

		return true;
	}
	
	public boolean handleButtonUp(KeyEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());

        int keyCode = handleRemapping(mapping, event);
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
	
	public boolean handleButtonDown(KeyEvent event) {
		ControllerMapping mapping = getMappingForDevice(event.getDevice());

        int keyCode = handleRemapping(mapping, event);
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

        // Send a new input packet if this is the first instance of a button down event
        // or anytime if we're emulating a button
        if (event.getRepeatCount() == 0 || emulatingButtonFlags != 0) {
            sendControllerInputPacket();
        }
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
        public float triggerDeadzone;
		
		public int hatXAxis = -1;
		public int hatYAxis = -1;
		
		public boolean isDualShock4;
		public boolean isXboxController;
        public boolean backIsStart;
        public boolean isRemote;
        public boolean hasJoystickAxes;
	}
}
