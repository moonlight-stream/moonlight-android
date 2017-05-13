package com.limelight.nvstream;

public interface NvConnectionListener {
	void stageStarting(String stage);
	void stageComplete(String stage);
	void stageFailed(String stage);
	
	void connectionStarted();
	void connectionTerminated(Exception e);
	
	void displayMessage(String message);
	void displayTransientMessage(String message);
}
