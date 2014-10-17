package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.utils.Vector2d;

public class ControllerPacket extends InputPacket {
		public static final byte[] HEADER =
			{
				0x0A,
				0x00,
				0x00,
				0x00,
				0x00,
				0x14
			};
		
		public static final byte[] TAIL =
			{
				(byte)0x9C,
				0x00,
				0x00,
				0x00,
				0x55,
				0x00
			};
		
		public static final int PACKET_TYPE = 0x18;
		
		public static final short A_FLAG = 0x1000;
		public static final short B_FLAG = 0x2000;
		public static final short X_FLAG = 0x4000;
		public static final short Y_FLAG = (short)0x8000;
		public static final short UP_FLAG = 0x0001;
		public static final short DOWN_FLAG = 0x0002;
		public static final short LEFT_FLAG = 0x0004;
		public static final short RIGHT_FLAG = 0x0008;
		public static final short LB_FLAG = 0x0100;
		public static final short RB_FLAG = 0x0200;
		public static final short PLAY_FLAG = 0x0010;
		public static final short BACK_FLAG = 0x0020;
		public static final short LS_CLK_FLAG = 0x0040;
		public static final short RS_CLK_FLAG = 0x0080;
		public static final short SPECIAL_BUTTON_FLAG = 0x0400;
		
		public static final short PAYLOAD_LENGTH = 24;
		public static final short PACKET_LENGTH = PAYLOAD_LENGTH +
				InputPacket.HEADER_LENGTH;
		
		// This is the highest value that is read as zero on the PC
		public static final short MIN_MAGNITUDE = 7000;
		
		// Set this flag if you want ControllerPacket to handle scaling for you
		// Note: You MUST properly handle deadzones to use this flag
		public static boolean enableAxisScaling = false;
		
		short buttonFlags;
		byte leftTrigger;
		byte rightTrigger;
		short leftStickX;
		short leftStickY;
		short rightStickX;
		short rightStickY;
		
		public ControllerPacket(short buttonFlags, byte leftTrigger, byte rightTrigger,
				 short leftStickX, short leftStickY,
				 short rightStickX, short rightStickY)
		{
			super(PACKET_TYPE);
			
			this.buttonFlags = buttonFlags;
			this.leftTrigger = leftTrigger;
			this.rightTrigger = rightTrigger;
			
			Vector2d leftStick = handleDeadZone(leftStickX, leftStickY);
			this.leftStickX = (short) leftStick.getX();
			this.leftStickY = (short) leftStick.getY();
			
			Vector2d rightStick = handleDeadZone(rightStickX, rightStickY);
			this.rightStickX = (short) rightStick.getX();
			this.rightStickY = (short) rightStick.getY();
		}
		
		private static Vector2d inputVector = new Vector2d();
		private static Vector2d normalizedInputVector = new Vector2d();
		
		// This function is NOT THREAD SAFE!
		private static Vector2d handleDeadZone(short x, short y) {
			// Get out fast if we're in the dead zone
			if (x == 0 && y == 0) {
				return Vector2d.ZERO;
			}
			
			// Reinitialize our cached Vector2d object
			inputVector.initialize(x, y);
			
			if (enableAxisScaling) {
				// Remember our original magnitude for scaling later
				double magnitude = inputVector.getMagnitude();
				
				// Scale to hit a minimum magnitude
				inputVector.getNormalized(normalizedInputVector);
				
				normalizedInputVector.setX(normalizedInputVector.getX() * MIN_MAGNITUDE);
				normalizedInputVector.setY(normalizedInputVector.getY() * MIN_MAGNITUDE);
				
				// Now scale the rest of the way
				normalizedInputVector.scalarMultiply((32766.0 / MIN_MAGNITUDE) / (32768.0 / magnitude));
				
				return normalizedInputVector;
			}
			else {
				return inputVector;
			}
		}

		@Override
		public void toWirePayload(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.put(HEADER);
			bb.putShort(buttonFlags);
			bb.put(leftTrigger);
			bb.put(rightTrigger);
			bb.putShort(leftStickX);
			bb.putShort(leftStickY);
			bb.putShort(rightStickX);
			bb.putShort(rightStickY);
			bb.put(TAIL);
		}
		
		@Override
		public int getPacketLength() {
			return PACKET_LENGTH;
		}
	}