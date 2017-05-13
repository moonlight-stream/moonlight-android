package com.limelight.nvstream.av;

public interface RtpPacketFields {
	public byte getPacketType();
	
	public short getRtpSequenceNumber();
	
	public int referencePacket();
	
	public int dereferencePacket();
	
	public int getRefCount();
}
