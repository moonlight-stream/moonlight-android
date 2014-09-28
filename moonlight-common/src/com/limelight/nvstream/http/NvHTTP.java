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
import java.util.UUID;

import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.limelight.nvstream.http.PairingManager.PairState;


public class NvHTTP {
	private String uniqueId;
	private PairingManager pm;
	private InetAddress address;

	public static final int PORT = 47984;
	public static final int CONNECTION_TIMEOUT = 3000;
	public static final int READ_TIMEOUT = 5000;
	
	private final boolean verbose = false;

	public String baseUrl;
	
	public NvHTTP(InetAddress host, String uniqueId, String deviceName, LimelightCryptoProvider cryptoProvider) {
		this.uniqueId = uniqueId;
		this.address = host;
		
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
	
	public String getServerInfo(String uniqueId) throws MalformedURLException, IOException {
		return openHttpConnectionToString(baseUrl + "/serverinfo?uniqueid=" + uniqueId, true);
	}
	
	public ComputerDetails getComputerDetails() throws MalformedURLException, IOException, XmlPullParserException {
		ComputerDetails details = new ComputerDetails();
		String serverInfo = getServerInfo(uniqueId);
		
		details.name = getXmlString(serverInfo, "hostname").trim();
		details.uuid = UUID.fromString(getXmlString(serverInfo, "uniqueid").trim());
		details.macAddress = getXmlString(serverInfo, "mac");

		// If there's no LocalIP field, use the address we hit the server on
		String localIpStr = getXmlString(serverInfo, "LocalIP");
		if (localIpStr == null) {
			localIpStr = address.getHostAddress();
		}
		
		// If there's no ExternalIP field, use the address we hit the server on
		String externalIpStr = getXmlString(serverInfo, "ExternalIP");
		if (externalIpStr == null) {
			externalIpStr = address.getHostAddress();
		}
		
		details.localIp = InetAddress.getByName(localIpStr.trim());
		details.remoteIp = InetAddress.getByName(externalIpStr.trim());
		
		try {
			details.pairState = Integer.parseInt(getXmlString(serverInfo, "PairStatus").trim()) == 1 ?
					PairState.PAIRED : PairState.NOT_PAIRED;
		} catch (NumberFormatException e) {
			details.pairState = PairState.FAILED;
		}
		
		try {
			details.runningGameId = Integer.parseInt(getXmlString(serverInfo, "currentgame").trim());
		} catch (NumberFormatException e) {
			details.runningGameId = 0;
		}
		
		// We could reach it so it's online
		details.state = ComputerDetails.State.ONLINE;
		
		return details;
	}

	// Read timeout should be enabled for any HTTP query that requires no outside action
	// on the GFE server. Examples of queries that DO require outside action are launch, resume, and quit.
	// The initial pair query does require outside action (user entering a PIN) but subsequent pairing
	// queries do not.
	private InputStream openHttpConnection(String url, boolean enableReadTimeout) throws IOException {
		URLConnection conn = new URL(url).openConnection();
		if (verbose) {
			System.out.println(url);
		}
		conn.setConnectTimeout(CONNECTION_TIMEOUT);
		if (enableReadTimeout) {
			conn.setReadTimeout(READ_TIMEOUT);
		}
		
		conn.setUseCaches(false);
		conn.connect();
		return conn.getInputStream();
	}
	
	String openHttpConnectionToString(String url, boolean enableReadTimeout) throws MalformedURLException, IOException {
		Scanner s = new Scanner(openHttpConnection(url, enableReadTimeout));
		
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

	public String getServerVersion(String serverInfo) throws XmlPullParserException, IOException {
		return getXmlString(serverInfo, "appversion");
	}

	public PairingManager.PairState getPairState() throws IOException, XmlPullParserException {
		return pm.getPairState(getServerInfo(uniqueId));
	}

	public PairingManager.PairState getPairState(String serverInfo) throws IOException, XmlPullParserException {
		return pm.getPairState(serverInfo);
	}

	public int getCurrentGame(String serverInfo) throws IOException, XmlPullParserException {
		String game = getXmlString(serverInfo, "currentgame");
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
		// FIXME: Investigate whether this should be subject to the 2 second read timeout
		// or not.
		return openHttpConnection(baseUrl + "/applist?uniqueid="+uniqueId+"&appid="+
				app.getAppId()+"&AssetType=2&AssetIdx=0", false);
	}
	
	public LinkedList<NvApp> getAppList() throws GfeHttpResponseException, IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/applist?uniqueid=" + uniqueId, true);
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
	
	public void unpair() throws IOException {
		openHttpConnection(baseUrl + "/unpair?uniqueid=" + uniqueId, true);
	}

	final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	public int launchApp(int appId, int width, int height, int refreshRate, SecretKey inputKey, boolean sops, int riKeyId) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl +
			"/launch?uniqueid=" + uniqueId +
			"&appid=" + appId +
			"&mode=" + width + "x" + height + "x" + refreshRate +
			"&additionalStates=1&sops=" + (sops ? 1 : 0) +
			"&rikey="+bytesToHex(inputKey.getEncoded()) +
			"&rikeyid="+riKeyId, false);
		String gameSession = getXmlString(in, "gamesession");
		return Integer.parseInt(gameSession);
	}
	
	public boolean resumeApp(SecretKey inputKey, int riKeyId) throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/resume?uniqueid=" + uniqueId +
				"&rikey="+bytesToHex(inputKey.getEncoded()) +
				"&rikeyid="+riKeyId, false);
		String resume = getXmlString(in, "resume");
		return Integer.parseInt(resume) != 0;
	}
	
	public boolean quitApp() throws IOException, XmlPullParserException {
		InputStream in = openHttpConnection(baseUrl + "/cancel?uniqueid=" + uniqueId, false);
		String cancel = getXmlString(in, "cancel");
		return Integer.parseInt(cancel) != 0;
	}
}
