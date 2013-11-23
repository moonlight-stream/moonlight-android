package com.limelight.nvstream;

public interface NvConnectionListener {
	
	public enum Stage {
		LAUNCH_APP("app"),
		HANDSHAKE("handshake"),
		CONTROL_START("control connection"),
		VIDEO_START("video stream"),
		AUDIO_START("audio stream"),
		CONTROL_START2("control connection"),
		INPUT_START("input connection");
		
		private String name;
		private Stage(String name) {
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
}
