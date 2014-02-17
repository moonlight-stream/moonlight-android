package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IntConfigTuple extends ConfigTuple {

	public static final short PAYLOAD_LENGTH = 4;
	
	public int payload;
	
	public IntConfigTuple(short packetType, int payload) {
		super(packetType, PAYLOAD_LENGTH);
		this.payload = payload;
	}

	@Override
	public byte[] payloadToWire() {
		ByteBuffer bb = ByteBuffer.allocate(PAYLOAD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(payload);
		return bb.array();
	}
}
