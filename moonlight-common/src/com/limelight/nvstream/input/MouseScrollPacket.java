package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
	public void toWirePayload(ByteBuffer bb) {
		bb.order(ByteOrder.BIG_ENDIAN);
		
		bb.put((byte) 0x09);
		bb.put((byte) 0);
		bb.put((byte) 0);
		bb.put((byte) 0);
		
		bb.putShort(scroll);
		bb.putShort(scroll);
		
		bb.putShort((short) 0);
	}
	
	@Override
	public int getPacketLength() {
		return PACKET_LENGTH;
	}
}
