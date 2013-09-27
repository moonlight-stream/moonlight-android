package com.limelight.nvstream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.view.Surface;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.nvstream.input.NvController;

public class NvConnection {
	private String host;
	private Activity activity;
	
	private NvControl controlStream;
	private NvController inputStream;
	private Surface video;
	
	private ThreadPoolExecutor threadPool;
	
	public NvConnection(String host, Activity activity, Surface video)
	{
		this.host = host;
		this.activity = activity;
		this.video = video;
		this.threadPool = new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
	}

	public void start()
	{	
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					host = InetAddress.getByName(host).getHostAddress();
				} catch (UnknownHostException e) {
					e.printStackTrace();
					displayToast(e.getMessage());
					return;
				}
				
				try {
					startSteamBigPicture();
					performHandshake();
					startVideo(video);
					beginControlStream();
					controlStream.startJitterPackets();
					startController();
				} catch (XmlPullParserException e) {
					e.printStackTrace();
					displayToast(e.getMessage());
				} catch (IOException e) {
					e.printStackTrace();
					displayToast(e.getMessage());
				}
			}
		}).start();
	}
	
	public void startVideo(Surface surface)
	{
		new NvVideoStream().startVideoStream(host, surface);
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
					e.printStackTrace();
					displayToast(e.getMessage());
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
					e.printStackTrace();
					displayToast(e.getMessage());
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
					e.printStackTrace();
					displayToast(e.getMessage());
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
					e.printStackTrace();
					displayToast(e.getMessage());
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
	
	private void startSteamBigPicture() throws XmlPullParserException, IOException
	{
		NvHttp h = new NvHttp(host, "b0:ee:45:57:5d:5f");
		
		if (!h.getPairState())
		{
			displayToast("Device not paired with computer");
			return;
		}
		
		int sessionId = h.getSessionId();
		int appId = h.getSteamAppId(sessionId);
		
		System.out.println("Starting game session");
		int gameSession = h.launchApp(sessionId, appId);
		System.out.println("Started game session: "+gameSession);
	}
	
	private void performHandshake() throws UnknownHostException, IOException
	{
		System.out.println("Starting handshake");
		NvHandshake.performHandshake(host);
		System.out.println("Handshake complete");
	}
	
	private void beginControlStream() throws UnknownHostException, IOException
	{
		controlStream = new NvControl(host);
		
		System.out.println("Starting control");
		controlStream.beginControl();
	}
	
	private void startController() throws UnknownHostException, IOException
	{
		System.out.println("Starting input");
		inputStream = new NvController(host);
	}
}
