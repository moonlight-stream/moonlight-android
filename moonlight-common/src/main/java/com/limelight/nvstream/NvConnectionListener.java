package com.limelight.nvstream;

public interface NvConnectionListener {
	
	public enum Stage {
		LAUNCH_APP("app"),
		RTSP_HANDSHAKE("RTSP handshake"),
		CONTROL_START("control connection"),
		VIDEO_START("video stream"),
		AUDIO_START("audio stream"),
		INPUT_START("input connection");
		
		private String name;
		private Stage(String name) {
			this.name = name;
		}
		
		void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
	};
	
	public void stageStarting(Stage stage);
	public void stageComplete(Stage stage);
	public void stageFailed(Stage stage);
	
	public void connectionStarted();
	public void connectionTerminated(Exception e);
	
	public void displayMessage(String message);
	public void displayTransientMessage(String message);
}
