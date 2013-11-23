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

import android.content.Context;
import android.net.ConnectivityManager;
import android.view.Surface;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.nvstream.input.NvController;

public class NvConnection {
	private String host;
	private Game activity;
	private NvConnectionListener listener;
	
	private InetAddress hostAddr;
	private NvControl controlStream;
	private NvController inputStream;
	private Surface video;
	private NvVideoStream videoStream;
	private NvAudioStream audioStream;
	
	private ThreadPoolExecutor threadPool;
	
	public NvConnection(String host, Game activity, Surface video)
	{
		this.host = host;
		this.listener = activity;
		this.activity = activity;
		this.video = video;
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
			selectedIface = ifaceList.nextElement();
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
		NvHTTP h = new NvHTTP(hostAddr, getMacAddressString());
		
		if (!h.getPairState()) {
			displayToast("Device not paired with computer");
			return false;
		}
		
		int sessionId = h.getSessionId();
		int appId = h.getSteamAppId(sessionId);
		
		h.launchApp(sessionId, appId);
		
		return true;
	}
	
	private boolean startControlStream() throws IOException
	{
		controlStream = new NvControl(hostAddr, listener);
		controlStream.initialize();
		controlStream.start();
		return true;
	}
	
	private boolean startVideoStream() throws IOException
	{
		videoStream = new NvVideoStream(hostAddr, listener, controlStream);
		videoStream.startVideoStream(video);
		return true;
	}
	
	private boolean startAudioStream() throws IOException
	{
		audioStream = new NvAudioStream(hostAddr, listener);
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
					success = NvHandshake.performHandshake(hostAddr);
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

	public void start()
	{	
		new Thread(new Runnable() {
			@Override
			public void run() {
				checkDataConnection();
				
				try {
					hostAddr = InetAddress.getByName(host);
				} catch (UnknownHostException e) {
					displayToast(e.getMessage());
					listener.connectionTerminated(e);
					return;
				}
				
				establishConnection();
				
				activity.hideSystemUi();
			}
		}).start();
	}
	
	private void checkDataConnection()
	{
		ConnectivityManager mgr = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (mgr.isActiveNetworkMetered()) {
			displayToast("Warning: Your active network connection is metered!");
		}
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
	
	private void displayToast(final String message)
	{
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
			}
		});
	}
}
