package com.limelight.nvstream.input;

import java.nio.ByteBuffer;

public class MouseScrollPacket extends InputPacket {
	public static final int PACKET_TYPE = 0xa;
	public static final int PAYLOAD_LENGTH = 10;
	public static final int PACKET_LENGTH = PAYLOAD_LENGTH +
		InputPacket.HEADER_LENGTH;
	
	short scroll;
	
	public MouseScrollPacket(byte scrollClicks)
	{
		super(PACKET_TYPE);
		this.scroll = (short)(scrollClicks * 120);
	}

	@Override
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_LENGTH);
		
		bb.put(toWireHeader());
		bb.put((byte) 0x09);
		bb.put((byte) 0);
		bb.put((byte) 0);
		bb.put((byte) 0);
		
		bb.putShort(scroll);
		bb.putShort(scroll);
		
		bb.putShort((short) 0);
		
		return bb.array();
	}
}
