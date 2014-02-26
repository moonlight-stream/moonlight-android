package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ShortConfigTuple extends ConfigTuple {

	public static final short PAYLOAD_LENGTH = 2;
	
	public short payload;
	
	public ShortConfigTuple(short packetType, short payload) {
		super(packetType, PAYLOAD_LENGTH);
		this.payload = payload;
	}

	@Override
	public byte[] payloadToWire() {
		ByteBuffer bb = ByteBuffer.allocate(PAYLOAD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		bb.putShort(payload);
		return bb.array();
	}
}