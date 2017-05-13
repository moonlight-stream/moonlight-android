package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public class MouseScrollPacket extends InputPacket {
	private static final int HEADER_CODE = 0x09;
	private static final int PACKET_TYPE = 0xa;
	private static final int PAYLOAD_LENGTH = 10;
	private static final int PACKET_LENGTH = PAYLOAD_LENGTH +
		InputPacket.HEADER_LENGTH;
	
	
	private int headerCode;
	private short scroll;
	
	public MouseScrollPacket(ConnectionContext context, byte scrollClicks)
	{
		super(PACKET_TYPE);
		
		this.headerCode = HEADER_CODE;
		
		// On Gen 5 servers, the header code is incremented by one
		if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
			headerCode++;
		}
		
		this.scroll = (short)(scrollClicks * 120);
	}

	@Override
	public void toWirePayload(ByteBuffer bb) {		
		bb.order(ByteOrder.LITTLE_ENDIAN).putInt(headerCode);
		
		bb.order(ByteOrder.BIG_ENDIAN);
		
		bb.putShort(scroll);
		bb.putShort(scroll);
		
		bb.putShort((short) 0);
	}
	
	@Override
	public int getPacketLength() {
		return PACKET_LENGTH;
	}
}
