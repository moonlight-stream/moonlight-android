package com.limelight.nvstream;

import android.app.ActivityManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Semaphore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;

public class NvConnection {
	// Context parameters
	private String host;
	private LimelightCryptoProvider cryptoProvider;
	private String uniqueId;
	private ConnectionContext context;
	private static Semaphore connectionAllowed = new Semaphore(1);
	private final boolean isMonkey;
	
	public NvConnection(String host, String uniqueId, StreamConfiguration config, LimelightCryptoProvider cryptoProvider, X509Certificate serverCert)
	{		
		this.host = host;
		this.cryptoProvider = cryptoProvider;
		this.uniqueId = uniqueId;

		this.context = new ConnectionContext();
		this.context.streamConfig = config;
		this.context.serverCert = serverCert;
		try {
			// This is unique per connection
			this.context.riKey = generateRiAesKey();
		} catch (NoSuchAlgorithmException e) {
			// Should never happen
			e.printStackTrace();
		}
		
		this.context.riKeyId = generateRiKeyId();
		this.isMonkey = ActivityManager.isUserAMonkey();
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

	public void stop() {
		// Interrupt any pending connection. This is thread-safe.
		MoonBridge.interruptConnection();

		// Moonlight-core is not thread-safe with respect to connection start and stop, so
		// we must not invoke that functionality in parallel.
		synchronized (MoonBridge.class) {
			MoonBridge.stopConnection();
			MoonBridge.cleanupBridge();
		}

		// Now a pending connection can be processed
		connectionAllowed.release();
	}
	
	private boolean startApp() throws XmlPullParserException, IOException
	{
		NvHTTP h = new NvHTTP(context.serverAddress, uniqueId, context.serverCert, cryptoProvider);

		String serverInfo = h.getServerInfo();
		
		context.serverAppVersion = h.getServerVersion(serverInfo);
		if (context.serverAppVersion == null) {
			context.connListener.displayMessage("Server version malformed");
			return false;
		}

		// May be missing for older servers
		context.serverGfeVersion = h.getGfeVersion(serverInfo);
				
		if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
			context.connListener.displayMessage("Device not paired with computer");
			return false;
		}

		context.negotiatedHdr = context.streamConfig.getEnableHdr();
		if ((h.getServerCodecModeSupport(serverInfo) & 0x200) == 0 && context.negotiatedHdr) {
			context.connListener.displayTransientMessage("Your GPU does not support streaming HDR. The stream will be SDR.");
			context.negotiatedHdr = false;
		}
		
		//
		// Decide on negotiated stream parameters now
		//
		
		// Check for a supported stream resolution
		if (context.streamConfig.getHeight() >= 2160 && !h.supports4K(serverInfo)) {
			// Client wants 4K but the server can't do it
			context.connListener.displayTransientMessage("You must update GeForce Experience to stream in 4K. The stream will be 1080p.");
			
			// Lower resolution to 1080p
			context.negotiatedWidth = 1920;
			context.negotiatedHeight = 1080;
			context.negotiatedFps = context.streamConfig.getRefreshRate();
		}
		else {
			// Take what the client wanted
			context.negotiatedWidth = context.streamConfig.getWidth();
			context.negotiatedHeight = context.streamConfig.getHeight();
			context.negotiatedFps = context.streamConfig.getRefreshRate();
		}
		
		//
		// Video stream format will be decided during the RTSP handshake
		//
		
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
		
		// If there's a game running, resume it
		if (h.getCurrentGame(serverInfo) != 0) {
			try {
				if (h.getCurrentGame(serverInfo) == app.getAppId()) {
					if (!h.resumeApp(context)) {
						context.connListener.displayMessage("Failed to resume existing session");
						return false;
					}
				} else {
					return quitAndLaunch(h, context);
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
			return launchNotRunningApp(h, context);
		}
	}

	protected boolean quitAndLaunch(NvHTTP h, ConnectionContext context) throws IOException,
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

		return launchNotRunningApp(h, context);
	}
	
	private boolean launchNotRunningApp(NvHTTP h, ConnectionContext context)
			throws IOException, XmlPullParserException {
		// Launch the app since it's not running
		if (!h.launchApp(context, context.streamConfig.getApp().getAppId(), context.negotiatedHdr)) {
			context.connListener.displayMessage("Failed to launch application");
			return false;
		}
		
		LimeLog.info("Launched new game session");
		
		return true;
	}

	public void start(final AudioRenderer audioRenderer, final VideoDecoderRenderer videoDecoderRenderer, final NvConnectionListener connectionListener)
	{
		new Thread(new Runnable() {
			public void run() {
				context.connListener = connectionListener;
				context.videoCapabilities = videoDecoderRenderer.getCapabilities();

				String appName = context.streamConfig.getApp().getAppName();

				context.serverAddress = host;
				context.connListener.stageStarting(appName);

				try {
					if (!startApp()) {
						context.connListener.stageFailed(appName, 0);
						return;
					}
					context.connListener.stageComplete(appName);
				} catch (XmlPullParserException | IOException e) {
					e.printStackTrace();
					context.connListener.displayMessage(e.getMessage());
					context.connListener.stageFailed(appName, 0);
					return;
				}

				ByteBuffer ib = ByteBuffer.allocate(16);
				ib.putInt(context.riKeyId);

				// Acquire the connection semaphore to ensure we only have one
				// connection going at once.
				try {
					connectionAllowed.acquire();
				} catch (InterruptedException e) {
					context.connListener.displayMessage(e.getMessage());
					context.connListener.stageFailed(appName, 0);
					return;
				}

				// Moonlight-core is not thread-safe with respect to connection start and stop, so
				// we must not invoke that functionality in parallel.
				synchronized (MoonBridge.class) {
					MoonBridge.setupBridge(videoDecoderRenderer, audioRenderer, connectionListener);
					int ret = MoonBridge.startConnection(context.serverAddress,
							context.serverAppVersion, context.serverGfeVersion,
							context.negotiatedWidth, context.negotiatedHeight,
							context.negotiatedFps, context.streamConfig.getBitrate(),
							context.streamConfig.getMaxPacketSize(),
							context.streamConfig.getRemote(), context.streamConfig.getAudioConfiguration(),
							context.streamConfig.getHevcSupported(),
							context.negotiatedHdr,
							context.streamConfig.getHevcBitratePercentageMultiplier(),
							context.streamConfig.getClientRefreshRateX100(),
							context.riKey.getEncoded(), ib.array(),
							context.videoCapabilities);
					if (ret != 0) {
						// LiStartConnection() failed, so the caller is not expected
						// to stop the connection themselves. We need to release their
						// semaphore count for them.
						connectionAllowed.release();
					}
				}
			}
		}).start();
	}
	
	public void sendMouseMove(final short deltaX, final short deltaY)
	{
		if (!isMonkey) {
			MoonBridge.sendMouseMove(deltaX, deltaY);
		}
	}
	
	public void sendMouseButtonDown(final byte mouseButton)
	{
		if (!isMonkey) {
			MoonBridge.sendMouseButton(MouseButtonPacket.PRESS_EVENT, mouseButton);
		}
	}
	
	public void sendMouseButtonUp(final byte mouseButton)
	{
		if (!isMonkey) {
			MoonBridge.sendMouseButton(MouseButtonPacket.RELEASE_EVENT, mouseButton);
		}
	}
	
	public void sendControllerInput(final short controllerNumber,
			final short activeGamepadMask, final short buttonFlags,
			final byte leftTrigger, final byte rightTrigger,
			final short leftStickX, final short leftStickY,
			final short rightStickX, final short rightStickY)
	{
		if (!isMonkey) {
			MoonBridge.sendMultiControllerInput(controllerNumber, activeGamepadMask, buttonFlags,
			        leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
		}
	}
	
	public void sendControllerInput(final short buttonFlags,
			final byte leftTrigger, final byte rightTrigger,
			final short leftStickX, final short leftStickY,
			final short rightStickX, final short rightStickY)
	{
		if (!isMonkey) {
			MoonBridge.sendControllerInput(buttonFlags, leftTrigger, rightTrigger, leftStickX,
			        leftStickY, rightStickX, rightStickY);
		}
	}
	
	public void sendKeyboardInput(final short keyMap, final byte keyDirection, final byte modifier) {
		if (!isMonkey) {
			MoonBridge.sendKeyboardInput(keyMap, keyDirection, modifier);
		}
	}
	
	public void sendMouseScroll(final byte scrollClicks) {
		if (!isMonkey) {
			MoonBridge.sendMouseScroll(scrollClicks);
		}
	}

	public static String findExternalAddressForMdns(String stunHostname, int stunPort) {
		return MoonBridge.findExternalAddressIP4(stunHostname, stunPort);
	}
}
