package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MouseButtonPacket extends InputPacket {
	
	byte buttonEventType;
	byte mouseButton;
	
	public static final int PACKET_TYPE = 0x5;
	public static final int PAYLOAD_LENGTH = 5;
	public static final int PACKET_LENGTH = PAYLOAD_LENGTH +
			InputPacket.HEADER_LENGTH;
	
	public static final byte PRESS_EVENT = 0x07;
	public static final byte RELEASE_EVENT = 0x08;
	
	// left
	public static final byte BUTTON_1 = 0x01;
	// middle
	public static final byte BUTTON_2 = 0x02;
	// right
	public static final byte BUTTON_3 = 0x03;
	
	public MouseButtonPacket(boolean buttonDown, byte mouseButton)
	{
		super(PACKET_TYPE);
		
		this.mouseButton = mouseButton;
				
		buttonEventType = buttonDown ?
				PRESS_EVENT : RELEASE_EVENT;
	}

	@Override
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN);
		
		bb.put(toWireHeader());
		bb.put(buttonEventType);
		bb.putInt(mouseButton); 
		
		return bb.array();
	}
}
