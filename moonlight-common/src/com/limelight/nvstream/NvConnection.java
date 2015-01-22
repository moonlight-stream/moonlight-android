package com.limelight.nvstream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioStream;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoStream;
import com.limelight.nvstream.control.ControlStream;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.input.ControllerStream;
import com.limelight.nvstream.rtsp.RtspConnection;

public class NvConnection {
	private String host;
	private NvConnectionListener listener;
	private StreamConfiguration config;
	private LimelightCryptoProvider cryptoProvider;
	private String uniqueId;
	
	private InetAddress hostAddr;
	private ControlStream controlStream;
	private ControllerStream inputStream;
	private VideoStream videoStream;
	private AudioStream audioStream;
	
	// Start parameters
	private int drFlags;
	private Object videoRenderTarget;
	private VideoDecoderRenderer videoDecoderRenderer;
	private AudioRenderer audioRenderer;
	private String localDeviceName;
	private SecretKey riKey;
	private int riKeyId;
	
	public NvConnection(String host, String uniqueId, NvConnectionListener listener, StreamConfiguration config, LimelightCryptoProvider cryptoProvider)
	{
		this.host = host;
		this.listener = listener;
		this.config = config;
		this.cryptoProvider = cryptoProvider;
		this.uniqueId = uniqueId;
		
		try {
			// This is unique per connection
			this.riKey = generateRiAesKey();
		} catch (NoSuchAlgorithmException e) {
			// Should never happen
			e.printStackTrace();
		}
		
		this.riKeyId = generateRiKeyId();
	}
	
	private static SecretKey generateRiAesKey() throws NoSuchAlgorithmException {
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		
		// RI keys are 128 bits
		keyGen.init(128);
		
		return keyGen.generateKey();
	}
	
	private static int generateRiKeyId() {
		return new SecureRandom().nextInt();
	}
	
	public void stop()
	{
		if (videoStream != null) {
			videoStream.abort();
		}
		if (audioStream != null) {
			audioStream.abort();
		}
		
		if (controlStream != null) {
			controlStream.abort();
		}
		
		if (inputStream != null) {
			inputStream.abort();
			inputStream = null;
		}
	}
	
	private boolean startApp() throws XmlPullParserException, IOException
	{
		NvHTTP h = new NvHTTP(hostAddr, uniqueId, localDeviceName, cryptoProvider);
		
		String serverInfo = h.getServerInfo(uniqueId);
		String serverVersion = h.getServerVersion(serverInfo);
		if (!serverVersion.startsWith("4.")) {
			listener.displayMessage("Limelight now requires GeForce Experience 2.2.2 or later. Please upgrade GFE on your PC and try again.");
			return false;
		}
		
		if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
			listener.displayMessage("Device not paired with computer");
			return false;
		}
				
		NvApp app = h.getApp(config.getApp());
		if (app == null) {
			listener.displayMessage("The app " + config.getApp() + " is not in GFE app list");
			return false;
		}
		
