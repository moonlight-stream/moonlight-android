package com.limelight.nvstream.av;

public interface ConnectionStatusListener {
	public void connectionTerminated();
	
	public void connectionDetectedFrameLoss(int firstLostFrame, int nextSuccessfulFrame);
	
	public void connectionSinkTooSlow(int firstLostFrame, int nextSuccessfulFrame);
	
	public void connectionReceivedFrame(int frameIndex);
	
	public void connectionLostPackets(int lastReceivedPacket, int nextReceivedPacket);
}
