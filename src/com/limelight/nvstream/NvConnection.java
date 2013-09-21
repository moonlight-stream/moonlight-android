package com.limelight.nvstream;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

public class NvConnection {
	private String host;
	
	public NvConnection(String host)
	{
		this.host = host;
	}
	
	public void doShit() throws XmlPullParserException, IOException
	{
		NvHttp h = new NvHttp(host, "b0:ee:45:57:5d:5f");
		
		System.out.println("Shield Shit");
		System.out.println(h.getAppVersion());
		System.out.println(h.getPairState());
		
		
		int sessionId = h.getSessionId();
		System.out.println("Session ID: "+sessionId);
		int appId = h.getSteamAppId(sessionId);
		System.out.println("Steam app ID: "+appId);
		int gameSession = h.launchApp(sessionId, appId);
		System.out.println("Started game session: "+gameSession);
	}
}
