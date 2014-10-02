package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
		public static final short ABS_LS_OFFSET = 7000;
		public static final short ABS_RS_OFFSET = 7000;
		
		public static final double ABS_LS_SCALE_FACTOR = 1 - (ABS_LS_OFFSET / 32768.0);
		public static final double ABS_RS_SCALE_FACTOR = 1 - (ABS_RS_OFFSET / 32768.0);
		
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
			this.leftStickX = scaleLeftStickAxis(leftStickX);
			this.leftStickY = scaleLeftStickAxis(leftStickY);
			this.rightStickX = scaleRightStickAxis(rightStickX);
			this.rightStickY = scaleRightStickAxis(rightStickY);
		}
		
		private static short scaleAxis(int axisValue, short offset, double factor) {
			// Exit quit if it's zero
			if (axisValue == 0) {
				return 0;
			}
			
			// Remember the sign and remove it from the value
			int sign = axisValue < 0 ? -1 : 1;
			axisValue = Math.abs(axisValue);
			
			// Scale the initial value
			axisValue = (int)(axisValue * factor);
			
			// Add the offset
			axisValue += offset;
			
			// Correct the value if it's over the limit
			if (axisValue > 32767) {
				axisValue = 32767;
			}
			
			// Restore sign and return
			return (short)(sign * axisValue);
		}
		
		private static short scaleLeftStickAxis(short axisValue) {
			if (enableAxisScaling) {
				axisValue = scaleAxis(axisValue, ABS_LS_OFFSET, ABS_LS_SCALE_FACTOR);
			}
			
			return axisValue;
		}
		
		private static short scaleRightStickAxis(short axisValue) {
			if (enableAxisScaling) {
				axisValue = scaleAxis(axisValue, ABS_RS_OFFSET, ABS_RS_SCALE_FACTOR);
			}
			
			return axisValue;
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