package com.limelight.nvstream;

public interface NvConnectionListener {
	void stageStarting(String stage);
	void stageComplete(String stage);
	void stageFailed(String stage, long errorCode);
	
	void connectionStarted();
	void connectionTerminated(long errorCode);
	void connectionStatusUpdate(int connectionStatus);
	
	void displayMessage(String message);
	void displayTransientMessage(String message);

	void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor);
}
