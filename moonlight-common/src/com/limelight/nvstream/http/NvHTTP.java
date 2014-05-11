package com.limelight.nvstream.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;


public class NvHTTP {
	private String uniqueId;

	public static final int PORT = 47989;
	public static final int CONNECTION_TIMEOUT = 5000;

	public String baseUrl;

	public NvHTTP(InetAddress host, String uniqueId, String deviceName) {
		this.uniqueId = uniqueId;
		
		String safeAddress;
		if (host instanceof Inet6Address) {
			// RFC2732-formatted IPv6 address for use in URL
			safeAddress = "["+host.getHostAddress()+"]";
		}
		else {
			safeAddress = host.getHostAddress();
		}
		
		this.baseUrl = "http://" + safeAddress + ":" + PORT;
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
				if (xpp.getName().equals("root")) {
					verifyResponseStatus(xpp);
				}
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
	
	private void verifyResponseStatus(XmlPullParser xpp) throws GfeHttpResponseException {
		int statusCode = Integer.parseInt(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_code"));
		if (statusCode != 200) {
			throw new GfeHttpResponseException(statusCode, xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_message"));
		}
	}

	private InputStream openHttpConnection(String url) throws IOException {
		URLConnection conn = new URL(url).openConnection();
		conn.setConnectTimeout(CONNECTION_TIMEOUT);
		conn.setDefaultUseCaches(false);
		conn.connect();
		return conn.getInputStream();
	}

	public String getAppVersion() throws XmlPullParserException, IOException {
		InputStream in = openHttpConnection(baseUrl + "/appversion");
		return getXmlString(in, "appversion");
	}

	public boolean getPairState() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/pairstate?uniqueid=" + uniqueId);
		String paired = getXmlString(in, "paired");
		return Integer.valueOf(paired) != 0;
	}

	public int getSessionId() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/pair?uniqueid=" + uniqueId);
		String sessionId = getXmlString(in, "sessionid");
		return Integer.parseInt(sessionId);
	}

	public int getCurrentGame() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/serverinfo");
		String game = getXmlString(in, "currentgame");
		return Integer.parseInt(game);
	}
	
	public NvApp getSteamApp() throws IOException,
			XmlPullParserException {
		LinkedList<NvApp> appList = getAppList();
		for (NvApp app : appList) {
			if (app.getAppName().equals("Steam")) {
				return app;
			}
		}
		return null;
	}
	
	public LinkedList<NvApp> getAppList() throws GfeHttpResponseException, IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/applist?uniqueid=" + uniqueId);
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
				if (xpp.getName().equals("root")) {
					verifyResponseStatus(xpp);
				}
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
	public int launchApp(int appId, int width, int height, int refreshRate) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl +
			"/launch?uniqueid=" + uniqueId +
			"&appid=" + appId +
			"&mode=" + width + "x" + height + "x" + refreshRate);
		String gameSession = getXmlString(in, "gamesession");
		return Integer.parseInt(gameSession);
	}
	
	public boolean resumeApp() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/resume?uniqueid=" + uniqueId);
		String resume = getXmlString(in, "resume");
		return Integer.parseInt(resume) != 0;
	}
	
	public boolean quitApp() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/cancel?uniqueid=" + uniqueId);
		String cancel = getXmlString(in, "cancel");
		return Integer.parseInt(cancel) != 0;
	}
}
