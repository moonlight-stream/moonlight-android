package com.limelight.nvstream;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.input.NvController;

public class NvConnection {
	private String host;
	
	public NvConnection(String host)
	{
		this.host = host;
	}
	
	private void delay(int ms)
	{
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			return;
		}
	}
	
	public void doShit() throws XmlPullParserException, IOException
	{
		NvHttp h = new NvHttp(host, "b0:ee:45:57:5d:5f");
		
		System.out.println("Begin Shield Action"); 
		System.out.println(h.getAppVersion());
		System.out.println(h.getPairState());
		
		int sessionId = h.getSessionId();
		System.out.println("Session ID: "+sessionId);
		int appId = h.getSteamAppId(sessionId);
		System.out.println("Steam app ID: "+appId);
		int gameSession = h.launchApp(sessionId, appId);
		System.out.println("Started game session: "+gameSession);
		
		System.out.println("Starting handshake");
		NvHandshake.performHandshake(host);
		System.out.println("Handshake complete");
		
		NvControl nvC = new NvControl(host);
		
		System.out.println("Starting control");
		nvC.beginControl();
		
		System.out.println("Startup controller");
		NvController controller = new NvController(host);
		
		// Wait 3 seconds to start input
		delay(3000);
		
		System.out.println("Beginning controller input");
		controller.sendLeftButton();
		delay(100);
		controller.clearButtons();
		delay(250);
		controller.sendRightButton();
		delay(100);
		controller.clearButtons();
		delay(250);
		controller.sendRightButton();
		delay(100);
		controller.clearButtons();
		delay(250);
		controller.sendRightButton();
		delay(100);
		controller.clearButtons();
		delay(250);
		controller.sendLeftButton();
		delay(100);
		controller.clearButtons();
		
		new NvAudioStream().start();
		new NvVideoStream().start(host);
	}
}
