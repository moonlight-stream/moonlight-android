package com.limelight.nvstream.http

import android.annotation.SuppressLint
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Proxy
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.Principal
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.LinkedList
import java.util.Stack
import java.util.UUID
import java.util.concurrent.TimeUnit

import org.json.JSONObject

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.jni.MoonBridge

import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class NvHTTP(
    address: ComputerDetails.AddressTuple,
    httpsPort: Int,
    uniqueId: String,
    clientName: String,
    serverCert: X509Certificate?,
    private val cryptoProvider: LimelightCryptoProvider,
    private val timeoutConfig: TimeoutConfig = TimeoutConfig.DEFAULT
) {
    private var uniqueId: String = "0123456789ABCDEF"
    val pairingManager: PairingManager
    private var clientName: String? = null

    private val baseUrlHttp: HttpUrl

    private var httpsPort: Int

    private lateinit var httpClientLongConnectTimeout: OkHttpClient
    private lateinit var httpClientLongConnectNoReadTimeout: OkHttpClient
    private lateinit var httpClientShortConnectTimeout: OkHttpClient

    private lateinit var defaultTrustManager: X509TrustManager
    private lateinit var trustManager: X509TrustManager
    private lateinit var keyManager: X509KeyManager
    internal var serverCert: X509Certificate? = null
    private var lastServerInfoTrustedByCert = false

    data class TimeoutConfig(
        val connectTimeoutMs: Int,
        val readTimeoutMs: Int,
        val shortConnectTimeoutMs: Int
    ) {
        init {
            require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
            require(readTimeoutMs >= 0) { "readTimeoutMs must be non-negative" }
            require(shortConnectTimeoutMs > 0) { "shortConnectTimeoutMs must be positive" }
        }

        companion object {
            val DEFAULT = TimeoutConfig(
                LONG_CONNECTION_TIMEOUT,
                READ_TIMEOUT,
                SHORT_CONNECTION_TIMEOUT
            )
        }
    }

    class DisplayInfo(
        val index: Int,
        val name: String,
        val guid: String
    )

    init {

        if (clientName.isNotEmpty()) this.clientName = clientName

        this.serverCert = serverCert

        initializeHttpState(cryptoProvider)

        this.httpsPort = httpsPort

        try {
            var addressString = address.address
            if (addressString.contains(":") && addressString.contains(".")) {
                val addr = InetAddress.getByName(addressString)
                if (addr is Inet4Address) {
                    addressString = addr.hostAddress!!
                }
            }

            this.baseUrlHttp = HttpUrl.Builder()
                .scheme("http")
                .host(addressString)
                .port(address.port)
                .build()
        } catch (e: IllegalArgumentException) {
            throw IOException(e)
        }

        this.pairingManager = PairingManager(this, cryptoProvider)
    }

    private fun initializeHttpState(cryptoProvider: LimelightCryptoProvider) {
        keyManager = object : X509KeyManager {
            override fun chooseClientAlias(keyTypes: Array<String>?, issuers: Array<Principal>?, socket: java.net.Socket?): String = "Limelight-RSA"
            override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: java.net.Socket?): String? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(cryptoProvider.getClientCertificate())
            override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
            override fun getPrivateKey(alias: String?) = cryptoProvider.getClientPrivateKey()
            override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
        }

        defaultTrustManager = getDefaultTrustManager()
        trustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
                throw IllegalStateException("Should never be called")
            }
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                try {
                    defaultTrustManager.checkServerTrusted(certs, authType)
                } catch (e: CertificateException) {
                    if (certs.size == 1 && this@NvHTTP.serverCert != null) {
                        if (certs[0] != this@NvHTTP.serverCert) {
                            throw CertificateException("Certificate mismatch")
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

        val hv = HostnameVerifier { hostname, session ->
            try {
                val certificates = session.peerCertificates
                if (certificates.size == 1 && certificates[0] == this@NvHTTP.serverCert) {
                    return@HostnameVerifier true
                }
            } catch (e: SSLPeerUnverifiedException) {
                e.printStackTrace()
            }
            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }

        val sc = createSslContext()

        httpClientLongConnectTimeout = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .hostnameVerifier(hv)
            .sslSocketFactory(sc.socketFactory, trustManager)
            .readTimeout(timeoutConfig.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutConfig.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .proxy(Proxy.NO_PROXY)
            .build()

        httpClientShortConnectTimeout = httpClientLongConnectTimeout.newBuilder()
            .connectTimeout(timeoutConfig.shortConnectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()

        httpClientLongConnectNoReadTimeout = httpClientLongConnectTimeout.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Synchronized
    private fun rebuildHttpClientsAfterTlsFailure(failedClient: OkHttpClient): OkHttpClient {
        val wasShortConnectClient = failedClient === httpClientShortConnectTimeout
        val wasNoReadTimeoutClient = failedClient === httpClientLongConnectNoReadTimeout

        httpClientLongConnectTimeout.dispatcher.cancelAll()
        httpClientLongConnectTimeout.connectionPool.evictAll()
        httpClientShortConnectTimeout.dispatcher.cancelAll()
        httpClientShortConnectTimeout.connectionPool.evictAll()
        httpClientLongConnectNoReadTimeout.dispatcher.cancelAll()
        httpClientLongConnectNoReadTimeout.connectionPool.evictAll()

        initializeHttpState(cryptoProvider)

        return when {
            wasShortConnectClient -> httpClientShortConnectTimeout
            wasNoReadTimeoutClient -> httpClientLongConnectNoReadTimeout
            else -> httpClientLongConnectTimeout
        }
    }

    private fun shouldRetryTlsHandshake(e: SSLHandshakeException): Boolean {
        return e.cause !is CertificateException
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getHttpsUrl(likelyOnline: Boolean): HttpUrl {
        if (httpsPort == 0) {
            httpsPort = getHttpsPort(
                openHttpConnectionToString(
                    if (likelyOnline) httpClientLongConnectTimeout else httpClientShortConnectTimeout,
                    baseUrlHttp, "serverinfo"
                )
            )
        }
        return HttpUrl.Builder().scheme("https").host(baseUrlHttp.host).port(httpsPort).build()
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getServerInfo(likelyOnline: Boolean): String {
        val client = if (likelyOnline) httpClientLongConnectTimeout else httpClientShortConnectTimeout
        lastServerInfoTrustedByCert = false

        if (serverCert != null) {
            try {
                val resp: String
                try {
                    resp = openHttpConnectionToString(client, getHttpsUrl(likelyOnline), "serverinfo")
                } catch (e: SSLHandshakeException) {
                    if (e.cause is CertificateException) {
                        throw HostHttpResponseException(401, "Server certificate mismatch")
                    } else {
                        throw e
                    }
                }
                getServerVersion(resp)
                lastServerInfoTrustedByCert = true
                return resp
            } catch (e: HostHttpResponseException) {
                if (e.getErrorCode() == 401) {
                    return openHttpConnectionToString(client, baseUrlHttp, "serverinfo")
                }
                throw e
            }
        } else {
            return openHttpConnectionToString(client, baseUrlHttp, "serverinfo")
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getComputerDetails(serverInfo: String): ComputerDetails {
        val details = ComputerDetails()

        details.name = getXmlString(serverInfo, "hostname", false)
        if (details.name.isNullOrEmpty()) {
            details.name = "UNKNOWN"
        }

        details.uuid = getXmlString(serverInfo, "uniqueid", true)

        details.httpsPort = getHttpsPort(serverInfo)

        details.macAddress = getXmlString(serverInfo, "mac", false)

        details.localAddress = makeTuple(getXmlString(serverInfo, "LocalIP", false), baseUrlHttp.port)

        details.externalPort = getExternalPort(serverInfo)
        details.remoteAddress = makeTuple(getXmlString(serverInfo, "ExternalIP", false), details.externalPort)

        details.pairState = getPairState(serverInfo)
        details.serverInfoTrustedByCert = lastServerInfoTrustedByCert
        details.runningGameId = getCurrentGame(serverInfo)

        details.nvidiaServer = getXmlString(serverInfo, "state", true)!!.contains("MJOLNIR")
        details.supportsDesktopSpecialApp = getXmlString(serverInfo, "DesktopSpecialAppSupport", false) == "1"

        try {
            details.sunshineVersion = getSunshineVersion(serverInfo)
        } catch (e: Exception) {
            details.sunshineVersion = null
        }

        details.state = ComputerDetails.State.ONLINE

        return details
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getComputerDetails(likelyOnline: Boolean): ComputerDetails {
        return getComputerDetails(getServerInfo(likelyOnline))
    }

    private fun createSslContext(): SSLContext {
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(arrayOf<KeyManager>(keyManager), arrayOf<TrustManager>(trustManager), SecureRandom())
            return sc
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: KeyManagementException) {
            throw RuntimeException(e)
        }
    }

    private fun getCompleteUrl(baseUrl: HttpUrl, path: String, query: String?, displayName: String? = null): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegment(path)
            .query(query)
            .apply {
                displayName?.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("display_name", it)
                }
            }
            .addQueryParameter("uniqueid", uniqueId)
            .addQueryParameter("clientname", clientName)
            .addQueryParameter("uuid", UUID.randomUUID().toString())
            .build()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openHttpConnection(client: OkHttpClient, baseUrl: HttpUrl, path: String): ResponseBody {
        return openHttpConnection(client, baseUrl, path, null)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openHttpConnection(client: OkHttpClient, baseUrl: HttpUrl, path: String, query: String?, displayName: String? = null): ResponseBody {
        val completeUrl = getCompleteUrl(baseUrl, path, query, displayName)
        val request = Request.Builder().url(completeUrl).get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: SSLHandshakeException) {
            if (!shouldRetryTlsHandshake(e)) {
                throw e
            }

            LimeLog.warning("$completeUrl -> TLS handshake failed; rebuilding HTTP client and retrying once")
            rebuildHttpClientsAfterTlsFailure(client).newCall(request).execute()
        }

        val body = response.body

        if (response.isSuccessful) {
            return body
        }

        body.close()

        if (response.code == 404) {
            throw FileNotFoundException(completeUrl.toString())
        } else {
            throw HostHttpResponseException(response.code, response.message)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openHttpConnectionToString(client: OkHttpClient, baseUrl: HttpUrl, path: String): String {
        return openHttpConnectionToString(client, baseUrl, path, null)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openHttpConnectionToString(client: OkHttpClient, baseUrl: HttpUrl, path: String, query: String?, displayName: String? = null): String {
        try {
            val resp = openHttpConnection(client, baseUrl, path, query, displayName)
            val respString = resp.string()
            resp.close()

            if (verbose && path != "serverinfo") {
                LimeLog.info("${getCompleteUrl(baseUrl, path, query, displayName)} -> $respString")
            }

            return respString
        } catch (e: IOException) {
            if (verbose && path != "serverinfo") {
                LimeLog.warning("${getCompleteUrl(baseUrl, path, query, displayName)} -> ${e.message}")
                e.printStackTrace()
            }
            throw e
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openHttpConnectionPost(client: OkHttpClient, baseUrl: HttpUrl, path: String, jsonData: String): String {
        val completeUrl = getCompleteUrl(baseUrl, path, null)

        val body = jsonData.toRequestBody(
            "application/json; charset=utf-8".toMediaTypeOrNull()
        )

        val request = Request.Builder()
            .url(completeUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body

        if (response.isSuccessful) {
            val respString = responseBody.string()
            responseBody.close()

            if (verbose) {
                LimeLog.info("$completeUrl -> $respString")
            }

            return respString
        }

        responseBody.close()

        if (response.code == 404) {
            throw FileNotFoundException(completeUrl.toString())
        } else {
            throw HostHttpResponseException(response.code, response.message)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerVersion(serverInfo: String): String {
        return getXmlString(serverInfo, "appversion", true)!!
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getPairState(): PairState {
        return getPairState(getServerInfo(true))
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getPairState(serverInfo: String): PairState {
        return if (getXmlString(serverInfo, "PairStatus", true) == "1") PairState.PAIRED else PairState.NOT_PAIRED
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getMaxLumaPixelsH264(serverInfo: String): Long {
        val str = getXmlString(serverInfo, "MaxLumaPixelsH264", false)
        return str?.toLong() ?: 0
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getMaxLumaPixelsHEVC(serverInfo: String): Long {
        val str = getXmlString(serverInfo, "MaxLumaPixelsHEVC", false)
        return str?.toLong() ?: 0
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerCodecModeSupport(serverInfo: String): Long {
        val str = getXmlString(serverInfo, "ServerCodecModeSupport", false)
        return str?.toLong() ?: 0
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getGpuType(serverInfo: String): String? {
        return getXmlString(serverInfo, "gputype", false)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getGfeVersion(serverInfo: String): String? {
        return getXmlString(serverInfo, "GfeVersion", false)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getSunshineVersion(serverInfo: String): String? {
        return getXmlString(serverInfo, "SunshineVersion", false)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun supports4K(serverInfo: String): Boolean {
        val gfeVersionStr = getXmlString(serverInfo, "GfeVersion", false)
        return !(gfeVersionStr == null || gfeVersionStr.startsWith("2."))
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getCurrentGame(serverInfo: String): Int {
        return if (getXmlString(serverInfo, "state", true)!!.endsWith("_SERVER_BUSY")) {
            getXmlString(serverInfo, "currentgame", true)!!.toInt()
        } else {
            0
        }
    }

    fun getHttpsPort(serverInfo: String): Int {
        return try {
            getXmlString(serverInfo, "HttpsPort", true)!!.toInt()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            DEFAULT_HTTPS_PORT
        } catch (e: IOException) {
            e.printStackTrace()
            DEFAULT_HTTPS_PORT
        }
    }

    fun getExternalPort(serverInfo: String): Int {
        return try {
            getXmlString(serverInfo, "ExternalPort", true)!!.toInt()
        } catch (e: XmlPullParserException) {
            baseUrlHttp.port
        } catch (e: IOException) {
            e.printStackTrace()
            baseUrlHttp.port
        }
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getAppById(appId: Int): NvApp? {
        val appList = getAppList()
        for (app in appList) {
            if (app.appId == appId) {
                return app
            }
        }
        return null
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getAppByName(appName: String): NvApp? {
        val appList = getAppList()
        for (app in appList) {
            if (app.appName.equals(appName, ignoreCase = true)) {
                return app
            }
        }
        return null
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getAppListRaw(): String {
        return openHttpConnectionToString(httpClientLongConnectTimeout, getHttpsUrl(true), "applist")
    }

    @Throws(HostHttpResponseException::class, IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun getAppList(): LinkedList<NvApp> {
        return if (verbose) {
            getAppListByReader(StringReader(getAppListRaw()))
        } else {
            openHttpConnection(httpClientLongConnectTimeout, getHttpsUrl(true), "applist").use { resp ->
                getAppListByReader(InputStreamReader(resp.byteStream()))
            }
        }
    }

    @Throws(HostHttpResponseException::class, IOException::class, InterruptedException::class)
    internal fun executePairingCommand(additionalArguments: String, enableReadTimeout: Boolean): String {
        return openHttpConnectionToString(
            if (enableReadTimeout) httpClientLongConnectTimeout else httpClientLongConnectNoReadTimeout,
            baseUrlHttp, "pair", "devicename=roth&updateState=1&$additionalArguments"
        )
    }

    @Throws(HostHttpResponseException::class, IOException::class, InterruptedException::class)
    internal fun executePairingChallenge(): String {
        return openHttpConnectionToString(
            httpClientLongConnectTimeout, getHttpsUrl(true),
            "pair", "devicename=roth&updateState=1&phrase=pairchallenge"
        )
    }

    @Throws(IOException::class, InterruptedException::class)
    fun unpair() {
        openHttpConnectionToString(httpClientLongConnectTimeout, baseUrlHttp, "unpair")
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getBoxArt(app: NvApp): InputStream {
        val resp = openHttpConnection(httpClientLongConnectTimeout, getHttpsUrl(true), "appasset", "appid=${app.appId}&AssetType=2&AssetIdx=0")
        return resp.byteStream()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getDisplays(): List<DisplayInfo> {
        try {
            val jsonStr = openHttpConnectionToString(httpClientLongConnectTimeout, getHttpsUrl(true), "displays")
            val json = JSONObject(jsonStr)

            val statusCode = json.optInt("status_code", 0)
            if (statusCode != 200) {
                throw IOException("Failed to get displays: " + json.optString("status_message", "Unknown error"))
            }

            val displaysArray = json.optJSONArray("displays") ?: return ArrayList()

            val displays = ArrayList<DisplayInfo>(displaysArray.length())
            for (i in 0 until displaysArray.length()) {
                val displayObj = displaysArray.getJSONObject(i)

                var friendlyName = displayObj.optString("friendly_name", "")
                if (friendlyName.isEmpty()) {
                    friendlyName = displayObj.optString("display_name", "Display ${i + 1}")
                }

                val guid = displayObj.optString("device_id", "")

                displays.add(DisplayInfo(i, friendlyName, guid))
            }

            return displays
        } catch (e: org.json.JSONException) {
            throw IOException("Failed to parse displays response: ${e.message}", e)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun rotateDisplay(angle: Int, displayName: String?): Boolean {
        try {
            val jsonStr = openHttpConnectionToString(
                httpClientLongConnectTimeout,
                getHttpsUrl(true),
                "rotate-display",
                "angle=$angle",
                displayName
            )
            val json = JSONObject(jsonStr)

            val statusCode = json.optInt("status_code", 0)
            val success = json.optBoolean("success", false)

            if (statusCode != 200 || !success) {
                val errorMsg = json.optString("status_message", "Unknown error")
                LimeLog.warning("Failed to rotate display: $errorMsg")
                return false
            }

            LimeLog.info("Display rotation changed successfully to $angle degrees")
            return true
        } catch (e: org.json.JSONException) {
            throw IOException("Failed to parse rotate display response: ${e.message}", e)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerMajorVersion(serverInfo: String): Int {
        return getServerAppVersionQuad(serverInfo)[0]
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerAppVersionQuad(serverInfo: String): IntArray {
        val serverVersion = getServerVersion(serverInfo)
        val serverVersionSplit = serverVersion.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (serverVersionSplit.size != 4) {
            throw IllegalArgumentException("Malformed server version field: $serverVersion")
        }
        return IntArray(serverVersionSplit.size) { serverVersionSplit[it].toInt() }
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun launchApp(context: ConnectionContext, verb: String, appId: Int, enableHdr: Boolean): Boolean {
        if (appId == NvApp.DESKTOP_APP_ID && !context.supportsDesktopSpecialApp) {
            LimeLog.warning("Refusing Desktop special app launch without DesktopSpecialAppSupport")
            return false
        }

        val streamConfig = context.streamConfig
        val fps = if (context.isNvidiaServerSoftware && streamConfig.launchRefreshRate > 60)
            0 else streamConfig.launchRefreshRate

        var enableSops = streamConfig.sops
        if (context.isNvidiaServerSoftware) {
            if (context.negotiatedWidth * context.negotiatedHeight > 1280 * 720 &&
                context.negotiatedWidth * context.negotiatedHeight != 1920 * 1080 &&
                context.negotiatedWidth * context.negotiatedHeight != 3840 * 2160
            ) {
                LimeLog.info("Disabling SOPS due to non-standard resolution: ${context.negotiatedWidth}x${context.negotiatedHeight}")
                enableSops = false
            }
        }

        var queryParams = "appid=$appId" +
            "&mode=${streamConfig.reqWidth}x${streamConfig.reqHeight}x$fps" +
            "&additionalStates=1&sops=${if (enableSops) 1 else 0}" +
            "&resolutionScale=${streamConfig.resolutionScale}" +
            "&rikey=${bytesToHex(context.riKey.encoded)}" +
            "&rikeyid=${context.riKeyId}" +
            (if (!enableHdr) "" else "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0") +
            "&localAudioPlayMode=${if (streamConfig.getPlayLocalAudio()) 1 else 0}" +
            "&surroundAudioInfo=${streamConfig.audioConfiguration.surroundAudioInfo}" +
            "&remoteControllersBitmap=${streamConfig.attachedGamepadMask}" +
            "&gcmap=${streamConfig.attachedGamepadMask}" +
            "&gcpersist=${if (streamConfig.getPersistGamepadsAfterDisconnect()) 1 else 0}" +
            "&minBrightness=${context.minBrightness}" +
            "&maxBrightness=${context.maxBrightness}" +
            "&maxAverageBrightness=${context.maxAverageBrightness}"

        streamConfig.getUseVdd()?.let { useVdd ->
            queryParams += "&useVdd=${if (useVdd) 1 else 0}"
        }

        val customScreenMode = streamConfig.customScreenMode
        if (customScreenMode != -1) {
            queryParams += "&customScreenMode=$customScreenMode"
        }

        queryParams += MoonBridge.getLaunchUrlQueryParameters()

        val xmlStr = openHttpConnectionToString(
            httpClientLongConnectNoReadTimeout,
            getHttpsUrl(true),
            verb,
            queryParams,
            context.displayName
        )
        return if ((verb == "launch" && getXmlString(xmlStr, "gamesession", true) != "0") ||
            (verb == "resume" && getXmlString(xmlStr, "resume", true) != "0")
        ) {
            context.rtspSessionUrl = getXmlString(xmlStr, "sessionUrl0", false)
            true
        } else {
            false
        }
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun quitApp(): Boolean {
        val xmlStr = openHttpConnectionToString(httpClientLongConnectNoReadTimeout, getHttpsUrl(true), "cancel")
        if (getXmlString(xmlStr, "cancel", true) == "0") {
            return false
        }

        if (getCurrentGame(getServerInfo(true)) != 0) {
            throw HostHttpResponseException(599, "")
        }

        return true
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun pcSleep(): Boolean {
        val xmlStr = openHttpConnectionToString(httpClientLongConnectNoReadTimeout, getHttpsUrl(true), "pcsleep")
        return getXmlString(xmlStr, "pcsleep", true) != "0"
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun sendSuperCmd(cmdId: String): Boolean {
        val xmlStr = openHttpConnectionToString(httpClientLongConnectNoReadTimeout, getHttpsUrl(true), "supercmd", "cmdId=$cmdId")
        return getXmlString(xmlStr, "supercmd", true) != "0"
    }

    @SuppressLint("DefaultLocale")
    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    fun setBitrate(bitrateKbps: Int): Boolean {
        val query = String.format("bitrate=%d", bitrateKbps)
        val xmlStr = openHttpConnectionToString(
            httpClientLongConnectNoReadTimeout,
            getHttpsUrl(true), "bitrate", query
        )
        return getXmlString(xmlStr, "bitrate", true) != "0"
    }

    // ---------------------------------------------------------------------------
    // 智能码率 (ABR) — Sunshine 扩展 API
    // ---------------------------------------------------------------------------

    /** ABR HTTPS GET 助手；返回响应正文或 null。*/
    private fun abrGet(pathSegments: String): String? = try {
        val url = getHttpsUrl(true).newBuilder().addPathSegments(pathSegments).build()
        httpClientLongConnectTimeout.newCall(Request.Builder().url(url).get().build())
            .execute()
            .use { if (it.isSuccessful) it.body.string() else null }
    } catch (e: Exception) { null }

    /** ABR HTTPS POST 助手；返回响应正文或 null（失败/非 2xx 也返回 null）。*/
    private fun abrPost(pathSegments: String, payload: JSONObject): String? = try {
        val url = getHttpsUrl(true).newBuilder().addPathSegments(pathSegments).build()
        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        httpClientLongConnectTimeout.newCall(Request.Builder().url(url).post(body).build())
            .execute()
            .use { if (it.isSuccessful) it.body.string() else null }
    } catch (e: Exception) { null }

    /** 查询服务端 ABR 能力。失败返回 supported=false 的占位对象。*/
    fun getAbrCapabilities(): AbrCapabilities {
        val body = abrGet("api/abr/capabilities").takeUnless { it.isNullOrEmpty() }
            ?: return AbrCapabilities(false, 0, emptyList())
        return try {
            val json = JSONObject(body)
            val features = mutableListOf<String>()
            json.optJSONArray("features")?.let { arr ->
                for (i in 0 until arr.length()) features.add(arr.optString(i))
            }
            AbrCapabilities(
                json.optBoolean("supported", false),
                json.optInt("version", 0),
                features
            )
        } catch (e: Exception) {
            AbrCapabilities(false, 0, emptyList())
        }
    }

    /** 通知服务端启用/关闭 ABR。*/
    fun setAbrMode(config: AbrConfig): Boolean {
        val payload = JSONObject().apply {
            put("enabled", config.enabled)
            put("minBitrate", config.minBitrate)
            put("maxBitrate", config.maxBitrate)
            put("mode", config.mode)
        }
        return abrPost("api/abr", payload) != null
    }

    /** 上报客户端网络指标，可能返回服务端建议的新码率。*/
    fun reportNetworkFeedback(feedback: NetworkFeedback): AbrAction? {
        val payload = JSONObject().apply {
            put("packetLoss", feedback.packetLoss)
            put("rttMs", feedback.rttMs)
            put("decodeFps", feedback.decodeFps)
            put("droppedFrames", feedback.droppedFrames)
            put("currentBitrate", feedback.currentBitrate)
        }
        val body = abrPost("api/abr/feedback", payload).takeUnless { it.isNullOrEmpty() }
            ?: return null
        return try {
            val json = JSONObject(body)
            AbrAction(
                if (json.has("newBitrate")) json.optInt("newBitrate") else null,
                if (json.has("reason")) json.optString("reason") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // 剪贴板 blob 通道 (Sunshine /api/v1/clipboard/blob)
    //
    // 当 0x5508 控制包负载（>~64 KiB）超过 ENet 帧上限时，先把数据上传到
    // 该 HTTPS 端点拿到 id，然后通过 LI_CLIPBOARD_KIND_REF 帧把 id 发送给
    // 主机；主机再 GET 同一端点取回原始字节。所有调用走 mTLS（客户端证书）。
    // ---------------------------------------------------------------------------

    /**
     * Upload an oversized clipboard payload to the host. Blocks on the calling
     * thread (do NOT call from the UI thread). Returns the id/mime/size needed
     * to construct a [LI_CLIPBOARD_KIND_REF] frame.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun uploadClipboardBlob(mime: String, data: ByteArray): ClipboardBlobUploadResult {
        val url = getHttpsUrl(true).newBuilder()
            .addPathSegments("api/v1/clipboard/blob")
            .build()
        val body = data.toRequestBody(mime.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("X-Clipboard-Mime", mime)
            .build()
        clipboardBlobClient().newCall(request).execute().use { response ->
                val responseBody = response.body
                if (!response.isSuccessful) {
                    throw HostHttpResponseException(response.code, response.message)
                }
                val text = responseBody.string()
                return parseClipboardBlobUploadResponse(text, mime, data.size.toLong())
            }
    }

    /**
     * Download a previously advertised clipboard blob by id. Blocks on the
     * calling thread. Caller is responsible for enforcing any size cap before
     * passing the bytes on to a decoder.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun downloadClipboardBlob(id: String): ByteArray {
        val url = getHttpsUrl(true).newBuilder()
            .addPathSegments("api/v1/clipboard/blob")
            .addPathSegment(id)
            .build()
        val request = Request.Builder().url(url).get().build()
        clipboardBlobClient().newCall(request).execute().use { response ->
                val responseBody = response.body
                if (!response.isSuccessful) {
                    throw HostHttpResponseException(response.code, response.message)
                }
                return responseBody.bytes()
            }
    }

    /**
     * OkHttp client tuned for clipboard blob transfers: same mTLS pipeline as
     * the regular long-timeout client but with a finite read timeout so a
     * silently wedged server socket eventually surfaces as IOException instead
     * of hanging the IO executor forever.
     */
    private fun clipboardBlobClient(): OkHttpClient =
        httpClientLongConnectTimeout.newBuilder()
            .readTimeout(CLIPBOARD_BLOB_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(CLIPBOARD_BLOB_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()

    private fun parseClipboardBlobUploadResponse(
        body: String,
        fallbackMime: String,
        fallbackSize: Long
    ): ClipboardBlobUploadResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Empty clipboard blob upload response")
        }
        val obj = try {
            JSONObject(trimmed)
        } catch (e: org.json.JSONException) {
            throw IOException("Clipboard blob upload response is not JSON: ${e.message}")
        }
        val id = obj.optString("id").trim()
        if (id.isEmpty()) {
            throw IOException("Clipboard blob upload response missing id")
        }
        val mime = obj.optString("mime").ifBlank { fallbackMime }
        val sizeRaw = obj.optLong("size", -1L)
        val size = if (sizeRaw >= 0) sizeRaw else fallbackSize
        return ClipboardBlobUploadResult(id, mime, size)
    }

    companion object {
        const val DEFAULT_HTTPS_PORT = 47984
        const val DEFAULT_HTTP_PORT = 47989
        const val SHORT_CONNECTION_TIMEOUT = 3000
        const val LONG_CONNECTION_TIMEOUT = 5000
        const val READ_TIMEOUT = 7000

        /**
         * Per-attempt timeout for clipboard blob upload/download. Generous
         * enough for a multi-MiB image over a slow link, but bounded so a
         * silently dead host eventually surfaces as a retryable IOException.
         */
        private const val CLIPBOARD_BLOB_TIMEOUT_MS = 30_000

        private val verbose = BuildConfig.DEBUG

        private fun getDefaultTrustManager(): X509TrustManager {
            try {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)

                for (tm in tmf.trustManagers) {
                    if (tm is X509TrustManager) {
                        return tm
                    }
                }
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            } catch (e: java.security.KeyStoreException) {
                throw RuntimeException(e)
            }

            throw IllegalStateException("No X509 trust manager found")
        }

        @Throws(XmlPullParserException::class, IOException::class)
        fun getXmlString(r: Reader, tagname: String, throwIfMissing: Boolean): String? {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(r)
            var eventType = xpp.eventType
            val currentTag = Stack<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (xpp.name == "root") {
                            verifyResponseStatus(xpp)
                        }
                        currentTag.push(xpp.name)
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag.pop()
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTag.peek() == tagname) {
                            return xpp.text
                        }
                    }
                }
                eventType = xpp.next()
            }

            if (throwIfMissing) {
                throw XmlPullParserException("Missing mandatory field in host response: $tagname")
            }

            return null
        }

        @Throws(XmlPullParserException::class, IOException::class)
        fun getXmlString(str: String, tagname: String, throwIfMissing: Boolean): String? {
            return getXmlString(StringReader(str), tagname, throwIfMissing)
        }

        private fun verifyResponseStatus(xpp: XmlPullParser) {
            val statusCode = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_code").toLong().toInt()
            if (statusCode != 200) {
                var statusMsg = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_message")
                var code = statusCode
                if (code == -1 && "Invalid" == statusMsg) {
                    code = 418
                    statusMsg = "Missing audio capture device. Reinstall GeForce Experience."
                }
                throw HostHttpResponseException(code, statusMsg)
            }
        }

        private fun makeTuple(address: String?, port: Int): ComputerDetails.AddressTuple? {
            return if (address == null) null else ComputerDetails.AddressTuple(address, port)
        }

        @Throws(XmlPullParserException::class, IOException::class)
        fun getAppListByReader(r: Reader): LinkedList<NvApp> {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(r)
            var eventType = xpp.eventType
            val appList = LinkedList<NvApp>()
            val currentTag = Stack<String>()
            var rootTerminated = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (xpp.name == "root") {
                            verifyResponseStatus(xpp)
                        }
                        currentTag.push(xpp.name)
                        if (xpp.name == "App") {
                            appList.addLast(NvApp())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag.pop()
                        if (xpp.name == "root") {
                            rootTerminated = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val app = appList.last()
                        when (currentTag.peek()) {
                            "AppTitle" -> app.appName = xpp.text
                            "ID" -> app.setAppId(xpp.text)
                            "IsHdrSupported" -> app.hdrSupported = (xpp.text == "1")
                            "SuperCmds" -> {
                                val cmdListStr = xpp.text
                                if (cmdListStr != "null") {
                                    app.setCmdList(xpp.text)
                                }
                            }
                        }
                    }
                }
                eventType = xpp.next()
            }

            if (!rootTerminated) {
                throw XmlPullParserException("Malformed XML: Root tag was not terminated")
            }

            val i: MutableListIterator<NvApp> = appList.listIterator()
            while (i.hasNext()) {
                val app = i.next()
                if (!app.isInitialized()) {
                    LimeLog.warning("GFE returned incomplete app: ${app.appId} ${app.appName}")
                    i.remove()
                }
            }

            return appList
        }

        private val hexArray = "0123456789ABCDEF".toCharArray()

        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }
    }
}

// ---------------------------------------------------------------------------
// 智能码率 (ABR) 数据结构
// ---------------------------------------------------------------------------

data class AbrCapabilities(
    val supported: Boolean,
    val version: Int,
    val features: List<String>
)

data class AbrConfig(
    val enabled: Boolean,
    val minBitrate: Int,
    val maxBitrate: Int,
    val mode: String  // "quality" | "balanced" | "lowLatency"
)

data class NetworkFeedback(
    val packetLoss: Float,
    val rttMs: Int,
    val decodeFps: Float,
    val droppedFrames: Int,
    val currentBitrate: Int
)

data class AbrAction(
    val newBitrate: Int?,
    val reason: String?
)

// ---------------------------------------------------------------------------
// 剪贴板 blob 通道 (Sunshine /api/v1/clipboard/blob)
// ---------------------------------------------------------------------------

/**
 * Result of a successful clipboard blob upload. The host stores the blob
 * temporarily and addresses it by [id]; clients (this one and the host's
 * peers) fetch it back via GET /api/v1/clipboard/blob/{id}.
 */
data class ClipboardBlobUploadResult(
    val id: String,
    val mime: String,
    val size: Long,
)

