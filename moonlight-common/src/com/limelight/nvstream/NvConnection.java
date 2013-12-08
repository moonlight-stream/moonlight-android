package com.limelight.nvstream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.av.audio.AudioStream;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoStream;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.input.NvController;

public class NvConnection {
	private String host;
	private NvConnectionListener listener;
	
	private InetAddress hostAddr;
	private ControlStream controlStream;
	private NvController inputStream;
	private VideoStream videoStream;
	private AudioStream audioStream;
	
	// Start parameters
	private int drFlags;
	private Object videoRenderTarget;
	private VideoDecoderRenderer videoDecoderRenderer;
	private AudioRenderer audioRenderer;
	private String localDeviceName;
	
	private ThreadPoolExecutor threadPool;
	
	public NvConnection(String host, NvConnectionListener listener)
	{
		this.host = host;
		this.listener = listener;
		this.threadPool = new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
	}
	
	public static String getMacAddressString() throws SocketException {
		Enumeration<NetworkInterface> ifaceList;
		NetworkInterface selectedIface = null;

		// First look for a WLAN interface (since those generally aren't removable)
		ifaceList = NetworkInterface.getNetworkInterfaces();
		while (selectedIface == null && ifaceList.hasMoreElements()) {
			NetworkInterface iface = ifaceList.nextElement();

			if (iface.getName().startsWith("wlan") && 
				iface.getHardwareAddress() != null) {
				selectedIface = iface;
			}
		}

		// If we didn't find that, look for an Ethernet interface
		ifaceList = NetworkInterface.getNetworkInterfaces();
		while (selectedIface == null && ifaceList.hasMoreElements()) {
			NetworkInterface iface = ifaceList.nextElement();

			if (iface.getName().startsWith("eth") &&
				iface.getHardwareAddress() != null) {
				selectedIface = iface;
			}
		}
		
		// Now just find something with a MAC address
		ifaceList = NetworkInterface.getNetworkInterfaces();
		while (selectedIface == null && ifaceList.hasMoreElements()) {
			NetworkInterface iface = ifaceList.nextElement();

			if (iface.getHardwareAddress() != null) {
				selectedIface = iface;
				break;
			}
		}
		
		if (selectedIface == null) {
			return null;
		}

		byte[] macAddress = selectedIface.getHardwareAddress();
		if (macAddress != null) {
			StringBuilder addrStr = new StringBuilder();
			for (int i = 0; i < macAddress.length; i++) {
				addrStr.append(String.format("%02x", macAddress[i]));
				if (i != macAddress.length - 1) {
					addrStr.append(':');
				}
			}
			return addrStr.toString();
		}
		
		return null;
	}
	
	public void stop()
	{
		threadPool.shutdownNow();
		
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
			inputStream.close();
			inputStream = null;
		}
	}
	
	private boolean startSteamBigPicture() throws XmlPullParserException, IOException
	{
		NvHTTP h = new NvHTTP(hostAddr, getMacAddressString(), localDeviceName);
		
		if (!h.getPairState()) {
			listener.displayMessage("Device not paired with computer");
			return false;
		}
		
		int sessionId = h.getSessionId();
		int appId = h.getSteamAppId(sessionId);
		
		h.launchApp(sessionId, appId);
		
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
		videoStream = new VideoStream(hostAddr, listener, controlStream);
		videoStream.startVideoStream(videoDecoderRenderer, videoRenderTarget, drFlags);
		return true;
	}
	
	private boolean startAudioStream() throws IOException
	{
		audioStream = new AudioStream(hostAddr, listener, audioRenderer);
		audioStream.startAudioStream();
		return true;
	}
	
	private boolean startInputConnection() throws IOException
	{
		inputStream = new NvController(hostAddr);
		inputStream.initialize();
		return true;
	}
	
	private void establishConnection() {
		for (NvConnectionListener.Stage currentStage : NvConnectionListener.Stage.values())
		{
			boolean success = false;

			listener.stageStarting(currentStage);
			try {
				switch (currentStage)
				{
				case LAUNCH_APP:
					success = startSteamBigPicture();
					break;

				case HANDSHAKE:
					success = Handshake.performHandshake(hostAddr);
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
					
				case CONTROL_START2:
					controlStream.startJitterPackets();
					success = true;
					break;
					
				case INPUT_START:
					success = startInputConnection();
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
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
			@Override
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
		
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					inputStream.sendMouseMove(deltaX, deltaY);
				} catch (IOException e) {
					listener.connectionTerminated(e);
				}
			}
		});
	}
	
	public void sendMouseButtonDown()
	{
		if (inputStream == null)
			return;
		
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					inputStream.sendMouseButtonDown();
				} catch (IOException e) {
					listener.connectionTerminated(e);
				}
			}
		});
	}
	
	public void sendMouseButtonUp()
	{
		if (inputStream == null)
			return;
		
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					inputStream.sendMouseButtonUp();
				} catch (IOException e) {
					listener.connectionTerminated(e);
				}
			}
		});
	}
	
	public void sendControllerInput(final short buttonFlags,
			final byte leftTrigger, final byte rightTrigger,
			final short leftStickX, final short leftStickY,
			final short rightStickX, final short rightStickY)
	{
		if (inputStream == null)
			return;
		
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					inputStream.sendControllerInput(buttonFlags, leftTrigger,
							rightTrigger, leftStickX, leftStickY,
							rightStickX, rightStickY);
				} catch (IOException e) {
					listener.connectionTerminated(e);
				}
			}
		});
	}
	
	public void sendKeyboardInput(final short keyMap, final byte keyDirection) {
		if (inputStream == null)
			return;
		
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					inputStream.sendKeyboardInput(keyMap, keyDirection);
				} catch (IOException e) {
					listener.displayMessage(e.getMessage());
					NvConnection.this.stop();
				}
			}
		});
	}
}
