package com.limelight.nvstream.input;

import java.nio.ByteBuffer;

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
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH);
		
		bb.put(toWireHeader());
		bb.put(HEADER);
		bb.putShort(deltaX);
		bb.putShort(deltaY);
		
		return bb.array();
	}
}
