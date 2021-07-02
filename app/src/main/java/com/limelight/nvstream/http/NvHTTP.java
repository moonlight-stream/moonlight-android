package com.limelight.nvstream.http;

import android.os.Build;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.http.PairingManager.PairState;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class NvHTTP {
    private String uniqueId;
    private PairingManager pm;

    public static final int HTTPS_PORT = 47984;
    public static final int HTTP_PORT = 47989;
    public static final int CONNECTION_TIMEOUT = 3000;
    public static final int READ_TIMEOUT = 5000;

    // Print URL and content to logcat on debug builds
    private static boolean verbose = BuildConfig.DEBUG;

    public String baseUrlHttps;
    public String baseUrlHttp;
    
    private OkHttpClient httpClient;
    private OkHttpClient httpClientWithReadTimeout;

    private X509TrustManager defaultTrustManager;
    private X509TrustManager trustManager;
    private X509KeyManager keyManager;
    private X509Certificate serverCert;

    void setServerCert(X509Certificate serverCert) {
        this.serverCert = serverCert;
    }

    private static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }

        throw new IllegalStateException("No X509 trust manager found");
    }

    private void initializeHttpState(final LimelightCryptoProvider cryptoProvider) {
        keyManager = new X509KeyManager() {
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
        };

        defaultTrustManager = getDefaultTrustManager();
        trustManager = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                throw new IllegalStateException("Should never be called");
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                try {
                    // Try the default trust manager first to allow pairing with certificates
                    // that chain up to a trusted root CA. This will raise CertificateException
                    // if the certificate is not trusted (expected for GFE's self-signed certs).
                    defaultTrustManager.checkServerTrusted(certs, authType);
                } catch (CertificateException e) {
                    // Check the server certificate if we've paired to this host
                    if (certs.length == 1 && NvHTTP.this.serverCert != null) {
                        if (!certs[0].equals(NvHTTP.this.serverCert)) {
                            throw new CertificateException("Certificate mismatch");
                        }
                    }
                    else {
                        // The cert chain doesn't look like a self-signed cert or we don't have
                        // a certificate pinned, so re-throw the original validation error.
                        throw e;
                    }
                }
            }
        };

        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                try {
                    Certificate[] certificates = session.getPeerCertificates();
                    if (certificates.length == 1 && certificates[0].equals(NvHTTP.this.serverCert)) {
                        // Allow any hostname if it's our pinned cert
                        return true;
                    }
                } catch (SSLPeerUnverifiedException e) {
                    e.printStackTrace();
                }

                // Fall back to default HostnameVerifier for validating CA-issued certs
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
            }
        };

        httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                .hostnameVerifier(hv)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
        
        httpClientWithReadTimeout = httpClient.newBuilder()
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }
    
    public NvHTTP(String address, String uniqueId, X509Certificate serverCert, LimelightCryptoProvider cryptoProvider) throws IOException {
        // Use the same UID for all Moonlight clients so we can quit games
        // started by other Moonlight clients.
        this.uniqueId = "0123456789ABCDEF";

        this.serverCert = serverCert;

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
    
    private static void verifyResponseStatus(XmlPullParser xpp) throws GfeHttpResponseException {
        // We use Long.parseLong() because in rare cases GFE can send back a status code of
        // 0xFFFFFFFF, which will cause Integer.parseInt() to throw a NumberFormatException due
        // to exceeding Integer.MAX_VALUE. We'll get the desired error code of -1 by just casting
        // the resulting long into an int.
        int statusCode = (int)Long.parseLong(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_code"));
        if (statusCode != 200) {
            String statusMsg = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_message");
            if (statusCode == -1 && "Invalid".equals(statusMsg)) {
                // Special case handling an audio capture error which GFE doesn't
                // provide any useful status message for.
                statusCode = 418;
                statusMsg = "Missing audio capture device. Reinstall GeForce Experience.";
            }
            throw new GfeHttpResponseException(statusCode, statusMsg);
        }
    }
    
    public String getServerInfo() throws IOException, XmlPullParserException {
        String resp;
        
        //
        // TODO: Shield Hub uses HTTP for this and is able to get an accurate PairStatus with HTTP.
        // For some reason, we always see PairStatus is 0 over HTTP and only 1 over HTTPS. It looks
        // like there are extra request headers required to make this stuff work over HTTP.
        //

        // When we have a pinned cert, use HTTPS to fetch serverinfo and fall back on cert mismatch
        if (serverCert != null) {
            try {
                try {
                    resp = openHttpConnectionToString(baseUrlHttps + "/serverinfo?"+buildUniqueIdUuidString(), true);
                } catch (SSLHandshakeException e) {
                    // Detect if we failed due to a server cert mismatch
                    if (e.getCause() instanceof CertificateException) {
                        // Jump to the GfeHttpResponseException exception handler to retry
                        // over HTTP which will allow us to pair again to update the cert
                        throw new GfeHttpResponseException(401, "Server certificate mismatch");
                    }
                    else {
                        throw e;
                    }
                }

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
        else {
            // No pinned cert, so use HTTP
            return openHttpConnectionToString(baseUrlHttp + "/serverinfo", true);
        }
    }
    
    public ComputerDetails getComputerDetails() throws IOException, XmlPullParserException {
        ComputerDetails details = new ComputerDetails();
        String serverInfo = getServerInfo();
        
        details.name = getXmlString(serverInfo, "hostname");
        if (details.name == null || details.name.isEmpty()) {
            details.name = "UNKNOWN";
        }

        details.uuid = getXmlString(serverInfo, "uniqueid");
        details.macAddress = getXmlString(serverInfo, "mac");
        details.localAddress = getXmlString(serverInfo, "LocalIP");

        // This may be null, but that's okay
        details.remoteAddress = getXmlString(serverInfo, "ExternalIP");

        // This has some extra logic to always report unpaired if the pinned cert isn't there
        details.pairState = getPairState(serverInfo);
        
        details.runningGameId = getCurrentGame(serverInfo);
        
        // We could reach it so it's online
        details.state = ComputerDetails.State.ONLINE;
        
        return details;
    }

    // This hack is Android-specific but we do it on all platforms
    // because it doesn't really matter
    private OkHttpClient performAndroidTlsHack(OkHttpClient client) {
        // Doing this each time we create a socket is required
        // to avoid the SSLv3 fallback that causes connection failures
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(new KeyManager[] { keyManager }, new TrustManager[] { trustManager }, new SecureRandom());

            // TLS 1.2 is not enabled by default prior to Android 5.0, so we'll need a custom
            // SSLSocketFactory in order to connect to GFE 3.20.4 which requires TLSv1.2 or later.
            // We don't just always use TLSv12SocketFactory because explicitly specifying TLS versions
            // prevents later TLS versions from being negotiated even if client and server otherwise
            // support them.
            return client.newBuilder().sslSocketFactory(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                            sc.getSocketFactory() : new TLSv12SocketFactory(sc),
                    trustManager).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    // Read timeout should be enabled for any HTTP query that requires no outside action
    // on the GFE server. Examples of queries that DO require outside action are launch, resume, and quit.
    // The initial pair query does require outside action (user entering a PIN) but subsequent pairing
    // queries do not.
    private ResponseBody openHttpConnection(String url, boolean enableReadTimeout) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        Response response;

        if (enableReadTimeout) {
            response = performAndroidTlsHack(httpClientWithReadTimeout).newCall(request).execute();
        }
        else {
            response = performAndroidTlsHack(httpClient).newCall(request).execute();
        }

        ResponseBody body = response.body();
        
        if (response.isSuccessful()) {
            return body;
        }
        
        // Unsuccessful, so close the response body
        if (body != null) {
            body.close();
        }
        
        if (response.code() == 404) {
            throw new FileNotFoundException(url);
        }
        else {
            throw new GfeHttpResponseException(response.code(), response.message());
        }
    }
    
    String openHttpConnectionToString(String url, boolean enableReadTimeout) throws IOException {
        try {
            if (verbose) {
                LimeLog.info("Requesting URL: "+url);
            }

            ResponseBody resp = openHttpConnection(url, enableReadTimeout);
            String respString = resp.string();
            resp.close();

            if (verbose) {
                LimeLog.info(url+" -> "+respString);
            }

            return respString;
        } catch (IOException e) {
            if (verbose) {
                e.printStackTrace();
            }
            
            throw e;
        }
    }

    public String getServerVersion(String serverInfo) throws XmlPullParserException, IOException {
        return getXmlString(serverInfo, "appversion");
    }

    public PairingManager.PairState getPairState() throws IOException, XmlPullParserException {
        return getPairState(getServerInfo());
    }

    public PairingManager.PairState getPairState(String serverInfo) throws IOException, XmlPullParserException {
        if (!NvHTTP.getXmlString(serverInfo, "PairStatus").equals("1")) {
            return PairState.NOT_PAIRED;
        }

        return PairState.PAIRED;
    }
    
    public long getMaxLumaPixelsH264(String serverInfo) throws XmlPullParserException, IOException {
        String str = getXmlString(serverInfo, "MaxLumaPixelsH264");
        if (str != null) {
            return Long.parseLong(str);
        } else {
            return 0;
        }
    }
    
    public long getMaxLumaPixelsHEVC(String serverInfo) throws XmlPullParserException, IOException {
        String str = getXmlString(serverInfo, "MaxLumaPixelsHEVC");
        if (str != null) {
            return Long.parseLong(str);
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
            return Long.parseLong(str);
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
        // Only allow 4K on GFE 3.x
        String gfeVersionStr = getXmlString(serverInfo, "GfeVersion");
        if (gfeVersionStr == null || gfeVersionStr.startsWith("2.")) {
            return false;
        }

        return true;
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

    public PairingManager getPairingManager() {
        return pm;
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
                    app.setAppName(xpp.getText());
                } else if (currentTag.peek().equals("ID")) {
                    app.setAppId(xpp.getText());
                } else if (currentTag.peek().equals("IsHdrSupported")) {
                    app.setHdrSupported(xpp.getText().equals("1"));
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
        openHttpConnectionToString(baseUrlHttp + "/unpair?"+buildUniqueIdUuidString(), true);
    }
    
    public InputStream getBoxArt(NvApp app) throws IOException {
        ResponseBody resp = openHttpConnection(baseUrlHttps + "/appasset?"+ buildUniqueIdUuidString() +
                "&appid=" + app.getAppId() + "&AssetType=2&AssetIdx=0", true);
        return resp.byteStream();
    }
    
    public int getServerMajorVersion(String serverInfo) throws XmlPullParserException, IOException {
        return getServerAppVersionQuad(serverInfo)[0];
    }
    
    public int[] getServerAppVersionQuad(String serverInfo) throws XmlPullParserException, IOException {
        String serverVersion = getServerVersion(serverInfo);
        if (serverVersion == null) {
            throw new IllegalArgumentException("Missing server version field");
        }
        String[] serverVersionSplit = serverVersion.split("\\.");
        if (serverVersionSplit.length != 4) {
            throw new IllegalArgumentException("Malformed server version field: "+serverVersion);
        }
        int[] ret = new int[serverVersionSplit.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = Integer.parseInt(serverVersionSplit[i]);
        }
        return ret;
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
        // Using an FPS value over 60 causes SOPS to default to 720p60,
        // so force it to 0 to ensure the correct resolution is set. We
        // used to use 60 here but that locked the frame rate to 60 FPS
        // on GFE 3.20.3.
        int fps = context.streamConfig.getLaunchRefreshRate() > 60 ? 0 : context.streamConfig.getLaunchRefreshRate();

        // Using an unsupported resolution (not 720p, 1080p, or 4K) causes
        // GFE to force SOPS to 720p60. This is fine for < 720p resolutions like
        // 360p or 480p, but it is not ideal for 1440p and other resolutions.
        // When we detect an unsupported resolution, disable SOPS unless it's under 720p.
        // FIXME: Detect support resolutions using the serverinfo response, not a hardcoded list
        boolean enableSops = context.streamConfig.getSops();
        if (context.negotiatedWidth * context.negotiatedHeight > 1280 * 720 &&
                context.negotiatedWidth * context.negotiatedHeight != 1920 * 1080 &&
                context.negotiatedWidth * context.negotiatedHeight != 3840 * 2160) {
            LimeLog.info("Disabling SOPS due to non-standard resolution: "+context.negotiatedWidth+"x"+context.negotiatedHeight);
            enableSops = false;
        }

        String xmlStr = openHttpConnectionToString(baseUrlHttps +
            "/launch?" + buildUniqueIdUuidString() +
            "&appid=" + appId +
            "&mode=" + context.negotiatedWidth + "x" + context.negotiatedHeight + "x" + fps +
            "&additionalStates=1&sops=" + (enableSops ? 1 : 0) +
            "&rikey="+bytesToHex(context.riKey.getEncoded()) +
            "&rikeyid="+context.riKeyId +
            (!enableHdr ? "" : "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0") +
            "&localAudioPlayMode=" + (context.streamConfig.getPlayLocalAudio() ? 1 : 0) +
            "&surroundAudioInfo=" + context.streamConfig.getAudioConfiguration().getSurroundAudioInfo() +
            (context.streamConfig.getAttachedGamepadMask() != 0 ? "&remoteControllersBitmap=" + context.streamConfig.getAttachedGamepadMask() : "") +
            (context.streamConfig.getAttachedGamepadMask() != 0 ? "&gcmap=" + context.streamConfig.getAttachedGamepadMask() : ""),
            false);
        String gameSession = getXmlString(xmlStr, "gamesession");
        if (gameSession != null && !gameSession.equals("0")) {
            context.rtspSessionUrl = getXmlString(xmlStr, "sessionUrl0");
            return true;
        }
        else {
            return false;
        }
    }
    
    public boolean resumeApp(ConnectionContext context) throws IOException, XmlPullParserException {
        String xmlStr = openHttpConnectionToString(baseUrlHttps + "/resume?" + buildUniqueIdUuidString() +
                "&rikey="+bytesToHex(context.riKey.getEncoded()) +
                "&rikeyid="+context.riKeyId +
                "&surroundAudioInfo=" + context.streamConfig.getAudioConfiguration().getSurroundAudioInfo(),
                false);
        String resume = getXmlString(xmlStr, "resume");
        if (Integer.parseInt(resume) != 0) {
            context.rtspSessionUrl = getXmlString(xmlStr, "sessionUrl0");
            return true;
        }
        else {
            return false;
        }
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

    // Based on example code from https://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
    private static class TLSv12SocketFactory extends SSLSocketFactory {
        private SSLSocketFactory internalSSLSocketFactory;

        public TLSv12SocketFactory(SSLContext context) {
            internalSSLSocketFactory = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return internalSSLSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return internalSSLSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket());
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enableTLSv12OnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
        }

        private Socket enableTLSv12OnSocket(Socket socket) {
            if (socket instanceof SSLSocket) {
                // TLS 1.2 is not enabled by default prior to Android 5.0. We must enable it
                // explicitly to ensure we can communicate with GFE 3.20.4 which blocks TLS 1.0.
                ((SSLSocket)socket).setEnabledProtocols(new String[] {"TLSv1.2"});
            }
            return socket;
        }
    }
}
