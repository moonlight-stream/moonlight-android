package com.limelight.nvstream.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Scanner;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.moonlight_stream.moonlight_common.BuildConfig;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;


public class NvHTTP {
	private String uniqueId;
	private PairingManager pm;
	private String address;

	public static final int HTTPS_PORT = 47984;
	public static final int HTTP_PORT = 47989;
	public static final int CONNECTION_TIMEOUT = 3000;
	public static final int READ_TIMEOUT = 5000;

	// Print URL and content to logcat on debug builds
	private static boolean verbose = BuildConfig.DEBUG;

	public String baseUrlHttps;
	public String baseUrlHttp;
	
	private OkHttpClient httpClient = new OkHttpClient();
	private OkHttpClient httpClientWithReadTimeout;
		
	private TrustManager[] trustAllCerts;
	private KeyManager[] ourKeyman;
	
	private void initializeHttpState(final LimelightCryptoProvider cryptoProvider) {
		trustAllCerts = new TrustManager[] { 
				new X509TrustManager() {
					public X509Certificate[] getAcceptedIssuers() { 
						return new X509Certificate[0]; 
					}
					public void checkClientTrusted(X509Certificate[] certs, String authType) {}
					public void checkServerTrusted(X509Certificate[] certs, String authType) {}
				}};

		ourKeyman = new KeyManager[] {
				new X509KeyManager() {
					public String chooseClientAlias(String[] keyTypes,
							Principal[] issuers, Socket socket) { return "Limelight-RSA"; }
					public String chooseServerAlias(String keyType, Principal[] issuers,
							Socket socket) { return null; }
					public X509Certificate[] getCertificateChain(String alias) {
						return new X509Certificate[] {cryptoProvider.getClientCertificate()};
					}
					public String[] getClientAliases(String keyType, Principal[] issuers) { return null; }
					public PrivateKey getPrivateKey(String alias) {
						return cryptoProvider.getClientPrivateKey();
					}
					public String[] getServerAliases(String keyType, Principal[] issuers) { return null; }
				}
		};

		// Ignore differences between given hostname and certificate hostname
		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) { return true; }
		};
		
		httpClient.setConnectionPool(new ConnectionPool(0, 1));
		httpClient.setHostnameVerifier(hv);
		httpClient.setReadTimeout(0, TimeUnit.MILLISECONDS);
		httpClient.setConnectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
		
		httpClientWithReadTimeout = httpClient.clone();
		httpClientWithReadTimeout.setReadTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS);
	}
	
	public NvHTTP(String address, String uniqueId, String deviceName, LimelightCryptoProvider cryptoProvider) throws IOException {
		this.uniqueId = uniqueId;
		this.address = address;
		
		initializeHttpState(cryptoProvider);

		try {
			// The URI constructor takes care of escaping IPv6 literals
			this.baseUrlHttps = new URI("https", null, address, HTTPS_PORT, null, null, null).toString();
			this.baseUrlHttp = new URI("http", null, address, HTTP_PORT, null, null, null).toString();
		} catch (URISyntaxException e) {
			// Encapsulate URISyntaxException into IOException for callers to handle more easily
			throw new IOException(e);
		}

		this.pm = new PairingManager(this, cryptoProvider);
	}
	
	String buildUniqueIdUuidString() {
		return "uniqueid="+uniqueId+"&uuid="+UUID.randomUUID();
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
					return xpp.getText().trim();
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
	
	public String getServerInfo() throws MalformedURLException, IOException, XmlPullParserException {
		String resp;
		
		//
		// TODO: Shield Hub uses HTTP for this and is able to get an accurate PairStatus with HTTP.
		// For some reason, we always see PairStatus is 0 over HTTP and only 1 over HTTPS. It looks
		// like there are extra request headers required to make this stuff work over HTTP.
		//
		
		try {
			resp = openHttpConnectionToString(baseUrlHttps + "/serverinfo?"+buildUniqueIdUuidString(), true);
			
			// This will throw an exception if the request came back with a failure status.
			// We want this because it will throw us into the HTTP case if the client is unpaired.
			getServerVersion(resp);
		}
		catch (GfeHttpResponseException e) {
			if (e.getErrorCode() == 401) {
				// Cert validation error - fall back to HTTP
				return openHttpConnectionToString(baseUrlHttp + "/serverinfo", true);
			}
			
			// If it's not a cert validation error, throw it
			throw e;
		}
		return resp;
	}
	
	public ComputerDetails getComputerDetails() throws MalformedURLException, IOException, XmlPullParserException {
		ComputerDetails details = new ComputerDetails();
		String serverInfo = getServerInfo();
		
		details.name = getXmlString(serverInfo, "hostname");
		if (details.name == null || details.name.isEmpty()) {
			details.name = "UNKNOWN";
		}

		details.uuid = UUID.fromString(getXmlString(serverInfo, "uniqueid"));
		details.macAddress = getXmlString(serverInfo, "mac");

		// If there's no LocalIP field, use the address we hit the server on
		details.localAddress = getXmlString(serverInfo, "LocalIP");
		if (details.localAddress == null) {
			details.localAddress = address;
		}
		
		// If there's no ExternalIP field, use the address we hit the server on
		details.remoteAddress = getXmlString(serverInfo, "ExternalIP");
		if (details.remoteAddress == null) {
			details.remoteAddress = address;
		}
		
		try {
			details.pairState = Integer.parseInt(getXmlString(serverInfo, "PairStatus")) == 1 ?
					PairState.PAIRED : PairState.NOT_PAIRED;
		} catch (NumberFormatException e) {
			details.pairState = PairState.FAILED;
		}
		
		try {
			details.runningGameId = getCurrentGame(serverInfo);
		} catch (NumberFormatException e) {
			details.runningGameId = 0;
		}
		
		// We could reach it so it's online
		details.state = ComputerDetails.State.ONLINE;
		
		return details;
	}
	
	// This hack is Android-specific but we do it on all platforms
	// because it doesn't really matter
	private void performAndroidTlsHack(OkHttpClient client) {
		// Doing this each time we create a socket is required
		// to avoid the SSLv3 fallback that causes connection failures
		try {
			SSLContext sc = SSLContext.getInstance("TLSv1");
			sc.init(ourKeyman, trustAllCerts, new SecureRandom());
			
			client.setSslSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Read timeout should be enabled for any HTTP query that requires no outside action
	// on the GFE server. Examples of queries that DO require outside action are launch, resume, and quit.
	// The initial pair query does require outside action (user entering a PIN) but subsequent pairing
	// queries do not.
	private ResponseBody openHttpConnection(String url, boolean enableReadTimeout) throws IOException {
		Request request = new Request.Builder().url(url).build();
		Response response;
		
		if (enableReadTimeout) {
			performAndroidTlsHack(httpClientWithReadTimeout);
			response = httpClientWithReadTimeout.newCall(request).execute();
		}
		else {
			performAndroidTlsHack(httpClient);
			response = httpClient.newCall(request).execute();
		}
		
		ResponseBody body = response.body();
		
		if (response.isSuccessful()) {
			return body;
		}
		
		// Unsuccessful, so close the response body
		try {
			if (body != null) {
				body.close();
			}
		} catch (IOException e) {}
		
		if (response.code() == 404) {
			throw new FileNotFoundException(url);
		}
		else {
			throw new IOException("HTTP request failed: "+response.code());
		}
	}
	
	String openHttpConnectionToString(String url, boolean enableReadTimeout) throws MalformedURLException, IOException {
		if (verbose) {
			LimeLog.info("Requesting URL: "+url);
		}
		
		ResponseBody resp;
		try {
			resp = openHttpConnection(url, enableReadTimeout);
		} catch (IOException e) {
			if (verbose) {
				e.printStackTrace();
			}
			
			throw e;
		}
		
		StringBuilder strb = new StringBuilder();
		try {
			Scanner s = new Scanner(resp.byteStream());
			try {
				while (s.hasNext()) {
					strb.append(s.next());
					strb.append(' ');
				}
			} finally {
				s.close();
			}
		} finally {
			resp.close();
		}
		
		if (verbose) {
			LimeLog.info(url+" -> "+strb.toString());
		}
		
		return strb.toString();
	}

	public String getServerVersion(String serverInfo) throws XmlPullParserException, IOException {
		return getXmlString(serverInfo, "appversion");
	}

	public PairingManager.PairState getPairState() throws IOException, XmlPullParserException {
		return pm.getPairState(getServerInfo());
	}

	public PairingManager.PairState getPairState(String serverInfo) throws IOException, XmlPullParserException {
		return pm.getPairState(serverInfo);
	}
	
	public long getMaxLumaPixelsH264(String serverInfo) throws XmlPullParserException, IOException {
		String str = getXmlString(serverInfo, "MaxLumaPixelsH264");
		if (str != null) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}
	
	public long getMaxLumaPixelsHEVC(String serverInfo) throws XmlPullParserException, IOException {
		String str = getXmlString(serverInfo, "MaxLumaPixelsHEVC");
		if (str != null) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}

	// Possible meaning of bits
	// Bit 0: H.264 Baseline
	// Bit 1: H.264 High
	// ----
	// Bit 8: HEVC Main
	// Bit 9: HEVC Main10
	// Bit 10: HEVC Main10 4:4:4
	// Bit 11: ???
	public long getServerCodecModeSupport(String serverInfo) throws XmlPullParserException, IOException {
		String str = getXmlString(serverInfo, "ServerCodecModeSupport");
		if (str != null) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}
	
	public String getGpuType(String serverInfo) throws XmlPullParserException, IOException {
		return getXmlString(serverInfo, "gputype");
	}

	public String getGfeVersion(String serverInfo) throws XmlPullParserException, IOException {
		return getXmlString(serverInfo, "GfeVersion");
	}
	
	public boolean supports4K(String serverInfo) throws XmlPullParserException, IOException {
		// serverinfo returns supported resolutions in descending order, so getting the first
		// height will give us whether we support 4K. If this is not present, we don't support
		// 4K.
		String heightStr = getXmlString(serverInfo, "Height");
		if (heightStr == null) {
			return false;
		}
		
		// Only allow 4K on GFE 3.x
		String gfeVersionStr = getXmlString(serverInfo, "GfeVersion");
		if (gfeVersionStr == null || gfeVersionStr.startsWith("2.")) {
			return false;
		}
		
		try {
			if (Integer.parseInt(heightStr) >= 2160) {
				// Found a 4K resolution in the list
				return true;
			}
		} catch (NumberFormatException ignored) {}
		
		return false;
	}
	
	public boolean supports4K60(String serverInfo) throws XmlPullParserException, IOException {
		// If we don't support 4K at all, bail early
		if (!supports4K(serverInfo)) {
			return false;
		}
		
		// serverinfo returns supported resolutions in descending order, so getting the first
		// refresh rate will give us whether we support 4K60. If this is 30, we don't support
		// 4K 60 FPS.
		String fpsStr = getXmlString(serverInfo, "RefreshRate");
		if (fpsStr == null) {
			return false;
		}
		
		try {
			if (Integer.parseInt(fpsStr) >= 60) {
				// 4K supported and 60 FPS is the first entry
				return true;
			}
		} catch (NumberFormatException ignored) {}
		
		return false;
	}

	public int getCurrentGame(String serverInfo) throws IOException, XmlPullParserException {
		// GFE 2.8 started keeping currentgame set to the last game played. As a result, it no longer
		// has the semantics that its name would indicate. To contain the effects of this change as much
		// as possible, we'll force the current game to zero if the server isn't in a streaming session.
		String serverState = getXmlString(serverInfo, "state");
		if (serverState != null && serverState.endsWith("_SERVER_BUSY")) {
			String game = getXmlString(serverInfo, "currentgame");
			return Integer.parseInt(game);
		}
		else {
			return 0;
		}
	}

	public NvApp getAppById(int appId) throws IOException, XmlPullParserException {
		LinkedList<NvApp> appList = getAppList();
		for (NvApp appFromList : appList) {
			if (appFromList.getAppId() == appId) {
				return appFromList;
			}
		}
		return null;
	}
	
	/* NOTE: Only use this function if you know what you're doing.
	 * It's totally valid to have two apps named the same thing,
	 * or even nothing at all! Look apps up by ID if at all possible
	 * using the above function */
	public NvApp getAppByName(String appName) throws IOException, XmlPullParserException {
		LinkedList<NvApp> appList = getAppList();
		for (NvApp appFromList : appList) {
			if (appFromList.getAppName().equalsIgnoreCase(appName)) {
				return appFromList;
			}
		}
		return null;
	}
	
	public PairingManager.PairState pair(String serverInfo, String pin) throws Exception {
		return pm.pair(serverInfo, pin);
	}
	
	public static LinkedList<NvApp> getAppListByReader(Reader r) throws XmlPullParserException, IOException {
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser xpp = factory.newPullParser();

		xpp.setInput(r);
		int eventType = xpp.getEventType();
		LinkedList<NvApp> appList = new LinkedList<NvApp>();
		Stack<String> currentTag = new Stack<String>();
		boolean rootTerminated = false;

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
				if (xpp.getName().equals("root")) {
					rootTerminated = true;
				}
				break;
			case (XmlPullParser.TEXT):
				NvApp app = appList.getLast();
				if (currentTag.peek().equals("AppTitle")) {
					app.setAppName(xpp.getText().trim());
				} else if (currentTag.peek().equals("ID")) {
					app.setAppId(xpp.getText().trim());
				} else if (currentTag.peek().equals("IsHdrSupported")) {
					app.setHdrSupported(xpp.getText().trim().equals("1"));
				}
				break;
			}
			eventType = xpp.next();
		}
		
		// Throw a malformed XML exception if we've not seen the root tag ended
		if (!rootTerminated) {
			throw new XmlPullParserException("Malformed XML: Root tag was not terminated");
		}
		
		// Ensure that all apps in the list are initialized
		ListIterator<NvApp> i = appList.listIterator();
		while (i.hasNext()) {
			NvApp app = i.next();
			
			// Remove uninitialized apps
			if (!app.isInitialized()) {
				LimeLog.warning("GFE returned incomplete app: "+app.getAppId()+" "+app.getAppName());
				i.remove();
			}
		}
		
		return appList;
	}
	
	public String getAppListRaw() throws MalformedURLException, IOException {
		return openHttpConnectionToString(baseUrlHttps + "/applist?"+buildUniqueIdUuidString(), true);
	}
	
	public LinkedList<NvApp> getAppList() throws GfeHttpResponseException, IOException, XmlPullParserException {
		if (verbose) {
			// Use the raw function so the app list is printed
			return getAppListByReader(new StringReader(getAppListRaw()));
		}
		else {
			ResponseBody resp = openHttpConnection(baseUrlHttps + "/applist?" + buildUniqueIdUuidString(), true);
			LinkedList<NvApp> appList = getAppListByReader(new InputStreamReader(resp.byteStream()));
			resp.close();
			return appList;
		}
	}
	
	public void unpair() throws IOException {
		openHttpConnectionToString(baseUrlHttps + "/unpair?"+buildUniqueIdUuidString(), true);
	}
	
	public InputStream getBoxArt(NvApp app) throws IOException {
		ResponseBody resp = openHttpConnection(baseUrlHttps + "/appasset?"+ buildUniqueIdUuidString() +
				"&appid=" + app.getAppId() + "&AssetType=2&AssetIdx=0", true);
		return resp.byteStream();
	}
	
	public int getServerMajorVersion(String serverInfo) throws XmlPullParserException, IOException {
		int[] appVersionQuad = getServerAppVersionQuad(serverInfo);
		if (appVersionQuad != null) {
			return appVersionQuad[0];
		}
		else {
			return 0;
		}
	}
	
	public int[] getServerAppVersionQuad(String serverInfo) throws XmlPullParserException, IOException {
		try {
			String serverVersion = getServerVersion(serverInfo);
			if (serverVersion == null) {
				LimeLog.warning("Missing server version field");
				return null;
			}
			String[] serverVersionSplit = serverVersion.split("\\.");
			if (serverVersionSplit.length != 4) {
				LimeLog.warning("Malformed server version field");
				return null;
			}
			int[] ret = new int[serverVersionSplit.length];
			for (int i = 0; i < ret.length; i++) {
				ret[i] = Integer.parseInt(serverVersionSplit[i]);
			}
			return ret;
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
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
	
	public boolean launchApp(ConnectionContext context, int appId, boolean enableHdr) throws IOException, XmlPullParserException {
		String xmlStr = openHttpConnectionToString(baseUrlHttps +
			"/launch?" + buildUniqueIdUuidString() +
			"&appid=" + appId +
			"&mode=" + context.negotiatedWidth + "x" + context.negotiatedHeight + "x" + context.negotiatedFps +
			"&additionalStates=1&sops=" + (context.streamConfig.getSops() ? 1 : 0) +
			"&rikey="+bytesToHex(context.riKey.getEncoded()) +
			"&rikeyid="+context.riKeyId +
			(!enableHdr ? "" : "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0") +
			"&localAudioPlayMode=" + (context.streamConfig.getPlayLocalAudio() ? 1 : 0) +
			"&surroundAudioInfo=" + ((context.streamConfig.getAudioChannelMask() << 16) + context.streamConfig.getAudioChannelCount()) +
			(context.streamConfig.getAttachedGamepadMask() != 0 ? "&remoteControllersBitmap=" + context.streamConfig.getAttachedGamepadMask() : "") +
			(context.streamConfig.getAttachedGamepadMask() != 0 ? "&gcmap=" + context.streamConfig.getAttachedGamepadMask() : ""),
			false);
		String gameSession = getXmlString(xmlStr, "gamesession");
		return gameSession != null && !gameSession.equals("0");
	}
	
	public boolean resumeApp(ConnectionContext context) throws IOException, XmlPullParserException {
		String xmlStr = openHttpConnectionToString(baseUrlHttps + "/resume?" + buildUniqueIdUuidString() +
				"&rikey="+bytesToHex(context.riKey.getEncoded()) +
				"&rikeyid="+context.riKeyId +
				"&surroundAudioInfo=" + ((context.streamConfig.getAudioChannelMask() << 16) + context.streamConfig.getAudioChannelCount()),
				false);
		String resume = getXmlString(xmlStr, "resume");
		return Integer.parseInt(resume) != 0;
	}
	
	public boolean quitApp() throws IOException, XmlPullParserException {
		String xmlStr = openHttpConnectionToString(baseUrlHttps + "/cancel?" + buildUniqueIdUuidString(), false);
		String cancel = getXmlString(xmlStr, "cancel");
		if (Integer.parseInt(cancel) == 0) {
			return false;
		}

		// Newer GFE versions will just return success even if quitting fails
		// if we're not the original requestor.
		if (getCurrentGame(getServerInfo()) != 0) {
			// Generate a synthetic GfeResponseException letting the caller know
			// that they can't kill someone else's stream.
			throw new GfeHttpResponseException(599, "");
		}

		return true;
	}
}
