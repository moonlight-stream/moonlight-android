package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MouseMovePacket extends InputPacket {
	
	private static final byte[] HEADER =
		{
		0x06,
		0x00,
		0x00,
		0x00
		};

	public static final int PACKET_TYPE = 0x8;
	public static final int PAYLOAD_LENGTH = 8;
	public static final int PACKET_LENGTH = PAYLOAD_LENGTH +
		InputPacket.HEADER_LENGTH;
	
	short deltaX;
	short deltaY;
	
	public MouseMovePacket(short deltaX, short deltaY)
	{
		super(PACKET_TYPE);
		
		this.deltaX = deltaX;
		this.deltaY = deltaY;
	}

	@Override
	public void toWirePayload(ByteBuffer bb) {
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.put(HEADER);
		bb.putShort(deltaX);
		bb.putShort(deltaY);
	}
	
	@Override
	public int getPacketLength() {
		return PACKET_LENGTH;
	}
}
