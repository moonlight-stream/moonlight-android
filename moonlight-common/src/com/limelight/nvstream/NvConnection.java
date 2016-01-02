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
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;
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
	// Context parameters
	private String host;
	private LimelightCryptoProvider cryptoProvider;
	private String uniqueId;
	private ConnectionContext context;
	
	// Stream objects
	private ControlStream controlStream;
	private ControllerStream inputStream;
	private VideoStream videoStream;
	private AudioStream audioStream;
	
	// Start parameters
	private int drFlags;
	private Object videoRenderTarget;
	private AudioRenderer audioRenderer;
	
	public NvConnection(String host, String uniqueId, NvConnectionListener listener, StreamConfiguration config, LimelightCryptoProvider cryptoProvider)
	{		
		this.host = host;
		this.cryptoProvider = cryptoProvider;
		this.uniqueId = uniqueId;
		
		this.context = new ConnectionContext();
		this.context.connListener = listener;
		this.context.streamConfig = config;
		try {
			// This is unique per connection
			this.context.riKey = generateRiAesKey();
		} catch (NoSuchAlgorithmException e) {
			// Should never happen
			e.printStackTrace();
		}
		
		this.context.riKeyId = generateRiKeyId();
		
		this.context.negotiatedVideoFormat = VideoFormat.Unknown;
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
		NvHTTP h = new NvHTTP(context.serverAddress, uniqueId, null, cryptoProvider);
		
		String serverInfo = h.getServerInfo();
		String serverVersion = h.getServerVersion(serverInfo);
		if (serverVersion == null || serverVersion.indexOf('.') < 0) {
			context.connListener.displayMessage("Server major version not present");
			return false;
		}
		
		try {
			int majorVersion = Integer.parseInt(serverVersion.substring(0, serverVersion.indexOf('.')));
			if (majorVersion < 3) {
				// Even though we support major version 3 (2.1.x), GFE 2.2.2 is preferred.
				context.connListener.displayMessage("This app requires GeForce Experience 2.2.2 or later. Please upgrade GFE on your PC and try again.");
				return false;
			}
			else if (majorVersion > 4) {
				// Warn the user but allow them to continue
				context.connListener.displayTransientMessage("This version of GFE is not currently supported. You may experience issues until this app is updated.");
			}
			
			switch (majorVersion) {
			case 3:
				context.serverGeneration = ConnectionContext.SERVER_GENERATION_3;
				break;
			case 4:
			default:
				context.serverGeneration = ConnectionContext.SERVER_GENERATION_4;
				break;
			}
			
			LimeLog.info("Server major version: "+majorVersion);
		} catch (NumberFormatException e) {
			context.connListener.displayMessage("Server version malformed: "+serverVersion);
			return false;
		}
		
		if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
			context.connListener.displayMessage("Device not paired with computer");
			return false;
		}
		
		//
		// Decide on negotiated stream parameters now
		//
		
		// Check for a supported stream resolution
		if (context.streamConfig.getHeight() >= 2160 && !h.supports4K(serverInfo)) {
			// Client wants 4K but the server can't do it
			context.connListener.displayTransientMessage("Your PC does not have a supported GPU or GFE version for 4K streaming. The stream will be 1080p.");
			
			// Lower resolution to 1080p
			context.negotiatedWidth = 1920;
			context.negotiatedHeight = 1080;
		}
		else {
			// Take what the client wanted
			context.negotiatedWidth = context.streamConfig.getWidth();
			context.negotiatedHeight = context.streamConfig.getHeight();
		}
		
		// For now, always take the client's FPS request
		context.negotiatedFps = context.streamConfig.getRefreshRate();
		
		// Determine whether we should request H.265 video
		String gpuType = h.getGpuType(serverInfo);
		if (context.streamConfig.getHevcSupported() && // Client wants it
				h.getMaxLumaPixelsHEVC(serverInfo) > 0 && gpuType != null && // Check if GFE version supports it
				gpuType.contains("GTX 9")) // Check if GPU can do it (only 900-series) - TODO: Find a better way to detect this
		{
			context.negotiatedVideoFormat = VideoFormat.H265;
		}
		else 
		{
			context.negotiatedVideoFormat = VideoFormat.H264;
		}
		
		NvApp app = context.streamConfig.getApp();
		
		// If the client did not provide an exact app ID, do a lookup with the applist
		if (!context.streamConfig.getApp().isInitialized()) {
			LimeLog.info("Using deprecated app lookup method - Please specify an app ID in your StreamConfiguration instead");
			app = h.getAppByName(context.streamConfig.getApp().getAppName());
			if (app == null) {
				context.connListener.displayMessage("The app " + context.streamConfig.getApp().getAppName() + " is not in GFE app list");
				return false;
			}
		}
		
		// Update the running status of the app
		app.setIsRunning(h.getCurrentGame(serverInfo) == app.getAppId());
		
		// If there's a game running, resume it
		if (h.getCurrentGame(serverInfo) != 0) {
			try {
				if (app.getIsRunning()) {
					if (!h.resumeApp(context)) {
						context.connListener.displayMessage("Failed to resume existing session");
						return false;
					}
				} else if (h.getCurrentGame(serverInfo) != app.getAppId()) {
					return quitAndLaunch(h, app);
				}
			} catch (GfeHttpResponseException e) {
				if (e.getErrorCode() == 470) {
					// This is the error you get when you try to resume a session that's not yours.
					// Because this is fairly common, we'll display a more detailed message.
					context.connListener.displayMessage("This session wasn't started by this device," +
							" so it cannot be resumed. End streaming on the original " +
							"device or the PC itself and try again. (Error code: "+e.getErrorCode()+")");
					return false;
				}
				else if (e.getErrorCode() == 525) {
					context.connListener.displayMessage("The application is minimized. Resume it on the PC manually or " +
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
		try {
			if (!h.quitApp()) {
				context.connListener.displayMessage("Failed to quit previous session! You must quit it manually");
				return false;
			} 
		} catch (GfeHttpResponseException e) {
			if (e.getErrorCode() == 599) {
				context.connListener.displayMessage("This session wasn't started by this device," +
						" so it cannot be quit. End streaming on the original " +
						"device or the PC itself. (Error code: "+e.getErrorCode()+")");
				return false;
			}
			else {
				throw e;
			}
		}

		return launchNotRunningApp(h, app);
	}
	
	private boolean launchNotRunningApp(NvHTTP h, NvApp app) 
			throws IOException, XmlPullParserException {
		// Launch the app since it's not running
		if (!h.launchApp(context, app.getAppId())) {
			context.connListener.displayMessage("Failed to launch application");
			return false;
		}
		
		LimeLog.info("Launched new game session");
		
		return true;
	}
	
	private boolean doRtspHandshake() throws IOException
	{
		RtspConnection r = new RtspConnection(context);
		r.doRtspHandshake();
		return true;
	}
	
	private boolean startControlStream() throws IOException
	{
		controlStream = new ControlStream(context);
		controlStream.initialize();
		controlStream.start();
		return true;
	}
	
	private boolean startVideoStream() throws IOException
	{
		videoStream = new VideoStream(context, controlStream);
		return videoStream.startVideoStream(videoRenderTarget, drFlags);
	}
	
	private boolean startAudioStream() throws IOException
	{
		audioStream = new AudioStream(context, audioRenderer);
		return audioStream.startAudioStream();
	}
	
	private boolean startInputConnection() throws IOException
	{
		// Because input events can be delivered at any time, we must only assign
		// it to the instance variable once the object is properly initialized.
		// This avoids the race where inputStream != null but inputStream.initialize()
		// has not returned yet.
		ControllerStream tempController = new ControllerStream(context);
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
				currentStage.setName(context.streamConfig.getApp().getAppName());
			}
			
			context.connListener.stageStarting(currentStage);
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
				context.connListener.displayMessage(e.getMessage());
				success = false;
			}
			
			if (success) {
				context.connListener.stageComplete(currentStage);
			}
			else {
				context.connListener.stageFailed(currentStage);
				return;
			}
		}
		
		context.connListener.connectionStarted();
	}

	public void start(String localDeviceName, Object videoRenderTarget, int drFlags, AudioRenderer audioRenderer, VideoDecoderRenderer videoDecoderRenderer)
	{
		this.drFlags = drFlags;
		this.audioRenderer = audioRenderer;
		this.videoRenderTarget = videoRenderTarget;
		this.context.videoDecoderRenderer = videoDecoderRenderer;
		
		new Thread(new Runnable() {
			public void run() {
				try {
					context.serverAddress = InetAddress.getByName(host);
				} catch (UnknownHostException e) {
					context.connListener.connectionTerminated(e);
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
	
	public void sendControllerInput(final short controllerNumber,
			final short buttonFlags,
			final byte leftTrigger, final byte rightTrigger,
			final short leftStickX, final short leftStickY,
			final short rightStickX, final short rightStickY)
	{
		if (inputStream == null)
			return;
		
		inputStream.sendControllerInput(controllerNumber, buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY);
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
	
	public VideoFormat getActiveVideoFormat() {
		return context.negotiatedVideoFormat;
	}
}
