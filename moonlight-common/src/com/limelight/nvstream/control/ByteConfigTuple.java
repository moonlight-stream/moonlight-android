package com.limelight.nvstream.control;

public class ByteConfigTuple extends ConfigTuple {
	public static final short PAYLOAD_LENGTH = 1;
	
	public byte payload;
	
	public ByteConfigTuple(short packetType, byte payload) {
		super(packetType, PAYLOAD_LENGTH);
		this.payload = payload;
	}

	@Override
	public byte[] payloadToWire() {
		return new byte[] {payload};
	}
}
