package com.limelight.nvstream

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

import com.limelight.utils.NetHelper

import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Semaphore

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

import org.xmlpull.v1.XmlPullParserException

import com.limelight.LimeLog
import com.limelight.R
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.HdrCapabilityHelper

open class NvConnection(
    private val appContext: Context,
    host: ComputerDetails.AddressTuple,
    httpsPort: Int,
    private val uniqueId: String,
    pairName: String,
    config: StreamConfiguration,
    private val cryptoProvider: LimelightCryptoProvider,
    serverCert: X509Certificate?,
    displayName: String? = null,
    forceResumeCurrentSession: Boolean = false
) {
    private val clientName: String =
        pairName.ifEmpty {
            // Settings.Global.getString can return null on some OEM ROMs.
            Settings.Global.getString(appContext.contentResolver, "device_name")
                ?: Build.MODEL
                ?: "Moonlight V+ Client"
        }
    private val context: ConnectionContext = ConnectionContext()
    private val isMonkey: Boolean = ActivityManager.isUserAMonkey()

    @Volatile
    var serverVersion: String? = null
        private set

    init {
        context.serverAddress = host
        context.httpsPort = httpsPort
        context.streamConfig = config
        context.serverCert = serverCert
        context.displayName = displayName
        context.forceResumeCurrentSession = forceResumeCurrentSession

        context.riKey = generateRiAesKey()
        context.riKeyId = generateRiKeyId()

        val brightnessRange = HdrCapabilityHelper.getBrightnessRangeAsInts(appContext).let {
            if (config.hdrBrightnessOverride) {
                HdrCapabilityHelper.applyBrightnessOverride(it, config.hdrPeakBrightnessNits)
            } else {
                it
            }
        }
        context.minBrightness = brightnessRange[0]
        context.maxBrightness = brightnessRange[1]
        context.maxAverageBrightness = brightnessRange[2]

        if (config.hdrBrightnessOverride) {
            LimeLog.info(
                "HDR brightness override: min=${context.minBrightness}, " +
                    "max=${context.maxBrightness}, avg=${context.maxAverageBrightness} nits"
            )
        }
    }

    fun stop() {
        MoonBridge.interruptConnection()

        synchronized(MoonBridge::class.java) {
            MoonBridge.stopConnection()
            MoonBridge.cleanupBridge()
        }

        connectionAllowed.release()
    }

    /**
     * 创建一个新的 NvHTTP 实例（线程安全：调用方负责自行分线程）。
     * 主要用于 [com.limelight.AdaptiveBitrateService] 等需要直接访问服务端 API 的旁路逻辑。
     */
    fun createNvHttp(): NvHTTP =
        NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)

    /** 应用 ABR 调整后的码率到本地配置（不发起网络请求）。*/
    fun applyBitrateLocally(bitrateKbps: Int) {
        context.streamConfig.bitrate = bitrateKbps
    }

    private fun resolveServerAddress(): InetAddress {
        val serverAddress = context.serverAddress
        val addrs = InetAddress.getAllByName(serverAddress.address)
        for (addr in addrs) {
            try {
                Socket().use { s ->
                    s.setSoLinger(true, 0)
                    s.connect(InetSocketAddress(addr, serverAddress.port), 1000)
                    return addr
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        if (addrs.isNotEmpty()) {
            return addrs[0]
        } else {
            throw IOException("No addresses found for ${context.serverAddress}")
        }
    }

    @Suppress("DEPRECATION")
    private fun detectServerConnectionType(): Int {
        val isVpn = NetHelper.isActiveNetworkVpn(appContext)
        val isMobile = NetHelper.isActiveNetworkMobile(appContext)

        if (isVpn || isMobile) {
            return StreamConfiguration.STREAM_CFG_REMOTE
        }

        val connMgr = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connMgr.activeNetwork
            if (activeNetwork != null) {
                val linkProperties = connMgr.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    val serverAddress: InetAddress
                    try {
                        serverAddress = resolveServerAddress()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        return StreamConfiguration.STREAM_CFG_AUTO
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val nat64Prefix = linkProperties.nat64Prefix
                        if (nat64Prefix != null && nat64Prefix.contains(serverAddress)) {
                            return StreamConfiguration.STREAM_CFG_REMOTE
                        }
                    }

                    for (route in linkProperties.routes) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && route.type != android.net.RouteInfo.RTN_UNICAST) {
                            continue
                        }

                        if (route.matches(serverAddress)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                if (!route.hasGateway()) {
                                    return StreamConfiguration.STREAM_CFG_LOCAL
                                }
                            } else {
                                val gateway = route.gateway
                                if (gateway == null || gateway.isAnyLocalAddress) {
                                    return StreamConfiguration.STREAM_CFG_LOCAL
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val activeNetworkInfo = connMgr.activeNetworkInfo
            if (activeNetworkInfo != null) {
                when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_VPN,
                    ConnectivityManager.TYPE_MOBILE,
                    ConnectivityManager.TYPE_MOBILE_DUN,
                    ConnectivityManager.TYPE_MOBILE_HIPRI,
                    ConnectivityManager.TYPE_MOBILE_MMS,
                    ConnectivityManager.TYPE_MOBILE_SUPL,
                    ConnectivityManager.TYPE_WIMAX ->
                        return StreamConfiguration.STREAM_CFG_REMOTE
                }
            }
        }

        return StreamConfiguration.STREAM_CFG_AUTO
    }

    private inline fun <T> timeConnectionStep(name: String, block: () -> T): T {
        val startTime = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            LimeLog.info("Connection step '$name' completed in ${SystemClock.elapsedRealtime() - startTime}ms")
        }
    }

    @Throws(XmlPullParserException::class, IOException::class, InterruptedException::class)
    private fun startApp(): Boolean {
        val h = NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)
        val connListener = context.connListener
        val streamConfig = context.streamConfig

        val serverInfo = timeConnectionStep("serverinfo") { h.getServerInfo(true) }

        context.serverAppVersion = h.getServerVersion(serverInfo)

        val details = h.getComputerDetails(serverInfo)
        details.serverCert = context.serverCert
        serverVersion = details.sunshineVersion ?: context.serverAppVersion
        context.isNvidiaServerSoftware = details.nvidiaServer
        context.supportsDesktopSpecialApp = details.supportsDesktopSpecialApp

        context.serverGfeVersion = h.getGfeVersion(serverInfo)

        if (details.pairState != PairingManager.PairState.PAIRED) {
            LimeLog.warning("Rejecting NOT_PAIRED serverinfo while starting stream; trusted=${details.serverInfoTrustedByCert}")
            connListener.displayMessage("Device not paired with computer")
            return false
        }

        context.serverCodecModeSupport = h.getServerCodecModeSupport(serverInfo).toInt()

        context.negotiatedHdr = (streamConfig.supportedVideoFormats and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0
        if ((context.serverCodecModeSupport and 0x20200) == 0 && context.negotiatedHdr) {
            connListener.displayTransientMessage("Your PC GPU does not support streaming HDR. The stream will be SDR.")
            context.negotiatedHdr = false
        }

        if ((streamConfig.reqWidth > 4096 || streamConfig.reqHeight > 4096) &&
            (h.getServerCodecModeSupport(serverInfo) and 0x200) == 0L && context.isNvidiaServerSoftware
        ) {
            connListener.displayMessage("Your host PC does not support streaming at resolutions above 4K.")
            return false
        } else if ((streamConfig.reqWidth > 4096 || streamConfig.reqHeight > 4096) &&
            (streamConfig.supportedVideoFormats and MoonBridge.VIDEO_FORMAT_MASK_H264.inv()) == 0
        ) {
            connListener.displayMessage("Your streaming device must support HEVC or AV1 to stream at resolutions above 4K.")
            return false
        } else if (streamConfig.reqHeight >= 2160 && !h.supports4K(serverInfo)) {
            connListener.displayTransientMessage("You must update GeForce Experience to stream in 4K. The stream will be 1080p.")
            context.negotiatedWidth = 1920
            context.negotiatedHeight = 1080
        } else {
            context.negotiatedWidth = streamConfig.width
            context.negotiatedHeight = streamConfig.height
        }

        if (streamConfig.remote == StreamConfiguration.STREAM_CFG_AUTO) {
            context.negotiatedRemoteStreaming = timeConnectionStep("connection type detection") {
                detectServerConnectionType()
            }
            context.negotiatedPacketSize =
                if (context.negotiatedRemoteStreaming == StreamConfiguration.STREAM_CFG_REMOTE)
                    1024 else streamConfig.maxPacketSize
        } else {
            context.negotiatedRemoteStreaming = streamConfig.remote
            context.negotiatedPacketSize = streamConfig.maxPacketSize
        }

        var app = streamConfig.app

        if (!streamConfig.app.isInitialized()) {
            LimeLog.info("Using deprecated app lookup method - Please specify an app ID in your StreamConfiguration instead")
            app = h.getAppByName(streamConfig.app.appName) ?: run {
                connListener.displayMessage("The app ${streamConfig.app.appName} is not in GFE app list")
                return false
            }
        }

        val currentGameId = h.getCurrentGame(serverInfo)

        if (context.forceResumeCurrentSession) {
            val resumeAppId = if (currentGameId != 0) currentGameId else app.appId
            return timeConnectionStep("resume app") { resumeExistingSession(h, context, resumeAppId) }
        }

        if (currentGameId != 0) {
            return if (currentGameId == app.appId) {
                timeConnectionStep("resume app") { resumeExistingSession(h, context, app.appId) }
            } else {
                timeConnectionStep("quit and launch app") { quitAndLaunch(h, context) }
            }
        }

        return timeConnectionStep("launch app") { launchNotRunningApp(h, context) }
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    private fun resumeExistingSession(h: NvHTTP, context: ConnectionContext, appId: Int): Boolean {
        val connListener = context.connListener

        if (!ensureDesktopSpecialAppSupported(context, appId)) {
            return false
        }

        try {
            if (!h.launchApp(context, "resume", appId, context.negotiatedHdr)) {
                connListener.displayMessage("Failed to resume existing session")
                return false
            }
        } catch (e: HostHttpResponseException) {
            when (e.getErrorCode()) {
                470 -> {
                    connListener.displayMessage(
                        "This session wasn't started by this device," +
                                " so it cannot be resumed. End streaming on the original " +
                                "device or the PC itself and try again. (Error code: ${e.getErrorCode()})"
                    )
                    return false
                }
                525 -> {
                    connListener.displayMessage(
                        "The application is minimized. Resume it on the PC manually or " +
                                "quit the session and start streaming again."
                    )
                    return false
                }
                else -> throw e
            }
        }

        LimeLog.info("Resumed existing game session")
        return true
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    protected fun quitAndLaunch(h: NvHTTP, context: ConnectionContext): Boolean {
        val connListener = context.connListener

        if (!ensureDesktopSpecialAppSupported(context, context.streamConfig.app.appId)) {
            return false
        }

        try {
            if (!h.quitApp()) {
                connListener.displayMessage("Failed to quit previous session! You must quit it manually")
                return false
            }
        } catch (e: HostHttpResponseException) {
            if (e.getErrorCode() == 599) {
                connListener.displayMessage(
                    "This session wasn't started by this device," +
                            " so it cannot be quit. End streaming on the original " +
                            "device or the PC itself. (Error code: ${e.getErrorCode()})"
                )
                return false
            } else {
                throw e
            }
        }

        return launchNotRunningApp(h, context)
    }

    @Throws(IOException::class, XmlPullParserException::class, InterruptedException::class)
    private fun launchNotRunningApp(h: NvHTTP, context: ConnectionContext): Boolean {
        if (!ensureDesktopSpecialAppSupported(context, context.streamConfig.app.appId)) {
            return false
        }

        if (!h.launchApp(context, "launch", context.streamConfig.app.appId, context.negotiatedHdr)) {
            context.connListener.displayMessage("Failed to launch application")
            return false
        }

        LimeLog.info("Launched new game session")
        return true
    }

    private fun ensureDesktopSpecialAppSupported(context: ConnectionContext, appId: Int): Boolean {
        if (appId != NvApp.DESKTOP_APP_ID || context.supportsDesktopSpecialApp) {
            return true
        }

        context.connListener.displayMessage(appContext.getString(R.string.error_desktop_special_app_unsupported))
        return false
    }

    fun start(audioRenderer: AudioRenderer, videoDecoderRenderer: VideoDecoderRenderer, connectionListener: NvConnectionListener) {
        Thread {
            context.connListener = connectionListener
            context.videoCapabilities = videoDecoderRenderer.getCapabilities()

            val appName = context.streamConfig.app.appName

            context.connListener.stageStarting(appName)

            try {
                if (!timeConnectionStep("app negotiation") { startApp() }) {
                    context.connListener.stageFailed(appName, 0, 0)
                    return@Thread
                }
                context.connListener.stageComplete(appName)
            } catch (e: HostHttpResponseException) {
                e.printStackTrace()
                val hostMessage = when (e.getSunshineErrorCode()) {
                    "VDD_NOT_SUPPORTED" ->
                        appContext.getString(R.string.error_vdd_unsupported)
                    "VDD_DRIVER_MISSING", "VDD_DRIVER_UNREACHABLE" ->
                        appContext.getString(R.string.error_vdd_unavailable)
                    else -> e.message
                }
                context.connListener.displayMessage(hostMessage)
                context.connListener.stageFailed(appName, 0, e.getErrorCode())
                return@Thread
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                context.connListener.displayMessage(e.message ?: "")
                context.connListener.stageFailed(appName, MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989, 0)
                return@Thread
            } catch (e: IOException) {
                e.printStackTrace()
                context.connListener.displayMessage(e.message ?: "")
                context.connListener.stageFailed(appName, MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989, 0)
                return@Thread
            } catch (e: InterruptedException) {
                context.connListener.displayMessage("Connection interrupted")
                context.connListener.stageFailed(appName, 0, 0)
                return@Thread
            }

            val ib = ByteBuffer.allocate(16)
            ib.putInt(context.riKeyId)

            try {
                val waitStartTime = SystemClock.elapsedRealtime()
                connectionAllowed.acquire()
                val waitMs = SystemClock.elapsedRealtime() - waitStartTime
                if (waitMs > 10) {
                    LimeLog.info("Connection step 'connection slot wait' completed in ${waitMs}ms")
                }
            } catch (e: InterruptedException) {
                context.connListener.displayMessage(e.message ?: "")
                context.connListener.stageFailed(appName, 0, 0)
                return@Thread
            }

            synchronized(MoonBridge::class.java) {
                MoonBridge.setupBridge(videoDecoderRenderer, audioRenderer, connectionListener)
                val ret = timeConnectionStep("native connection") {
                    MoonBridge.startConnection(
                        context.serverAddress.address,
                        context.serverAppVersion, context.serverGfeVersion, context.rtspSessionUrl,
                        context.serverCodecModeSupport,
                        context.negotiatedWidth, context.negotiatedHeight,
                        context.streamConfig.refreshRate, context.streamConfig.bitrate,
                        context.negotiatedPacketSize, context.negotiatedRemoteStreaming,
                        context.streamConfig.audioConfiguration.toInt(),
                        context.streamConfig.supportedVideoFormats,
                        context.streamConfig.clientRefreshRateX100,
                        context.riKey.encoded, ib.array(),
                        context.videoCapabilities,
                        context.streamConfig.colorSpace,
                        context.streamConfig.colorRange,
                        context.streamConfig.hdrMode,
                        context.streamConfig.getEnableMic(),
                        context.streamConfig.getControlOnly(),
                        context.streamConfig.audioCodec,
                        context.streamConfig.audioBitrate
                    )
                }
                if (ret != 0) {
                    connectionAllowed.release()
                    return@synchronized
                }
            }
        }.start()
    }

    fun sendMouseMove(deltaX: Short, deltaY: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseMove(deltaX, deltaY)
        }
    }

    fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {
        if (!isMonkey) {
            MoonBridge.sendMousePosition(x, y, referenceWidth, referenceHeight)
        }
    }

    fun sendMouseMoveAsMousePosition(deltaX: Short, deltaY: Short, referenceWidth: Short, referenceHeight: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseMoveAsMousePosition(deltaX, deltaY, referenceWidth, referenceHeight)
        }
    }

    fun sendMouseButtonDown(mouseButton: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.PRESS_EVENT, mouseButton)
        }
    }

    fun sendMouseButtonUp(mouseButton: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.RELEASE_EVENT, mouseButton)
        }
    }

    fun sendControllerInput(
        controllerNumber: Short, activeGamepadMask: Short, buttonFlags: Int,
        leftTrigger: Byte, rightTrigger: Byte,
        leftStickX: Short, leftStickY: Short,
        rightStickX: Short, rightStickY: Short
    ) {
        if (!isMonkey) {
            MoonBridge.sendMultiControllerInput(
                controllerNumber, activeGamepadMask, buttonFlags,
                leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY
            )
        }
    }

    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        if (!isMonkey) {
            MoonBridge.sendKeyboardInput(keyMap, keyDirection, modifier, flags)
        }
    }

    fun sendMouseScroll(scrollClicks: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll((scrollClicks * 120).toShort())
        }
    }

    fun sendMouseHScroll(scrollClicks: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll((scrollClicks * 120).toShort())
        }
    }

    fun sendMouseHighResScroll(scrollAmount: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll(scrollAmount)
        }
    }

    fun sendMouseHighResHScroll(scrollAmount: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll(scrollAmount)
        }
    }

    fun sendTouchEvent(
        eventType: Byte, pointerId: Int, x: Float, y: Float, pressureOrDistance: Float,
        contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendTouchEvent(eventType, pointerId, x, y, pressureOrDistance, contactAreaMajor, contactAreaMinor, rotation)
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendTouchpadEvent(
        eventType: Byte, pointerId: Int, x: Float, y: Float, pressure: Float,
        contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short,
        deviceWidthMm: Short, deviceHeightMm: Short, buttonState: Byte
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendTouchpadEvent(
                eventType, pointerId, x, y, pressure,
                contactAreaMajor, contactAreaMinor, rotation,
                deviceWidthMm, deviceHeightMm, buttonState
            )
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendTouchpadFrameEvent(
        contactCount: Byte, eventTypes: ByteArray, pointerIds: IntArray,
        x: FloatArray, y: FloatArray, pressure: FloatArray, rotation: Short,
        deviceWidthMm: Short, deviceHeightMm: Short, buttonState: Byte
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendTouchpadFrameEvent(
                contactCount, eventTypes, pointerIds,
                x, y, pressure, rotation,
                deviceWidthMm, deviceHeightMm, buttonState
            )
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendPenEvent(
        eventType: Byte, toolType: Byte, penButtons: Byte, x: Float, y: Float,
        pressureOrDistance: Float, contactAreaMajor: Float, contactAreaMinor: Float,
        rotation: Short, tilt: Byte
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendPenEvent(eventType, toolType, penButtons, x, y, pressureOrDistance, contactAreaMajor, contactAreaMinor, rotation, tilt)
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendControllerArrivalEvent(
        controllerNumber: Byte, activeGamepadMask: Short, type: Byte,
        supportedButtonFlags: Int, capabilities: Short
    ): Int {
        return MoonBridge.sendControllerArrivalEvent(controllerNumber, activeGamepadMask, type, supportedButtonFlags, capabilities)
    }

    fun sendControllerTouchEvent(
        controllerNumber: Byte, eventType: Byte, pointerId: Int,
        x: Float, y: Float, pressure: Float
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendControllerTouchEvent(controllerNumber, eventType, pointerId, x, y, pressure)
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendControllerMotionEvent(
        controllerNumber: Byte, motionType: Byte,
        x: Float, y: Float, z: Float
    ): Int {
        return if (!isMonkey) {
            MoonBridge.sendControllerMotionEvent(controllerNumber, motionType, x, y, z)
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }
    }

    fun sendControllerBatteryEvent(controllerNumber: Byte, batteryState: Byte, batteryPercentage: Byte) {
        MoonBridge.sendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage)
    }

    fun sendUtf8Text(text: String) {
        if (!isMonkey) {
            MoonBridge.sendUtf8Text(text)
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun doStopAndQuit() {
        this.stop()
        Thread {
            val h: NvHTTP
            try {
                h = NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            try {
                h.quitApp()
            } catch (e: InterruptedException) {
                LimeLog.info("Quit app interrupted")
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
            }
        }.start()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun sendSuperCmd(cmdId: String) {
        Thread {
            val h: NvHTTP
            try {
                h = NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            try {
                h.sendSuperCmd(cmdId)
            } catch (e: InterruptedException) {
                LimeLog.info("Send super command interrupted")
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
            }
        }.start()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun setBitrate(bitrateKbps: Int, callback: BitrateAdjustmentCallback?) {
        Thread {
            val h: NvHTTP
            try {
                h = NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)
                LimeLog.info("NvHTTP created successfully for bitrate adjustment")
            } catch (e: IOException) {
                LimeLog.warning("Failed to create NvHTTP for bitrate adjustment: ${e.message}")
                callback?.onFailure("Failed to create HTTP connection: ${e.message}")
                return@Thread
            }
            try {
                LimeLog.info("Sending bitrate adjustment request...")
                val success = h.setBitrate(bitrateKbps)
                if (success) {
                    context.streamConfig.bitrate = bitrateKbps
                    LimeLog.info("Bitrate adjustment successful, updated local config to $bitrateKbps kbps")
                    callback?.onSuccess(bitrateKbps)
                } else {
                    LimeLog.warning("Bitrate adjustment request failed (server returned false)")
                    callback?.onFailure("Server returned failure response")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                LimeLog.warning("Bitrate adjustment interrupted: ${e.message}")
                callback?.onFailure("Operation interrupted")
            } catch (e: IOException) {
                LimeLog.warning("Failed to set bitrate: ${e.message}")
                e.printStackTrace()
                callback?.onFailure("Network error: ${e.message}")
            } catch (e: XmlPullParserException) {
                LimeLog.warning("Failed to set bitrate: ${e.message}")
                e.printStackTrace()
                callback?.onFailure("Network error: ${e.message}")
            }
        }.start()
    }

    interface BitrateAdjustmentCallback {
        fun onSuccess(newBitrate: Int)
        fun onFailure(errorMessage: String)
    }

    interface DisplayRotationCallback {
        fun onSuccess(angle: Int)
        fun onFailure(errorMessage: String)
    }

    fun rotateDisplay(angle: Int, callback: DisplayRotationCallback?) {
        Thread {
            val h: NvHTTP
            try {
                h = NvHTTP(context.serverAddress, context.httpsPort, uniqueId, clientName, context.serverCert, cryptoProvider)
            } catch (e: IOException) {
                LimeLog.warning("Failed to create NvHTTP for display rotation: ${e.message}")
                callback?.onFailure("Failed to create HTTP connection: ${e.message}")
                return@Thread
            }

            try {
                LimeLog.info("Sending display rotation request: $angle degrees")
                val success = h.rotateDisplay(angle, context.displayName)

                if (success) {
                    LimeLog.info("Display rotation successful: $angle degrees")
                    callback?.onSuccess(angle)
                } else {
                    LimeLog.warning("Display rotation request failed (server returned false)")
                    callback?.onFailure("Server returned failure response")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                LimeLog.warning("Display rotation interrupted: ${e.message}")
                callback?.onFailure("Operation interrupted")
            } catch (e: FileNotFoundException) {
                LimeLog.warning("Display rotation not supported by server (404): ${e.message}")
                callback?.onFailure("服务端不支持显示旋转功能，请更新服务端版本")
            } catch (e: IOException) {
                LimeLog.warning("Failed to rotate display: ${e.message}")
                callback?.onFailure("网络错误: ${e.message}")
            }
        }.start()
    }

    val host: String
        get() = context.serverAddress.address

    val currentBitrate: Int
        get() = context.streamConfig.bitrate

    companion object {
        private val connectionAllowed = Semaphore(1)

        fun findExternalAddressForMdns(stunHostname: String, stunPort: Int): String {
            return MoonBridge.findExternalAddressIP4(stunHostname, stunPort)
        }

        private fun generateRiAesKey(): SecretKey {
            try {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(128)
                return keyGen.generateKey()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        private fun generateRiKeyId(): Int {
            return SecureRandom().nextInt()
        }
    }
}