		// If there's a game running, resume it
		if (h.getCurrentGame(serverInfo) != 0) {
			try {
				if (h.getCurrentGame(serverInfo) == app.getAppId()) {
					if (!h.resumeApp(riKey, riKeyId)) {
						listener.displayMessage("Failed to resume existing session");
						return false;
					}
				} else if (h.getCurrentGame(serverInfo) != app.getAppId()) {
					return quitAndLaunch(h, app);
				}
			} catch (GfeHttpResponseException e) {
				if (e.getErrorCode() == 470) {
					// This is the error you get when you try to resume a session that's not yours.
					// Because this is fairly common, we'll display a more detailed message.
					listener.displayMessage("This session wasn't started by this device," +
							" so it cannot be resumed. End streaming on the original " +
							"device or the PC itself and try again. (Error code: "+e.getErrorCode()+")");
					return false;
				}
				else if (e.getErrorCode() == 525) {
					listener.displayMessage("The application is minimized. Resume it on the PC manually or " +
							"quit the session and start streaming again.");
					return false;
				} else {
					throw e;
				}
			}
			
			LimeLog.info("Resumed existing game session");
			return true;
		}
		else {
			return launchNotRunningApp(h, app);
		}
	}

	protected boolean quitAndLaunch(NvHTTP h, NvApp app) throws IOException,
			XmlPullParserException {
		if (!h.quitApp()) {
			listener.displayMessage("Failed to quit previous session! You must quit it manually");
			return false;
		} else {
			return launchNotRunningApp(h, app);
		}
	}
	
	private boolean launchNotRunningApp(NvHTTP h, NvApp app) 
			throws IOException, XmlPullParserException {
		// Launch the app since it's not running
		int gameSessionId = h.launchApp(app.getAppId(), riKey, riKeyId, config);
		if (gameSessionId == 0) {
			listener.displayMessage("Failed to launch application");
			return false;
		}
		
		LimeLog.info("Launched new game session");
		
		return true;
	}
	
	private boolean doRtspHandshake() throws IOException
	{
		RtspConnection r = new RtspConnection(hostAddr);
		r.doRtspHandshake(config);
		return true;
	}
	
	private boolean startControlStream() throws IOException
	{
		controlStream = new ControlStream(hostAddr, listener);
		controlStream.initialize();
		controlStream.start();
		return true;
	}
	
	private boolean startVideoStream() throws IOException
	{
		videoStream = new VideoStream(hostAddr, listener, controlStream, config);
		return videoStream.startVideoStream(videoDecoderRenderer, videoRenderTarget, drFlags);
	}
	
	private boolean startAudioStream() throws IOException
	{
		audioStream = new AudioStream(hostAddr, listener, audioRenderer);
		return audioStream.startAudioStream();
	}
	
	private boolean startInputConnection() throws IOException
	{
		// Because input events can be delivered at any time, we must only assign
		// it to the instance variable once the object is properly initialized.
		// This avoids the race where inputStream != null but inputStream.initialize()
		// has not returned yet.
		ControllerStream tempController = new ControllerStream(hostAddr, riKey, riKeyId, listener);
		tempController.initialize();
		tempController.start();
		inputStream = tempController;
		return true;
	}
	
	private void establishConnection() {
		for (NvConnectionListener.Stage currentStage : NvConnectionListener.Stage.values())
		{
			boolean success = false;

			if (currentStage == NvConnectionListener.Stage.LAUNCH_APP) {
				// Display the app name instead of the stage name
				currentStage.setName(config.getApp());
			}
			
			listener.stageStarting(currentStage);
			try {
				switch (currentStage)
				{
				case LAUNCH_APP:
					success = startApp();
					break;

				case RTSP_HANDSHAKE:
					success = doRtspHandshake();
					break;
					
				case CONTROL_START:
					success = startControlStream();
					break;
					
				case VIDEO_START:
					success = startVideoStream();
					break;
					
				case AUDIO_START:
					success = startAudioStream();
					break;
					
				case INPUT_START:
					success = startInputConnection();
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				listener.displayMessage(e.getMessage());
				success = false;
			}
			
			if (success) {
				listener.stageComplete(currentStage);
			}
			else {
				listener.stageFailed(currentStage);
				return;
			}
		}
		
		listener.connectionStarted();
	}

	public void start(String localDeviceName, Object videoRenderTarget, int drFlags, AudioRenderer audioRenderer, VideoDecoderRenderer videoDecoderRenderer)
	{
		this.localDeviceName = localDeviceName;
		this.drFlags = drFlags;
		this.audioRenderer = audioRenderer;
		this.videoRenderTarget = videoRenderTarget;
		this.videoDecoderRenderer = videoDecoderRenderer;
		
		new Thread(new Runnable() {
			public void run() {
				try {
					hostAddr = InetAddress.getByName(host);
				} catch (UnknownHostException e) {
					listener.connectionTerminated(e);
					return;
				}
				
				establishConnection();
			}
		}).start();
	}
	
	public void sendMouseMove(final short deltaX, final short deltaY)
	{
		if (inputStream == null)
			return;
		
		inputStream.sendMouseMove(deltaX, deltaY);
	}
	
	public void sendMouseButtonDown(final byte mouseButton)
	{
		if (inputStream == null)
			return;
		
		inputStream.sendMouseButtonDown(mouseButton);
	}
	
	public void sendMouseButtonUp(final byte mouseButton)
	{
		if (inputStream == null)
			return;
		
		inputStream.sendMouseButtonUp(mouseButton);
	}
	
	public void sendControllerInput(final short buttonFlags,
			final byte leftTrigger, final byte rightTrigger,
			final short leftStickX, final short leftStickY,
			final short rightStickX, final short rightStickY)
	{
		if (inputStream == null)
			return;
		
		inputStream.sendControllerInput(buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY);
	}
	
	public void sendKeyboardInput(final short keyMap, final byte keyDirection, final byte modifier) {
		if (inputStream == null)
			return;
		
		inputStream.sendKeyboardInput(keyMap, keyDirection, modifier);
	}
	
	public void sendMouseScroll(final byte scrollClicks) {
		if (inputStream == null)
			return;
		
		inputStream.sendMouseScroll(scrollClicks);
	}
}
