package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public class MultiControllerPacket extends InputPacket {
		private static final byte[] TAIL =
			{
				(byte)0x9C,
				0x00,
				0x00,
				0x00,
				0x55,
				0x00
			};
		
		private static final int HEADER_CODE = 0x0d;
		private static final int PACKET_TYPE = 0x1e;
		
		private static final short PAYLOAD_LENGTH = 30;
		private static final short PACKET_LENGTH = PAYLOAD_LENGTH +
				InputPacket.HEADER_LENGTH;
		
		short controllerNumber;
		short activeGamepadMask;
		short buttonFlags;
		byte leftTrigger;
		byte rightTrigger;
		short leftStickX;
		short leftStickY;
		short rightStickX;
		short rightStickY;
		
		private int headerCode;
		
		public MultiControllerPacket(ConnectionContext context, 
				short controllerNumber, short activeGamepadMask,
				short buttonFlags, byte leftTrigger, byte rightTrigger,
				 short leftStickX, short leftStickY,
				 short rightStickX, short rightStickY)
		{
			super(PACKET_TYPE);
			
			this.headerCode = HEADER_CODE;
			
			// On Gen 5 servers, the header code is decremented by one
			if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
				headerCode--;
			}
			
			this.controllerNumber = controllerNumber;
			this.activeGamepadMask = activeGamepadMask;
			
			this.buttonFlags = buttonFlags;
			this.leftTrigger = leftTrigger;
			this.rightTrigger = rightTrigger;
			
			this.leftStickX = leftStickX;
			this.leftStickY = leftStickY;
			
			this.rightStickX = rightStickX;
			this.rightStickY = rightStickY;
		}
		
		public MultiControllerPacket(int packetType,
				 short controllerNumber, short activeGamepadMask,
				 short buttonFlags,
				 byte leftTrigger, byte rightTrigger,
				 short leftStickX, short leftStickY,
				 short rightStickX, short rightStickY)
		{
			super(packetType);
			
			this.controllerNumber = controllerNumber;
			this.activeGamepadMask = activeGamepadMask;
			
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
			bb.putInt(headerCode);
			bb.putShort((short) 0x1a);
			bb.putShort(controllerNumber);
			bb.putShort(activeGamepadMask);
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