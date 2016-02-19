package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public class MouseMovePacket extends InputPacket {
	private static final int HEADER_CODE = 0x06;
	private static final int PACKET_TYPE = 0x8;
	private static final int PAYLOAD_LENGTH = 8;
	private static final int PACKET_LENGTH = PAYLOAD_LENGTH +
		InputPacket.HEADER_LENGTH;
	
	private int headerCode; 
	
	// Accessed in ControllerStream for batching
	short deltaX;
	short deltaY;
	
	public MouseMovePacket(ConnectionContext context, short deltaX, short deltaY)
	{
		super(PACKET_TYPE);
		
		this.headerCode = HEADER_CODE;
		
		// On Gen 5 servers, the header code is incremented by one
		if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
			headerCode++;
		}
		
		this.deltaX = deltaX;
		this.deltaY = deltaY;
	}

	@Override
	public void toWirePayload(ByteBuffer bb) {
		bb.order(ByteOrder.LITTLE_ENDIAN).putInt(headerCode);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putShort(deltaX);
		bb.putShort(deltaY);
	}
	
	@Override
	public int getPacketLength() {
		return PACKET_LENGTH;
	}
}
