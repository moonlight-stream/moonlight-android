package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MultiControllerPacket extends InputPacket {
		public static final byte[] TAIL =
			{
				(byte)0x9C,
				0x00,
				0x00,
				0x00,
				0x55,
				0x00
			};
		
		public static final int PACKET_TYPE = 0x1e;
		
		public static final short PAYLOAD_LENGTH = 30;
		public static final short PACKET_LENGTH = PAYLOAD_LENGTH +
				InputPacket.HEADER_LENGTH;
		
		short controllerNumber;
		short buttonFlags;
		byte leftTrigger;
		byte rightTrigger;
		short leftStickX;
		short leftStickY;
		short rightStickX;
		short rightStickY;
		
		public MultiControllerPacket(short controllerNumber, short buttonFlags, byte leftTrigger, byte rightTrigger,
				 short leftStickX, short leftStickY,
				 short rightStickX, short rightStickY)
		{
			super(PACKET_TYPE);
			
			this.controllerNumber = controllerNumber;
			
			this.buttonFlags = buttonFlags;
			this.leftTrigger = leftTrigger;
			this.rightTrigger = rightTrigger;
			
			this.leftStickX = leftStickX;
			this.leftStickY = leftStickY;
			
			this.rightStickX = rightStickX;
			this.rightStickY = rightStickY;
		}

		@Override
		public void toWirePayload(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.putInt(0xd);
			bb.putShort((short) 0x1a);
			bb.putShort(controllerNumber);
			bb.putShort((short) 0x07);
			bb.putShort((short) 0x14);
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