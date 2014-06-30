package com.limelight.nvstream.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Stack;

import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;


public class NvHTTP {
	private String uniqueId;
	private PairingManager pm;
	private LimelightCryptoProvider cryptoProvider;

	public static final int PORT = 47984;
	public static final int CONNECTION_TIMEOUT = 5000;
	
	private final boolean verbose = false;

	public String baseUrl;
	
	public NvHTTP(InetAddress host, String uniqueId, String deviceName, LimelightCryptoProvider cryptoProvider) {
		this.uniqueId = uniqueId;
		this.cryptoProvider = cryptoProvider;
		
		String safeAddress;
		if (host instanceof Inet6Address) {
			// RFC2732-formatted IPv6 address for use in URL
			safeAddress = "["+host.getHostAddress()+"]";
		}
		else {
			safeAddress = host.getHostAddress();
		}
		
		this.baseUrl = "https://" + safeAddress + ":" + PORT;
		this.pm = new PairingManager(this, cryptoProvider);
	}
	
	static String getXmlString(Reader r, String tagname) throws XmlPullParserException, IOException {
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser xpp = factory.newPullParser();

		xpp.setInput(r);
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

	static String getXmlString(String str, String tagname) throws XmlPullParserException, IOException {
		return getXmlString(new StringReader(str), tagname);
	}
	
	static String getXmlString(InputStream in, String tagname) throws XmlPullParserException, IOException {
		return getXmlString(new InputStreamReader(in), tagname);
	}
	
	private static void verifyResponseStatus(XmlPullParser xpp) throws GfeHttpResponseException {
		int statusCode = Integer.parseInt(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_code"));
		if (statusCode != 200) {
			throw new GfeHttpResponseException(statusCode, xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_message"));
		}
	}

	private InputStream openHttpConnection(String url) throws IOException {
		URLConnection conn = new URL(url).openConnection();
		if (verbose) {
			System.out.println(url);
		}
		conn.setConnectTimeout(CONNECTION_TIMEOUT);
		conn.setUseCaches(false);
		conn.connect();
		return conn.getInputStream();
	}
	
	String openHttpConnectionToString(String url) throws MalformedURLException, IOException {
		Scanner s = new Scanner(openHttpConnection(url));
		
		String str = "";
		while (s.hasNext()) {
			str += s.next() + " ";
		}
		
		s.close();
		
		if (verbose) {
			System.out.println(str);
		}
		
		return str;
	}

	public String getServerVersion() throws XmlPullParserException, IOException {
		InputStream in = openHttpConnection(baseUrl + "/serverinfo?uniqueid=" + uniqueId);
		return getXmlString(in, "appversion");
	}

	public PairingManager.PairState getPairState() throws IOException, XmlPullParserException {
		return pm.getPairState(uniqueId);
	}

	public int getCurrentGame() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/serverinfo?uniqueid=" + uniqueId);
		String game = getXmlString(in, "currentgame");
		return Integer.parseInt(game);
	}
	
	public NvApp getApp(String app) throws IOException,
			XmlPullParserException {
		LinkedList<NvApp> appList = getAppList();
		for (NvApp appFromList : appList) {
			if (appFromList.getAppName().equals(app)) {
				return appFromList;
			}
		}
		return null;
	}
	
	public PairingManager.PairState pair(String pin) throws Exception {
		return pm.pair(uniqueId, pin);
	}
	
	public InputStream getBoxArtPng(NvApp app) throws IOException {
		return openHttpConnection(baseUrl + "/applist?uniqueid="+uniqueId+"&appid="+
				app.getAppId()+"&AssetType=2&AssetIdx=0");
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

	public int launchApp(int appId, int width, int height, int refreshRate, SecretKey inputKey) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl +
			"/launch?uniqueid=" + uniqueId +
			"&appid=" + appId +
			"&mode=" + width + "x" + height + "x" + refreshRate +
			"&additionalStates=1&sops=1&rikey="+cryptoProvider.encodeBase64String(inputKey.getEncoded()));
		String gameSession = getXmlString(in, "gamesession");
		return Integer.parseInt(gameSession);
	}
	
	public boolean resumeApp(SecretKey inputKey) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/resume?uniqueid=" + uniqueId +
				"&rikey="+cryptoProvider.encodeBase64String(inputKey.getEncoded()));
		String resume = getXmlString(in, "resume");
		return Integer.parseInt(resume) != 0;
	}
	
	public boolean quitApp() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/cancel?uniqueid=" + uniqueId);
		String cancel = getXmlString(in, "cancel");
		return Integer.parseInt(cancel) != 0;
	}
}
