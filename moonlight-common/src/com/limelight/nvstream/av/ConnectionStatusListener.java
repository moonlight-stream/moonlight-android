package com.limelight.nvstream.av;

public interface ConnectionStatusListener {
	public void connectionTerminated();
	
	public void connectionDetectedFrameLoss(int firstLostFrame, int lastLostFrame);
	
	public void connectionSinkTooSlow(int firstLostFrame, int lastLostFrame);
	
	public void connectionReceivedFrame(int frameIndex);
}
