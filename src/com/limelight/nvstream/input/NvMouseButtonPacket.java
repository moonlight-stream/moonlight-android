package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NvMouseButtonPacket extends NvInputPacket {
	
	private byte buttonEventType;
	
	public static final int PACKET_TYPE = 0x5;
	public static final int PAYLOAD_LENGTH = 5;
	public static final int PACKET_LENGTH = PAYLOAD_LENGTH +
			NvInputPacket.HEADER_LENGTH;
	
	public static final byte PRESS_EVENT = 0x07;
	public static final byte RELEASE_EVENT = 0x08;
	
	public NvMouseButtonPacket(boolean leftButtonDown)
	{
		super(PACKET_TYPE);
		
		buttonEventType = leftButtonDown ?
				PRESS_EVENT : RELEASE_EVENT;
	}

	@Override
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN);
		
		bb.put(toWireHeader());
		bb.put(buttonEventType);
		bb.putInt(1); // FIXME: button index?
		
		return bb.array();
	}
}
