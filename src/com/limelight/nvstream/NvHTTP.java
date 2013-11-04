package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class NvHTTP {
	private String macAddress;

	public static final int PORT = 47989;
	public String baseUrl;

	public NvHTTP(String host, String macAddress) {
		this.macAddress = macAddress;
		this.baseUrl = "http://" + host + ":" + PORT;
	}

	private String getXmlString(InputStream in, String tagname)
			throws XmlPullParserException, IOException {
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser xpp = factory.newPullParser();

		xpp.setInput(new InputStreamReader(in));
		int eventType = xpp.getEventType();
		Stack<String> currentTag = new Stack<String>();

		while (eventType != XmlPullParser.END_DOCUMENT) {
			switch (eventType) {
			case (XmlPullParser.START_TAG):
				currentTag.push(xpp.getName());
				break;
			case (XmlPullParser.END_TAG):
				currentTag.pop();
				break;
			case (XmlPullParser.TEXT):
				if (currentTag.peek().equals(tagname)) {
					return xpp.getText();
				}
				break;
			}
			eventType = xpp.next();
		}

		return null;
	}

	private InputStream openHttpConnection(String url) throws IOException {
		return new URL(url).openConnection().getInputStream();
	}

	public String getAppVersion() throws XmlPullParserException, IOException {
		InputStream in = openHttpConnection(baseUrl + "/appversion");
		return getXmlString(in, "appversion");
	}

	public boolean getPairState() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/pairstate?mac=" + macAddress);
		String paired = getXmlString(in, "paired");
		return Integer.valueOf(paired) != 0;
	}

	public int getSessionId() throws IOException, XmlPullParserException {
		/* Pass the model (minus spaces) as the device name */
		String deviceName = android.os.Build.MODEL;
		deviceName = deviceName.replace(" ", "");
		InputStream in = openHttpConnection(baseUrl + "/pair?mac=" + macAddress
				+ "&devicename=" + deviceName);
		String sessionId = getXmlString(in, "sessionid");
		return Integer.parseInt(sessionId);
	}

	public int getSteamAppId(int sessionId) throws IOException,
			XmlPullParserException {
		LinkedList<NvApp> appList = getAppList(sessionId);
		for (NvApp app : appList) {
			if (app.getAppName().equals("Steam")) {
				return app.getAppId();
			}
		}
		return 0;
	}
	
	public LinkedList<NvApp> getAppList(int sessionId) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/applist?session=" + sessionId);
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser xpp = factory.newPullParser();

		xpp.setInput(new InputStreamReader(in));
		int eventType = xpp.getEventType();
		LinkedList<NvApp> appList = new LinkedList<NvApp>();
		Stack<String> currentTag = new Stack<String>();

		while (eventType != XmlPullParser.END_DOCUMENT) {
			switch (eventType) {
			case (XmlPullParser.START_TAG):
				currentTag.push(xpp.getName());
				if (xpp.getName().equals("App")) {
					appList.addLast(new NvApp());
				}
				break;
			case (XmlPullParser.END_TAG):
				currentTag.pop();
				break;
			case (XmlPullParser.TEXT):
				NvApp app = appList.getLast();
				if (currentTag.peek().equals("AppTitle")) {
					app.setAppName(xpp.getText());
				} else if (currentTag.peek().equals("ID")) {
					app.setAppId(xpp.getText());
				} else if (currentTag.peek().equals("IsRunning")) {
					app.setIsRunning(xpp.getText());
				}
				break;
			}
			eventType = xpp.next();
		}
		return appList;
	}

	// Returns gameSession XML attribute
	public int launchApp(int sessionId, int appId) throws IOException,
			XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/launch?session="
			+ sessionId + "&appid=" + appId);
		String gameSession = getXmlString(in, "gamesession");
		return Integer.parseInt(gameSession);
	}
}
