package com.limelight.nvstream.av;

public interface ConnectionStatusListener {
	public void connectionTerminated();
	
	public void connectionNeedsResync();
}
