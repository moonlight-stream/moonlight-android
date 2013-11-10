package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NvControllerPacket extends NvInputPacket {
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
				NvInputPacket.HEADER_LENGTH;
		
		private short buttonFlags;
		private byte leftTrigger;
		private byte rightTrigger;
		private short leftStickX;
		private short leftStickY;
		private short rightStickX;
		private short rightStickY;
		
		public NvControllerPacket(short buttonFlags, byte leftTrigger, byte rightTrigger,
				 short leftStickX, short leftStickY,
				 short rightStickX, short rightStickY)
		{
			super(PACKET_TYPE);
			
			this.buttonFlags = buttonFlags;
			this.leftTrigger = leftTrigger;
			this.rightTrigger = rightTrigger;
			this.leftStickX = leftStickX;
			this.leftStickY = leftStickY;
			this.rightStickX = rightStickX;
			this.rightStickY = rightStickY;
		}
		
		public byte[] toWire()
		{
			ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
			
			bb.put(toWireHeader());
			bb.put(HEADER);
			bb.putShort(buttonFlags);
			bb.put(leftTrigger);
			bb.put(rightTrigger);
			bb.putShort(leftStickX);
			bb.putShort(leftStickY);
			bb.putShort(rightStickX);
			bb.putShort(rightStickY);
			bb.put(TAIL);
			
			return bb.array();
		}
	}