@file:Suppress("DEPRECATION")
package com.limelight

import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.audio.AudioDiagnostics
import com.limelight.binding.audio.AudioHapticsRuntimePolicy
import com.limelight.binding.audio.AudioHapticsSettings
import com.limelight.binding.audio.AudioVibrationService
import com.limelight.binding.audio.MicrophoneManager
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.GameInputDevice
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.advance_setting.ControllerManager
import com.limelight.binding.input.advance_setting.KeyboardUIController
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.NativeTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.binding.input.evdev.EvdevListener
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.framegen.FramegenCapture
import com.limelight.framegen.FramegenAdaptiveController
import com.limelight.framegen.FramegenInterceptor
import com.limelight.framegen.FramegenPerformanceEnricher
import com.limelight.framegen.FramegenRuntimeConfig
import com.limelight.framegen.FramegenRuntimePlanner
import com.limelight.gamemenu.GameMenu
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.binding.video.PerformanceInfo
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.AdaptiveBitrateService
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.input.ClipboardSyncManager
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.services.StreamNotificationService
import com.limelight.ui.CursorView
import com.limelight.ui.GameGestures
import com.limelight.ui.StreamView
import com.limelight.utils.Dialog
import com.limelight.utils.PanZoomHandler
import com.limelight.utils.FullscreenProgressOverlay
import com.limelight.utils.UiHelper
import com.limelight.utils.NetHelper
import com.limelight.utils.AnalyticsManager
import com.limelight.utils.AppCacheManager
import com.limelight.utils.AppSettingsManager
import com.limelight.services.KeyboardAccessibilityService

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.hardware.input.InputManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.preference.PreferenceManager
import android.util.Rational
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.View.OnTouchListener
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.annotation.RequiresApi

import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import androidx.core.content.edit
import androidx.core.net.toUri

class Game : Activity(), SurfaceHolder.Callback,
    OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
    OnSystemUiVisibilityChangeListener, GameGestures, StreamView.InputCallbacks,
    PerfOverlayListener, UsbDriverService.UsbDriverStateListener, View.OnKeyListener,
    KeyboardAccessibilityService.KeyEventCallback {

    // 这个标志位用于区分事件是来自无障碍服务还是来自UI（如StreamView）
    var isEventFromAccessibilityService = false

    lateinit var controllerHandler: ControllerHandler
    lateinit var touchInputHandler: TouchInputHandler
    var virtualController: VirtualController? = null
    lateinit var panZoomHandler: PanZoomHandler
    private var audioVibrationService: AudioVibrationService? = null
    private var appliedAudioHapticsSettings: AudioHapticsSettings? = null

    interface PerformanceInfoDisplay {
        fun display(performanceAttrs: Map<String, String>)
    }

    var controllerManager: ControllerManager? = null
    private val crownSessionController = CrownSessionController(this) { controllerManager }
    private var standaloneKeyboardUI: KeyboardUIController? = null
    private val performanceInfoDisplays = ArrayList<PerformanceInfoDisplay>()

    var microphoneManager: MicrophoneManager? = null
    var micButton: ImageButton? = null
    lateinit var prefConfig: PreferenceConfiguration
    lateinit var orientationManager: OrientationManager
    private lateinit var tombstonePrefs: SharedPreferences

    var conn: NvConnection? = null
    var progressOverlay: FullscreenProgressOverlay? = null

    // 智能码率
    var adaptiveBitrateService: AdaptiveBitrateService? = null
    @Volatile private var latestPerfInfo: PerformanceInfo? = null

    // Sunshine clipboard sync — null until pref toggles enable it.
    private var clipboardSyncManager: ClipboardSyncManager? = null

    var displayedFailureDialog = false
    var connecting = false
    var connected = false
    private var activeGameMenu: GameMenu? = null
    private var controllerShortcutHintView: View? = null
    private var autoEnterPip = false
    private var surfaceCreated = false
    var attemptedConnection = false
    var analyticsManager: AnalyticsManager? = null
    var streamStartTime: Long = 0
    var accumulatedStreamTime: Long = 0
    var lastActiveTime: Long = 0
    var isStreamingActive = false
    private var suppressPipRefCount = 0
    var pcName: String? = null
    var appName: String? = null
    lateinit var app: NvApp
    private var desiredRefreshRate = 0f
    private var selectedDisplayMode: DisplayModeManager.DisplayModeSelection? = null
    var appSettingsManager: AppSettingsManager? = null
    var computerUuid: String? = null

    lateinit var inputCaptureProvider: InputCaptureProvider
    var grabbedInput = true
    var cursorVisible = false
    lateinit var streamView: StreamView
    private var externalStreamView: StreamView? = null
    private var previousTimeMillis: Long = 0
    private var previousRxBytes: Long = 0

    lateinit var notificationOverlayManager: NotificationOverlayManager
    private var performanceOverlayManager: PerformanceOverlayManager? = null
    private var jitterMonitorManager: JitterMonitorManager? = null
    lateinit var cursorServiceManager: CursorServiceManager
    lateinit var floatBallHandler: FloatBallHandler
    lateinit var connectionCallbackHandler: ConnectionCallbackHandler
    lateinit var keyboardInputHandler: KeyboardInputHandler

    private var decoderRenderer: MediaCodecDecoderRenderer? = null
    private var framegenCapture: FramegenCapture? = null
    private val framegenAdaptiveController = FramegenAdaptiveController()
    private val framegenSurfaceExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FramegenSurface").apply { isDaemon = true }
    }
    private val framegenSurfaceGeneration = AtomicInteger(0)
    private var framegenInputHdrEnabled = false
    private var framegenEnabledToastShown = false
    private var reportedCrash = false

    private var highPerfWifiLock: WifiManager.WifiLock? = null
    private var lowLatencyWifiLock: WifiManager.WifiLock? = null

    var currentHostAddress: String? = null
    private var shouldResumeSession = false
    private var isExtremeResumeEnabled = false
    private var isChangingResolution = false
    private var audioRenderer: com.limelight.binding.audio.SmartAudioRenderer? = null

    enum class BackKeyMenuMode {
        GAME_MENU, CROWN_MODE, NO_MENU, NO_MENU_LOCKED
    }

    fun setcurrentBackKeyMenu(currentBackKeyMenu: BackKeyMenuMode) {
        crownSessionController.setBackKeyMenuMode(currentBackKeyMenu)
    }

    fun getCurrentBackKeyMenuMode(): BackKeyMenuMode = crownSessionController.backKeyMenuMode

    fun toggleVirtualControllerVisibility() {
        crownSessionController.toggleElementsVisibility()
    }

    fun toggleBackKeyMenuType() {
        crownSessionController.toggleBackKeyMenuType()
    }

    var isTouchOverrideEnabled = false

    fun getisTouchOverrideEnabled(): Boolean = isTouchOverrideEnabled

    fun setisTouchOverrideEnabled(isTouchOverrideEnabled: Boolean) {
        this.isTouchOverrideEnabled = isTouchOverrideEnabled
    }

    // 仅鼠标移动模式：禁用所有屏幕触摸产生的鼠标点击事件，只保留鼠标移动
    var isMouseMoveOnlyEnabled = false
        private set

    fun toggleMouseMoveOnly() {
        isMouseMoveOnlyEnabled = !isMouseMoveOnlyEnabled
    }

    var usbDriverServiceManager: UsbDriverServiceManager? = null
    var externalDisplayManager: ExternalDisplayManager? = null
    private lateinit var targetDisplayResolver: TargetDisplayResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cancelKeepAliveNotification()
        isChangingResolution = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        UiHelper.setLocale(this)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.decorView.setOnSystemUiVisibilityChangeListener(this)

        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_game)
        window.decorView.findViewById<View>(android.R.id.content).isFocusable = true

        targetDisplayResolver = TargetDisplayResolver(this)
        val initialTargetDisplay = targetDisplayResolver.resolve(
            PreferenceConfiguration.isExternalDisplayEnabled(this)
        )
        prefConfig = PreferenceConfiguration.readPreferences(this, initialTargetDisplay)
        orientationManager = OrientationManager(
            this,
            prefConfig.width,
            prefConfig.height,
            prefConfig.rotableScreen,
            prefConfig.onscreenController || prefConfig.enableCrownFeatures
        ) { currentTargetDisplay }
        tombstonePrefs = getSharedPreferences("DecoderTombstone", 0)

        val globalPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        isExtremeResumeEnabled = globalPrefs.getBoolean("checkbox_extreme_resume", false) &&
                isResumeStreamEnabled

        if (isResumeStreamEnabled) {
            checkNotificationPermission()
        }

        appSettingsManager = AppSettingsManager(this)
        computerUuid = intent.getStringExtra(EXTRA_PC_UUID)
        applyLastSettingsToCurrentSession()

        val customScreenMode = intent.getIntExtra(EXTRA_SCREEN_COMBINATION_MODE, -1)
        if (customScreenMode != -1) {
            prefConfig.screenCombinationMode = customScreenMode
        }
        NativeTouchContext.INTIAL_ZONE_PIXELS = prefConfig.longPressflatRegionPixels.toFloat()
        NativeTouchContext.ENABLE_ENHANCED_TOUCH = prefConfig.enableEnhancedTouch
        NativeTouchContext.ENHANCED_TOUCH_ON_RIGHT = if (prefConfig.enhancedTouchOnWhichSide) -1 else 1
        NativeTouchContext.ENHANCED_TOUCH_ZONE_DIVIDER = prefConfig.enhanceTouchZoneDivider * 0.01f
        NativeTouchContext.POINTER_VELOCITY_FACTOR = prefConfig.pointerVelocityFactor * 0.01f

        orientationManager.setPreferredOrientation()

        if (prefConfig.stretchVideo || DisplayModeManager.shouldIgnoreInsetsForResolution(
                currentTargetDisplay,
                prefConfig.width,
                prefConfig.height,
                prefConfig.usesNativeDisplayMode
            )
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        streamView = findViewById(R.id.surfaceView)
        streamView.setOnGenericMotionListener(this)
        streamView.setOnKeyListener(this)
        streamView.setInputCallbacks(this)

        val cursorOverlayView = findViewById<CursorView>(R.id.cursorOverlay)
        panZoomHandler = PanZoomHandler(this, this, streamView, cursorOverlayView, prefConfig)

        val backgroundTouchView = findViewById<View>(R.id.backgroundTouchView)
        backgroundTouchView.setOnTouchListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sourceFlags = InputDevice.SOURCE_CLASS_BUTTON or
                    InputDevice.SOURCE_CLASS_JOYSTICK or
                    InputDevice.SOURCE_CLASS_POINTER or
                    InputDevice.SOURCE_CLASS_POSITION or
                    InputDevice.SOURCE_CLASS_TRACKBALL
            streamView.requestUnbufferedDispatch(sourceFlags)
            backgroundTouchView.requestUnbufferedDispatch(sourceFlags)
        }

        notificationOverlayManager = NotificationOverlayManager(
            findViewById(R.id.notificationOverlay),
            findViewById(R.id.notificationText)
        ) { prefConfig.bitrate }
        controllerShortcutHintView = findViewById(R.id.controllerShortcutHint)

        micButton = findViewById(R.id.micButton)

        performanceOverlayManager = PerformanceOverlayManager(this, prefConfig) { enabled ->
            jitterMonitorManager?.setEnabled(enabled)
        }
        performanceOverlayManager?.initialize()

        jitterMonitorManager = JitterMonitorManager(this, prefConfig)
        jitterMonitorManager?.initialize()

        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this, this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            streamView.setOnCapturedPointerListener { _, event ->
                touchInputHandler.handleMotionEvent(getMotionEventTargetView(), event)
            }
        }

        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connMgr.isActiveNetworkMetered) {
            displayTransientMessage(resources.getString(R.string.conn_metered))
        }

        val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock")
            highPerfWifiLock?.setReferenceCounted(false)
            highPerfWifiLock?.acquire()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock")
                lowLatencyWifiLock?.setReferenceCounted(false)
                lowLatencyWifiLock?.acquire()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        appName = intent.getStringExtra(EXTRA_APP_NAME)
        pcName = intent.getStringExtra(EXTRA_PC_NAME)
        analyticsManager = AnalyticsManager.getInstance(this)

        val appId = intent.getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID)
        val appSupportsHdr = intent.getBooleanExtra(EXTRA_APP_HDR, false)
        val cmdList = intent.getStringExtra(EXTRA_APP_CMD)

        app = NvApp(appName ?: "app", appId, appSupportsHdr)
        if (cmdList != null) {
            app.setCmdList(cmdList)
        }

        if (appId != StreamConfiguration.INVALID_APP_ID && appName != null && appName != "app") {
            val cacheManager = AppCacheManager(this)
            cacheManager.saveAppInfo(intent.getStringExtra(EXTRA_PC_UUID), app)
        }

        showProgressOverlay()

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish()
            return
        }

        val glPrefs = GlPreferences.readPreferences(this)
        MediaCodecHelper.initialize(this, glPrefs.glRenderer)

        createConnectionAndHandler()
        keyboardInputHandler = KeyboardInputHandler(this)
        keyboardInputHandler.keyboardTranslator = KeyboardTranslator()

        audioVibrationService = AudioVibrationService(this)
        audioVibrationService?.controllerHandler = controllerHandler
        audioVibrationService?.setSettings(
            prefConfig.enableAudioVibration,
            prefConfig.audioVibrationStrength,
            prefConfig.audioVibrationMode,
            prefConfig.audioVibrationScene
        )
        appliedAudioHapticsSettings = AudioHapticsSettings(
            enabled = prefConfig.enableAudioVibration,
            strength = prefConfig.audioVibrationStrength,
            mode = prefConfig.audioVibrationMode,
            scene = prefConfig.audioVibrationScene
        )
        MoonBridge.setAudioHapticsSessionHandle(
            audioVibrationService?.nativeSessionHandle ?: 0L
        )
        MoonBridge.setAudioHapticsSceneMode(prefConfig.audioVibrationScene)
        updateAudioHapticsRuntimeEnabled(true)

        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(keyboardInputHandler.keyboardTranslator, null)

        touchInputHandler = TouchInputHandler(this)
        touchInputHandler.initTouchContexts(conn!!, streamView, prefConfig)

        cursorServiceManager = CursorServiceManager(
            streamView, cursorOverlayView, prefConfig, touchInputHandler.relativeTouchContextMap,
            object : CursorServiceManager.UiCallback {
                override fun runOnUi(runnable: Runnable) {
                    runOnUiThread(runnable)
                }
                override fun isActivityAlive(): Boolean {
                    return !isFinishing && !isDestroyed
                }
            }
        )

        streamView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                cursorServiceManager.syncCursorWithStream()
            }
        }
        streamView.post { cursorServiceManager.syncCursorWithStream() }

        if (prefConfig.onscreenController) {
            virtualController = VirtualController(
                controllerHandler,
                streamView.parent as FrameLayout,
                this
            )
            setupVirtualControllerGyro()
        }

        if (prefConfig.enableCrownFeatures) {
            initializeControllerManager()
        }

        if (prefConfig.usbDriver) {
            bindUsbDriverService()
        }

        if (decoderRenderer?.isAvcSupported() != true) {
            if (progressOverlay != null) {
                progressOverlay?.dismiss()
                progressOverlay = null
            }
            Dialog.displayDialog(
                this, resources.getString(R.string.conn_error_title),
                "This device or ROM doesn't support hardware accelerated H.264 playback.", true
            )
            return
        }

        streamView.holder.addCallback(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        DisplayPositionManager(this, prefConfig, streamView).setupDisplayPosition()

        setupExternalDisplay()

        floatBallHandler = FloatBallHandler(this, prefConfig)
        floatBallHandler.initialize()

        connectionCallbackHandler = ConnectionCallbackHandler(this)
    }

    private fun getOrCreateKeyboardUIController(): KeyboardUIController? {
        if (controllerManager != null) {
            val kUI = controllerManager?.keyboardUIController
            if (kUI != null) return kUI
        }
        if (standaloneKeyboardUI == null) {
            val keyboardContainer = findViewById<FrameLayout>(R.id.virtual_full_keyboard_container)
            if (keyboardContainer != null) {
                standaloneKeyboardUI = KeyboardUIController(keyboardContainer, createKeyboardEventListener(), this)
            }
        }
        return standaloneKeyboardUI
    }

    fun toggleVirtualKeyboard() {
        getOrCreateKeyboardUIController()?.toggle()
    }

    // region ---- Extracted helpers to reduce duplication ----

    /** Resolve the display currently used for rendering (external or built-in). */
    val currentTargetDisplay: Display
        get() = targetDisplayResolver.currentDisplay()

    /** Resolve the StreamView coordinate space for motion callbacks that don't pass a view. */
    private fun getMotionEventTargetView(): StreamView = activeStreamView ?: streamView

    /** Re-point all absolute/relative touch contexts at [view]. */
    private fun retargetTouchContexts(view: StreamView?) {
        for (i in 0 until TouchInputHandler.TOUCH_CONTEXT_LENGTH) {
            val absCtx = touchInputHandler.absoluteTouchContextMap[i]
            if (absCtx is AbsoluteTouchContext) absCtx.setTargetView(view!!)
            val relCtx = touchInputHandler.relativeTouchContextMap[i]
            if (relCtx is RelativeTouchContext) relCtx.setTargetView(view!!)
        }
    }

    /** Wire listeners on an arbitrary StreamView (main or external). */
    private fun setupStreamViewListeners(view: StreamView) {
        view.setOnGenericMotionListener(this)
        view.setOnKeyListener(this)
        view.setInputCallbacks(this)
        findViewById<View>(R.id.backgroundTouchView)?.setOnTouchListener(this)
        view.holder.addCallback(this)
    }

    /** Create the ExternalDisplayManager callback (shared by onCreate & prepareConnection). */
    private fun createExternalDisplayCallback(): ExternalDisplayManager.ExternalDisplayCallback {
        return object : ExternalDisplayManager.ExternalDisplayCallback {
            override fun onExternalDisplayConnected(display: Display) {
                LimeLog.info("External display connected, reinitializing input capture provider")
                inputCaptureProvider.disableCapture()
                inputCaptureProvider = InputCaptureManager.getInputCaptureProviderForExternalDisplay(this@Game, this@Game)
            }

            override fun onExternalDisplayDisconnected() {
                externalStreamView = null
                LimeLog.info("External display disconnected, cleared externalStreamView")
                retargetTouchContexts(this@Game.streamView)
                inputCaptureProvider.disableCapture()
                inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this@Game, this@Game)
            }

            override fun onStreamViewReady(streamView: StreamView) {
                externalStreamView = streamView
                retargetTouchContexts(streamView)
                setupStreamViewListeners(streamView)
                LimeLog.info("External display StreamView ready: ${streamView.width}x${streamView.height}")
            }
        }
    }

    /** Parse the server certificate from DER bytes in the launch intent. */
    private fun parseServerCert(): X509Certificate? {
        val derCertData = intent.getByteArrayExtra(EXTRA_SERVER_CERT) ?: return null
        return try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(derCertData)) as X509Certificate
        } catch (e: CertificateException) {
            e.printStackTrace()
            null
        }
    }

    /** Show the fullscreen connecting progress overlay. */
    private fun showProgressOverlay() {
        progressOverlay = FullscreenProgressOverlay(this, app)
        val computer = ComputerDetails()
        computer.name = pcName
        computer.uuid = intent.getStringExtra(EXTRA_PC_UUID)
        progressOverlay?.computer = computer
        progressOverlay?.show(
            resources.getString(R.string.conn_establishing_title),
            resources.getString(R.string.conn_establishing_msg)
        )
    }

    /** Shared keyboard-event listener used by KeyboardUIController instances. */
    private fun createKeyboardEventListener(): KeyboardUIController.OnKeyboardEventListener {
        return object : KeyboardUIController.OnKeyboardEventListener {
            override fun sendKeyEvent(down: Boolean, keyCode: Short) {
                if (controllerManager?.elementController != null) {
                    controllerManager?.elementController?.sendKeyEvent(down, keyCode)
                } else {
                    keyboardEvent(down, keyCode)
                }
            }
            override fun rumbleSingleVibrator(lowFreq: Short, highFreq: Short, duration: Int) {
                controllerManager?.elementController?.rumbleSingleVibrator(lowFreq, highFreq, duration)
            }
        }
    }

    /** Set up gyro callbacks on the virtual controller. */
    private fun setupVirtualControllerGyro() {
        val vc = virtualController ?: return
        vc.refreshLayout()
        vc.show()
        vc.setGyroEnabled(!prefConfig.gyroToMouse)
        controllerHandler.setVirtualControllerGyroCallbacks(
            { vc.setGyroEnabled(false) },
            { vc.setGyroEnabled(true) }
        )
    }

    /** Whether the resume-stream preference is enabled. */
    private val isResumeStreamEnabled: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("checkbox_resume_stream", false)

    private fun shouldUseFramegen(prefs: SharedPreferences = defaultPreferences()): Boolean =
        FramegenRuntimePlanner.shouldUse(prefs, prefConfig.width, prefConfig.height, prefConfig.fps)

    private fun framegenPresentationFps(prefs: SharedPreferences = defaultPreferences()): Int =
        FramegenRuntimePlanner.presentationFps(prefs, prefConfig.fps)

    private fun defaultPreferences(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(this)

    /**
     * Parse intent extras, build stream config, create NvConnection + ControllerHandler.
     * Shared by [onCreate] (first launch) and [prepareConnection] (resume reconnect).
     */
    private fun createConnectionAndHandler() {
        framegenEnabledToastShown = false
        if (::controllerHandler.isInitialized) {
            audioVibrationService?.controllerHandler = null
            controllerHandler.destroy()
        }
        val host = intent.getStringExtra(EXTRA_HOST) ?: ""
        val port = intent.getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT)
        val httpsPort = intent.getIntExtra(EXTRA_HTTPS_PORT, 0)
        val uniqueId = intent.getStringExtra(EXTRA_UNIQUEID) ?: ""
        val pairName = intent.getStringExtra(EXTRA_PAIR_NAME) ?: ""
        val pcUseVdd = if (intent.hasExtra(EXTRA_PC_USEVDD)) {
            intent.getBooleanExtra(EXTRA_PC_USEVDD, false)
        } else {
            null
        }
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val forceResumeCurrentSession = intent.getBooleanExtra(EXTRA_FORCE_RESUME_CURRENT_SESSION, false)
        val serverCert = parseServerCert()

        val streamConfigResult = buildStreamConfiguration(
            host, port, httpsPort, uniqueId, pairName, pcUseVdd, serverCert, displayName
        )
        val config = streamConfigResult.config

        conn = NvConnection(
            applicationContext,
            ComputerDetails.AddressTuple(host, port),
            httpsPort, uniqueId, pairName, config,
            PlatformBinding.getCryptoProvider(this), serverCert, displayName, forceResumeCurrentSession
        )
        orientationManager.connection = conn
        controllerHandler = ControllerHandler(this, conn!!, this, prefConfig)
    }

    /** Create or re-create ExternalDisplayManager with the standard callback. */
    private fun setupExternalDisplay() {
        val manager = ExternalDisplayManager(
            this,
            prefConfig,
            targetDisplayResolver
        )
        externalDisplayManager = manager
        manager.callback = createExternalDisplayCallback()
        manager.initialize(initialDisplayMode = selectedDisplayMode)
    }

    /** Bind or re-bind the USB driver service with the current controllerHandler. */
    private fun bindUsbDriverService() {
        usbDriverServiceManager?.stopAndUnbind()
        usbDriverServiceManager = UsbDriverServiceManager(this, this)
        usbDriverServiceManager?.controllerHandler = controllerHandler
        usbDriverServiceManager?.bind()
    }

    // endregion

    private fun buildStreamConfiguration(
        host: String?, port: Int, httpsPort: Int,
        uniqueId: String?, pairName: String?,
        pcUseVdd: Boolean?, serverCert: X509Certificate?,
        displayName: String?
    ): StreamConfigResult {
        val glPrefs = GlPreferences.readPreferences(this)
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        var willStreamHdr = false
        if (prefConfig.enableHdr) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val hdrCaps = currentTargetDisplay.hdrCapabilities

                if (hdrCaps != null) {
                    for (hdrType in hdrCaps.supportedHdrTypes) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            willStreamHdr = true
                            break
                        }
                    }
                }
                if (!willStreamHdr) {
                    Toast.makeText(this, "Display does not support HDR10", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "HDR requires Android 7.0 or later", Toast.LENGTH_LONG).show()
            }
        }

        if (decoderRenderer == null) {
            decoderRenderer = MediaCodecDecoderRenderer(
                this, prefConfig,
                {
                    tombstonePrefs.edit(commit = true) {
                        putInt(
                            "CrashCount",
                            tombstonePrefs.getInt("CrashCount", 0) + 1
                        )
                    }
                    reportedCrash = true
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered,
                willStreamHdr,
                glPrefs.glRenderer,
                this
            )
            // 首帧解码到达时立刻隐藏 loading overlay，无缝切到真实画面
            decoderRenderer?.firstFrameCallback = {
                runOnUiThread {
                    // OnFrameRenderedListener 触发时 SurfaceFlinger 还要等几个 vsync 才把帧真正合成上屏，
                    // 此时立即 GONE overlay 仍会暴露空缝；延迟 ~3 帧让 SurfaceView 上的视频稳定再隐藏。
                    streamView.postDelayed({
                        progressOverlay?.dismiss()
                        progressOverlay = null
                    }, 48)
                }
            }
        }

        if (willStreamHdr && decoderRenderer?.isHevcMain10Supported() != true && decoderRenderer?.isAv1Main10Supported() != true) {
            willStreamHdr = false
            Toast.makeText(this, "Decoder does not support HDR10 profile", Toast.LENGTH_LONG).show()
        }

        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC && decoderRenderer?.isHevcSupported() != true) {
            Toast.makeText(this, "No HEVC decoder found", Toast.LENGTH_LONG).show()
        }
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1 && decoderRenderer?.isAv1Supported() != true) {
            Toast.makeText(this, "No AV1 decoder found", Toast.LENGTH_LONG).show()
        }

        var supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264
        if (decoderRenderer?.isHevcSupported() == true) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265
            if (willStreamHdr && decoderRenderer?.isHevcMain10Supported() == true) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            }
        }
        if (decoderRenderer?.isAv1Supported() == true) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (willStreamHdr && decoderRenderer?.isAv1Main10Supported() == true) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            }
        }

        var gamepadMask = ControllerHandler.getAttachedControllerMask(this).toInt()
        if (!prefConfig.multiController) {
            gamepadMask = 1
        }
        if (prefConfig.onscreenController) {
            gamepadMask = gamepadMask or 1
        }

        val displayRefreshRate = prepareDisplayForRendering()
        LimeLog.info("Display refresh rate: $displayRefreshRate Hz")

        performanceOverlayManager?.setActualDisplayRefreshRate(displayRefreshRate)

        val clientRefreshRateX100 = (displayRefreshRate * 100).roundToInt()

        val roundedRefreshRate = displayRefreshRate.roundToInt()
        var chosenFrameRate = prefConfig.fps
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + 3) {
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                    LimeLog.info("Using drop mode for FPS > Hz")
                } else if (roundedRefreshRate <= 49) {
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                    LimeLog.info("Bogus refresh rate: $roundedRefreshRate")
                } else {
                    chosenFrameRate = roundedRefreshRate - 1
                    LimeLog.info("Adjusting FPS target for screen to $chosenFrameRate")
                }
            }
        }

        framegenInputHdrEnabled = willStreamHdr && prefConfig.hdrMode != MoonBridge.HDR_MODE_SDR

        val config = StreamConfiguration.Builder()
            .setResolution(prefConfig.width, prefConfig.height)
            .setLaunchRefreshRate(prefConfig.fps)
            .setRefreshRate(chosenFrameRate)
            .setApp(app)
            .setBitrate(prefConfig.bitrate)
            .setResolutionScale(prefConfig.resolutionScale)
            .setEnableSops(prefConfig.enableSops)
            .enableLocalAudioPlayback(prefConfig.playHostAudio)
            .setMaxPacketSize(1392)
            .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
            .setSupportedVideoFormats(supportedVideoFormats)
            .setAttachedGamepadMask(gamepadMask)
            .setClientRefreshRateX100(clientRefreshRateX100)
            .setAudioConfiguration(prefConfig.audioConfiguration)
            // When the user has disabled audio passthrough, force-negotiate Opus
            // so the host sends a decoded stream that AndroidAudioRenderer can play
            // — bypasses both PcmPassthroughRenderer and Ac3PassthroughRenderer.
            .setAudioCodec(if (prefConfig.enableAudioPassthrough) prefConfig.audioCodec else MoonBridge.AUDIO_CODEC_OPUS)
            .setAudioBitrate(prefConfig.audioCodecBitrate)
            .setColorSpace(decoderRenderer?.getPreferredColorSpace() ?: 0)
            .setColorRange(
                if (willStreamHdr && prefConfig.hdrMode == MoonBridge.HDR_MODE_HLG)
                    MoonBridge.COLOR_RANGE_FULL
                else decoderRenderer?.getPreferredColorRange() ?: 0
            )
            .setHdrMode(if (willStreamHdr) prefConfig.hdrMode else MoonBridge.HDR_MODE_SDR)
            .setHdrBrightnessOverride(
                willStreamHdr && prefConfig.hdrBrightnessOverride,
                prefConfig.hdrPeakBrightnessNits
            )
            .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
            .setUseVdd(pcUseVdd)
            .setEnableMic(prefConfig.enableMic)
            .setControlOnly(prefConfig.controlOnly)
            .setCustomScreenMode(prefConfig.screenCombinationMode)
            .build()

        LimeLog.info("Stream config: hdr=$willStreamHdr hdrMode=${prefConfig.hdrMode} fullRange=${prefConfig.fullRange}")

        return StreamConfigResult(config, displayRefreshRate, clientRefreshRateX100)
    }

    private class StreamConfigResult(
        val config: StreamConfiguration,
        val displayRefreshRate: Float,
        val clientRefreshRateX100: Int
    )

    private fun prepareConnection() {
        cursorServiceManager.destroyLocalCursorRenderers()
        runOnUiThread {
            val cursorOverlay = findViewById<CursorView>(R.id.cursorOverlay)
            cursorOverlay?.resetToDefault()
            cursorOverlay?.hide()
            notificationOverlayManager.reset()
        }

        orientationManager.reset()

        if (decoderRenderer != null) {
            try {
                decoderRenderer?.prepareForStop()
            } catch (_: Exception) {}
            decoderRenderer = null
        }

        // The Game activity may survive while the stream is stopped. Refresh the
        // controller motion settings before rebuilding the handler so a resumed
        // session doesn't keep the values captured by onCreate().
        prefConfig.refreshControllerMotionPreferencesFrom(
            PreferenceConfiguration.readPreferences(this, currentTargetDisplay)
        )

        createConnectionAndHandler()

        audioVibrationService?.controllerHandler = controllerHandler

        if (prefConfig.usbDriver) {
            bindUsbDriverService()
        } else {
            usbDriverServiceManager?.refreshListener()
        }

        touchInputHandler.initTouchContexts(conn!!, streamView, prefConfig)

        if (virtualController != null && prefConfig.onscreenController) {
            setupVirtualControllerGyro()
        }

        if (controllerManager != null) {
            if (prefConfig.enableCrownFeatures) {
                controllerManager?.refreshLayout()
            } else {
                controllerManager = null
            }
        }

        if (microphoneManager != null) {
            microphoneManager?.stopMicrophoneStream()
        }
        microphoneManager = MicrophoneManager(this, conn!!, prefConfig.enableMic)
        microphoneManager?.setStateListener(object : MicrophoneManager.MicrophoneStateListener {
            override fun onMicrophoneStateChanged(isActive: Boolean) {
                LimeLog.info("麦克风状态改变: ${if (isActive) "激活" else "暂停"}")
            }
            override fun onPermissionRequested() {
                LimeLog.info("麦克风权限请求已发送")
            }
        })

        setupExternalDisplay()
    }

    override fun onResume() {
        super.onResume()
        if (audioVibrationService != null) {
            updateAudioHapticsRuntimeEnabled(true)
        }
        KeyboardAccessibilityService.setIntercepting(true)
        val service = KeyboardAccessibilityService.instance
        if (service != null) {
            service.keyEventCallback = this
        } else {
            LimeLog.warning("KeyboardAccessibilityService is not running.")
        }

        if (microphoneManager != null && micButton != null) {
            microphoneManager?.updateMicrophoneButtonState()
        }
        if (::floatBallHandler.isInitialized) {
            floatBallHandler.show()
        }
    }

    override fun onKeyEvent(event: KeyEvent) {
        isEventFromAccessibilityService = true
        if (event.action == KeyEvent.ACTION_DOWN) {
            onKeyDown(event.keyCode, event)
        } else if (event.action == KeyEvent.ACTION_UP) {
            onKeyUp(event.keyCode, event)
        }
        isEventFromAccessibilityService = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        microphoneManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == KEEP_ALIVE_NOTIFICATION_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                StreamNotificationService.start(this, pcName, appName)
            } else {
                Toast.makeText(this, getString(R.string.toast_no_notification_permission), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        orientationManager.onConfigurationChanged()

        virtualController?.refreshLayout()
        controllerManager?.refreshLayout()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode) {
                virtualController?.hide()
                performanceOverlayManager?.hideOverlayImmediate()
                jitterMonitorManager?.hideImmediate()
                notificationOverlayManager.setHiding(true)
                microphoneManager?.setEnableMic(false)
                controllerHandler.disableSensors()
                UiHelper.notifyStreamEnteringPiP(this)
            } else {
                virtualController?.show()
                performanceOverlayManager?.applyRequestedVisibility()
                jitterMonitorManager?.applyVisibility()
                notificationOverlayManager.setHiding(false)
                notificationOverlayManager.applyVisibility()
                microphoneManager?.setEnableMic(prefConfig.enableMic)
                controllerHandler.enableSensors()
                controllerHandler.onSensorsReenabled()
                UiHelper.notifyStreamExitingPiP(this)
            }
        }

        performanceOverlayManager?.onConfigurationChanged()
        // PiP 下不重显浮层：进入 PiP 分支已 hideImmediate()，此处仅在非 PiP 时按偏好显隐
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode) {
            jitterMonitorManager?.applyVisibility()
        }
        refreshDisplayPosition()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(prefConfig.width, prefConfig.height))
            .setSourceRectHint(
                Rect(
                    streamView.left, streamView.top,
                    streamView.right, streamView.bottom
                )
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appName != null) {
                builder.setTitle(appName)
                if (pcName != null) {
                    builder.setSubtitle(pcName)
                }
            } else if (pcName != null) {
                builder.setTitle(pcName)
            }
        }

        return builder.build()
    }

    fun updatePipAutoEnter() {
        if (!prefConfig.enablePip) return

        val autoEnter = connected && suppressPipRefCount == 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter))
        } else {
            autoEnterPip = autoEnter
        }
    }

    fun setMetaKeyCaptureState(enabled: Boolean) {
        try {
            val semWindowManager = Class.forName("com.samsung.android.view.SemWindowManager")
            val getInstanceMethod = semWindowManager.getMethod("getInstance")
            val manager = getInstanceMethod.invoke(null)

            if (manager != null) {
                val parameterTypes = arrayOf<Class<*>>(ComponentName::class.java, Boolean::class.javaPrimitiveType!!)
                val requestMetaKeyEventMethod = semWindowManager.getDeclaredMethod("requestMetaKeyEvent", *parameterTypes)
                requestMetaKeyEventMethod.invoke(manager, this.componentName, enabled)
            } else {
                LimeLog.warning("SemWindowManager.getInstance() returned null")
            }
        } catch (_: ClassNotFoundException) {
        } catch (_: NoSuchMethodException) {
        } catch (_: InvocationTargetException) {
        } catch (_: IllegalAccessException) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (isResumeStreamEnabled) {
            if (!autoEnterPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                shouldResumeSession = true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                shouldResumeSession = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (autoEnterPip) {
                try {
                    enterPictureInPictureMode(getPictureInPictureParams(false))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean {
        if (autoEnterPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPictureInPictureMode(getPictureInPictureParams(false))
        }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        keyboardInputHandler.clearModifierState()
        inputCaptureProvider.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Android only delivers OnPrimaryClipChangedListener events while
            // the app holds input focus; clips copied in another app while
            // Game was paused are silently dropped. Re-poll on focus regain.
            clipboardSyncManager?.onFocusGained()
        }
    }

    private fun prepareDisplayForRendering(): Float {
        val display = currentTargetDisplay

        val presentationFps = framegenPresentationFps()
        val displayConfig = if (presentationFps != prefConfig.fps) {
            prefConfig.copy().apply {
                fps = presentationFps
            }
        } else {
            prefConfig
        }
        if (displayConfig.fps != prefConfig.fps) {
            LimeLog.info("Framegen display target FPS: ${prefConfig.fps} -> ${displayConfig.fps}")
        }

        val selection = DisplayModeManager.selectBestDisplayMode(display, displayConfig)
        selectedDisplayMode = selection

        if (!targetDisplayResolver.isExternalDisplaySelected()) {
            DisplayModeWindowApplier.apply(window, selection)
        } else {
            externalDisplayManager?.updateDisplayMode(selection)
        }

        updateStreamViewSize(prefConfig.width, prefConfig.height, selection.aspectRatioMatch)
        desiredRefreshRate = selection.refreshRate
        return selection.refreshRate
    }

    @SuppressLint("InlinedApi")
    private val hideSystemUiRunnable = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    fun hideSystemUi(delay: Int) {
        val h = window.decorView.handler
        if (h != null) {
            h.removeCallbacks(hideSystemUiRunnable)
            h.postDelayed(hideSystemUiRunnable, delay.toLong())
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decoderRenderer?.notifyVideoBackground()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decoderRenderer?.notifyVideoForeground()
        }
        hideSystemUi(50)
    }

    override fun onDestroy() {
        cancelKeepAliveNotification()
        if (::orientationManager.isInitialized) {
            orientationManager.cleanup()
        }
        if (::floatBallHandler.isInitialized) {
            floatBallHandler.release()
        }

        if (audioVibrationService != null) {
            updateAudioHapticsRuntimeEnabled(false)
            MoonBridge.setAudioHapticsSessionHandle(0L)
            audioVibrationService?.release()
            audioVibrationService = null
        }

        super.onDestroy()

        if (conn != null && connected) {
            connectionCallbackHandler.stopConnection()
        }

        if (::controllerHandler.isInitialized) {
            controllerHandler.destroy()
        }

        if (::keyboardInputHandler.isInitialized) {
            val inputManager = getSystemService(INPUT_SERVICE) as InputManager
            inputManager.unregisterInputDeviceListener(keyboardInputHandler.keyboardTranslator)
        }

        lowLatencyWifiLock?.release()
        highPerfWifiLock?.release()
        jitterMonitorManager?.destroy()
        jitterMonitorManager = null
        usbDriverServiceManager?.stopAndUnbind()
        if (::inputCaptureProvider.isInitialized) {
            inputCaptureProvider.destroy()
        }
        externalDisplayManager?.cleanup()
        microphoneManager?.stopMicrophoneStream()
        clipboardSyncManager?.stop()
        clipboardSyncManager = null
        framegenSurfaceGeneration.incrementAndGet()
        framegenSurfaceExecutor.shutdownNow()
    }

    override fun onPause() {
        updateAudioHapticsRuntimeEnabled(false)
        audioVibrationService?.stop()
        if (::floatBallHandler.isInitialized) {
            floatBallHandler.hide()
        }
        KeyboardAccessibilityService.setIntercepting(false)
        KeyboardAccessibilityService.instance?.keyEventCallback = null

        if (isFinishing) {
            if (::controllerHandler.isInitialized) {
                controllerHandler.stop()
            }
            if (::inputCaptureProvider.isInitialized) {
                setInputGrabState(false)
            }
        }
        super.onPause()
    }

    private fun updateAudioHapticsRuntimeEnabled(foreground: Boolean) {
        val featureEnabled = foreground && prefConfig.enableAudioVibration
        MoonBridge.setAudioHapticsOutputEnabled(featureEnabled)
    }

    /**
     * Applies Game Menu audio-haptics changes to the active stream.
     *
     * Android's system audio-coupled generator owns the device motor for the
     * lifetime of its AudioTrack. Reconfiguring it in place would leave the UI
     * and actual route out of sync, so those uncommon changes are persisted for
     * the next stream instead.
     */
    internal fun applyAudioHapticsSettings(settings: AudioHapticsSettings): Boolean {
        val service = audioVibrationService ?: return false
        val canApply = AudioHapticsRuntimePolicy.canApplyImmediately(
            systemAudioCoupledActive = service.systemAudioCoupledDeviceActive,
            applied = currentAudioHapticsSettings(),
            desired = settings
        )
        if (!canApply) return false

        service.setSettings(
            settings.enabled,
            settings.strength,
            settings.mode,
            settings.scene
        )
        appliedAudioHapticsSettings = settings
        MoonBridge.setAudioHapticsSceneMode(settings.scene)
        updateAudioHapticsRuntimeEnabled(true)
        return true
    }

    internal fun currentAudioHapticsSettings(): AudioHapticsSettings {
        return appliedAudioHapticsSettings ?: AudioHapticsSettings(
            enabled = prefConfig.enableAudioVibration,
            strength = prefConfig.audioVibrationStrength,
            mode = prefConfig.audioVibrationMode,
            scene = prefConfig.audioVibrationScene
        )
    }

    internal fun applyAudioHapticsStrength(strength: Int): Boolean {
        val service = audioVibrationService ?: return false
        if (service.systemAudioCoupledDeviceActive) return false

        val bounded = strength.coerceIn(0, AudioVibrationService.MAX_STRENGTH)
        val settings = currentAudioHapticsSettings().copy(strength = bounded)
        service.setSettings(
            settings.enabled,
            settings.strength,
            settings.mode,
            settings.scene
        )
        appliedAudioHapticsSettings = settings
        return true
    }

    fun changeResolution() {
        isChangingResolution = true
        this.recreate()
    }

    override fun onStop() {
        super.onStop()

        if ((isExtremeResumeEnabled || isChangingResolution) && !isFinishing) {
            LimeLog.info("Extreme Resume: onStop intercepted.")
            if (!isChangingResolution && isResumeStreamEnabled) {
                showKeepAliveNotification()
            }
            return
        }

        if (isStreamingActive && lastActiveTime > 0) {
            accumulatedStreamTime += System.currentTimeMillis() - lastActiveTime
            isStreamingActive = false
            LimeLog.info("串流时长计时暂停，已累计: ${accumulatedStreamTime / 1000} 秒")
        }

        if (!shouldResumeSession && !isFinishing) {
            if (isResumeStreamEnabled) {
                shouldResumeSession = true
                LimeLog.info("检测到应用进入后台（非主动退出），已标记为待恢复会话")
            }
        }

        if (progressOverlay != null) {
            progressOverlay?.dismiss()
            progressOverlay = null
        }
        Dialog.closeDialogs()
        activeGameMenu?.dismiss()
        activeGameMenu = null

        if (virtualController != null) {
            virtualController?.hide()
            virtualController?.cleanup()
        }

        val decoderMessage = getDecoderFormatLabel()

        if (conn != null) {
            displayedFailureDialog = true
            connectionCallbackHandler.stopConnection()
            showLatencyToast(decoderMessage)

            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit {
                    putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                }
            }
        }

        reportStreamAnalytics(decoderMessage)

        if (shouldResumeSession && isResumeStreamEnabled) {
            showKeepAliveNotification()
            LimeLog.info("应用进入后台，保持 Activity 存活以备快速恢复。连接已断开。")
        } else {
            finish()
        }
    }

    private fun getDecoderFormatLabel(): String {
        if (decoderRenderer == null) return "UNKNOWN"
        val videoFormat = (decoderRenderer?.getActiveVideoFormat() ?: 0)
        var label = when {
            (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0 -> "H.264"
            (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0 -> "HEVC"
            (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0 -> "AV1"
            else -> "UNKNOWN"
        }
        if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
            label += " HDR"
        }
        return label
    }

    private fun showLatencyToast(decoderMessage: String) {
        if (!prefConfig.enableLatencyToast) return
        val averageEndToEndLat = (decoderRenderer?.getAverageEndToEndLatency() ?: 0)
        val averageDecoderLat = (decoderRenderer?.getAverageDecoderLatency() ?: 0)
        var message: String? = null
        if (averageEndToEndLat > 0) {
            message = resources.getString(R.string.conn_client_latency) + " " + averageEndToEndLat + " ms"
            if (averageDecoderLat > 0) {
                message += " (" + resources.getString(R.string.conn_client_latency_hw) + " " + averageDecoderLat + " ms)"
            }
        } else if (averageDecoderLat > 0) {
            message = resources.getString(R.string.conn_hardware_latency) + " " + averageDecoderLat + " ms"
        }

        if (message != null) {
            message += " [$decoderMessage]"
        }

        if (prefConfig.enableMic && microphoneManager != null) {
            val micStats = AudioDiagnostics.getCurrentStats(this)
            message = if (message != null) "$message [mic]$micStats" else micStats
        }

        val surfaceFlingerStats = decoderRenderer?.getSurfaceFlingerStats() ?: ""
        if (surfaceFlingerStats.isNotEmpty()) {
            message = if (message != null) "$message\n$surfaceFlingerStats" else surfaceFlingerStats
        }

        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun reportStreamAnalytics(decoderMessage: String) {
        if (analyticsManager == null || pcName == null || streamStartTime <= 0) return

        var effectiveStreamDuration = accumulatedStreamTime
        if (isStreamingActive && lastActiveTime > 0) {
            effectiveStreamDuration += System.currentTimeMillis() - lastActiveTime
        }
        val totalElapsedTime = System.currentTimeMillis() - streamStartTime

        var resolutionWidth = 0
        var resolutionHeight = 0
        var averageEndToEndLatency = 0
        var averageDecoderLatency = 0
        if (decoderRenderer != null) {
            resolutionWidth = prefConfig.width
            resolutionHeight = prefConfig.height
            averageEndToEndLatency = (decoderRenderer?.getAverageEndToEndLatency() ?: 0)
            averageDecoderLatency = (decoderRenderer?.getAverageDecoderLatency() ?: 0)
        }

        analyticsManager?.logGameStreamEnd(
            pcName ?: "", appName, effectiveStreamDuration,
            decoderMessage, resolutionWidth, resolutionHeight,
            averageEndToEndLatency, averageDecoderLatency
        )
        LimeLog.info("串流统计 - 有效时长: ${effectiveStreamDuration / 1000}秒, 总耗时: ${totalElapsedTime / 1000}秒")
        streamStartTime = 0
        accumulatedStreamTime = 0
        isStreamingActive = false
    }

    fun setInputGrabState(grab: Boolean) {
        if (grab) {
            inputCaptureProvider.enableCapture()
            if (cursorVisible) {
                inputCaptureProvider.showCursor()
            }
        } else {
            if (::touchInputHandler.isInitialized) {
                touchInputHandler.cancelNonRootTouchpad()
            }
            inputCaptureProvider.disableCapture()
        }
        setMetaKeyCaptureState(grab)
        grabbedInput = grab
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return keyboardInputHandler.handleKeyDown(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return keyboardInputHandler.handleKeyUp(event) || super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return keyboardInputHandler.handleKeyMultiple(event) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun handleKeyDown(event: KeyEvent): Boolean {
        return keyboardInputHandler.handleKeyDown(event)
    }

    override fun handleKeyUp(event: KeyEvent): Boolean {
        return keyboardInputHandler.handleKeyUp(event)
    }

    val relativeTouchContextMap: Array<TouchContext?>
        get() = touchInputHandler.relativeTouchContextMap

    fun setTouchMode(enableRelativeTouch: Boolean) {
        touchInputHandler.setTouchMode(enableRelativeTouch)
    }

    fun setEnhancedTouch(enableRelativeTouch: Boolean) {
        touchInputHandler.setEnhancedTouch(enableRelativeTouch)
    }

    override fun toggleKeyboard() {
        LimeLog.info("Toggling keyboard overlay")
        streamView.clearFocus()
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.toggleSoftInput(0, 0)
    }

    fun enableNativeMousePointer(enable: Boolean) {
        LimeLog.info("Setting native mouse pointer: $enable")
        prefConfig.enableNativeMousePointer = enable

        if (enable) {
            if (::touchInputHandler.isInitialized) {
                touchInputHandler.cancelNonRootTouchpad()
            }
            inputCaptureProvider.disableCapture()
            cursorVisible = true
            inputCaptureProvider.showCursor()
            setMetaKeyCaptureState(true)
            cursorServiceManager.refreshLocalCursorState(true)
            val cursorOverlay = findViewById<CursorView>(R.id.cursorOverlay)
            cursorOverlay?.hide()
        } else {
            cursorVisible = false
            inputCaptureProvider.hideCursor()
            setInputGrabState(true)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return touchInputHandler.handleMotionEvent(getMotionEventTargetView(), event) || super.onGenericMotionEvent(event)
    }

    override fun onGenericMotion(view: View, event: MotionEvent): Boolean {
        return touchInputHandler.handleMotionEvent(view, event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!prefConfig.syncTouchEventWithDisplay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.requestUnbufferedDispatch(event)
            }
        }
        return touchInputHandler.handleMotionEvent(view, event)
    }

    override fun stageStarting(stage: String) {
        connectionCallbackHandler.stageStarting(stage)
    }

    override fun stageComplete(stage: String) {
        connectionCallbackHandler.stageComplete(stage)
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        connectionCallbackHandler.stageFailed(stage, portFlags, errorCode)
    }

    override fun connectionTerminated(errorCode: Int) {
        connectionCallbackHandler.connectionTerminated(errorCode)
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {
        connectionCallbackHandler.connectionStatusUpdate(connectionStatus)
    }

    override fun connectionStarted() {
        connectionCallbackHandler.connectionStarted()
        controllerHandler.retryPendingControllerArrivals()
        startClipboardSyncIfEnabled()
    }

    private fun startClipboardSyncIfEnabled() {
        if (clipboardSyncManager != null) return
        // moonlight-common-c PR #5 does not advertise a feature flag; we rely on the
        // user opting in. If the host doesn't speak 0x5508 the native send returns
        // -2 and inbound packets simply never arrive — no observable harm.
        val wantText = prefConfig.enableClipboardSyncText
        val wantImage = prefConfig.enableClipboardSyncImage
        if (!wantText && !wantImage) return
        val mgr = ClipboardSyncManager(
            context = applicationContext,
            syncText = wantText,
            syncImage = wantImage,
            fileProviderAuthority = "$packageName.clipboard_fileprovider",
            // Lazily resolve NvHTTP each time so the manager outlives transient
            // disconnects without us caching a stale instance. conn is set
            // before connectionStarted fires, so this is safe at construction.
            nvHttpProvider = { conn?.createNvHttp() },
        )
        runCatching { mgr.start() }
            .onFailure { LimeLog.warning("Clipboard sync start failed: ${it.message}") }
            .onSuccess { clipboardSyncManager = mgr }
    }

    /** 启动智能码率（如设置已开启）。在连接建立后调用。*/
    fun startAdaptiveBitrateIfEnabled() {
        if (!prefConfig.enableAdaptiveBitrate) return
        if (adaptiveBitrateService != null) return
        val c = conn ?: return
        val service = AdaptiveBitrateService(
            nvHttpFactory = { c.createNvHttp() },
            statsProvider = {
                latestPerfInfo?.let { p ->
                    AdaptiveBitrateService.AbrStats(
                        packetLoss = p.lostFrameRate,
                        rttMs = (p.rttInfo shr 32).toInt(),
                        decodeFps = p.totalFps,
                        droppedFrames = 0
                    )
                }
            },
            onBitrateChanged = { kbps, _ ->
                // service 已成功通知服务端，仅同步本地配置
                c.applyBitrateLocally(kbps)
            }
        )
        service.start(prefConfig.bitrate, prefConfig.abrMode)
        adaptiveBitrateService = service
    }

    /** 停止智能码率，恢复初始码率。*/
    fun stopAdaptiveBitrate() {
        adaptiveBitrateService?.stop()
        adaptiveBitrateService = null
    }

    override fun onStart() {
        super.onStart()

        if (!isStreamingActive && streamStartTime > 0) {
            lastActiveTime = System.currentTimeMillis()
            isStreamingActive = true
            LimeLog.info("串流时长计时恢复，之前累计: ${accumulatedStreamTime / 1000} 秒")
        }

        if (isExtremeResumeEnabled && connected) {
            LimeLog.info("Extreme Resume: Returning to foreground with active connection.")
            if (progressOverlay != null) {
                progressOverlay?.dismiss()
                progressOverlay = null
            }
            hideSystemUi(500)
            return
        }

        if (shouldResumeSession) {
            LimeLog.info("从后台恢复，正在快速重连...")
            Dialog.closeDialogs()
            shouldResumeSession = false
            displayedFailureDialog = false

            showProgressOverlay()

            try {
                prepareConnection()
            } catch (e: Exception) {
                LimeLog.severe("Failed to prepare connection: ${e.message}")
                finish()
                return
            }

            attemptedConnection = false
            connecting = false
            connected = false
            orientationManager.connected = false

            streamView.requestLayout()
            streamView.invalidate()
        }
    }

    override fun displayMessage(message: String) {
        runOnUiThread { Toast.makeText(this@Game, message, Toast.LENGTH_LONG).show() }
    }

    override fun displayTransientMessage(message: String) {
        if (!prefConfig.disableWarnings) displayMessage(message)
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        LimeLog.info(String.format(null as Locale?, "Rumble on gamepad %d: %04x %04x", controllerNumber, lowFreqMotor, highFreqMotor))
        controllerManager?.elementController?.gameVibrator(lowFreqMotor, highFreqMotor)
        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor)
    }

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        LimeLog.info(String.format(null as Locale?, "Rumble on gamepad triggers %d: %04x %04x", controllerNumber, leftTrigger, rightTrigger))
        controllerHandler.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        LimeLog.info("Display HDR mode: ${if (enabled) "enabled" else "disabled"}")
        framegenInputHdrEnabled = enabled
        val framegenHdrMode = if (enabled) prefConfig.hdrMode else MoonBridge.HDR_MODE_SDR
        FramegenInterceptor.configureHdrMode(framegenHdrMode, shouldUseFullRangeHdr(framegenHdrMode))
        decoderRenderer?.setHdrMode(enabled, hdrMetadata)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notifySystemHdrStatus(enabled)
        }
    }

    private fun notifySystemHdrStatus(hdrEnabled: Boolean) {
        runOnUiThread {
            try {
                val framegenSdrOutput = willFramegenOutputSdr()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.colorMode =
                        if (hdrEnabled && !framegenSdrOutput) {
                            ActivityInfo.COLOR_MODE_HDR
                        } else {
                            ActivityInfo.COLOR_MODE_DEFAULT
                        }
                }

                val params = window.attributes
                if (hdrEnabled && !framegenSdrOutput) {
                    if (prefConfig.enableHdrHighBrightness) {
                        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    }
                } else {
                    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = params

                LimeLog.info(
                    "ColorOS HDR notification: hdr=${if (hdrEnabled) "enabled" else "disabled"} " +
                        "framegenSdr=$framegenSdrOutput"
                )
            } catch (e: Exception) {
                LimeLog.warning("Failed to notify ColorOS system HDR status: ${e.message}")
            }
        }
    }

    private fun willFramegenOutputSdr(): Boolean =
        (framegenCapture != null || shouldUseFramegen()) && !framegenInputHdrEnabled

    private fun shouldUseFullRangeHdr(hdrMode: Int): Boolean {
        if (hdrMode == MoonBridge.HDR_MODE_SDR) {
            return false
        }
        if (hdrMode == MoonBridge.HDR_MODE_HLG) {
            return true
        }

        val preferredRange = decoderRenderer?.getPreferredColorRange()
            ?: if (prefConfig.fullRange) MoonBridge.COLOR_RANGE_FULL else MoonBridge.COLOR_RANGE_LIMITED
        return preferredRange == MoonBridge.COLOR_RANGE_FULL
    }

    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        controllerHandler.handleSetMotionEventState(controllerNumber, motionType, reportRateHz)
    }

    override fun onResolutionChanged(width: Int, height: Int) {
        val alignedWidth = width and 1.inv()
        val alignedHeight = height and 1.inv()

        val baseWidth: Int
        val baseHeight: Int

        if (prefConfig.resolutionScale != 100) {
            baseWidth = (alignedWidth * 100 / prefConfig.resolutionScale) and 1.inv()
            baseHeight = (alignedHeight * 100 / prefConfig.resolutionScale) and 1.inv()
            LimeLog.info("Resolution scale conversion: actual=${alignedWidth}x${alignedHeight}, base=${baseWidth}x${baseHeight}, scale=${prefConfig.resolutionScale}%")
        } else {
            baseWidth = alignedWidth
            baseHeight = alignedHeight
        }

        orientationManager.syncOrientationOnFirstFrame(baseWidth, baseHeight)

        if (prefConfig.width == baseWidth && prefConfig.height == baseHeight) {
            return
        }

        LimeLog.info("Resolution changed: ${prefConfig.width}x${prefConfig.height} -> ${baseWidth}x${baseHeight}")

        prefConfig.width = baseWidth
        prefConfig.height = baseHeight

        if (connected && decoderRenderer != null) {
            decoderRenderer?.onResolutionChanged(baseWidth, baseHeight)
        }

        val isLandscape = baseWidth > baseHeight
        runOnUiThread {
            Toast.makeText(this, getString(R.string.host_resolution_changed, baseWidth, baseHeight), Toast.LENGTH_SHORT).show()
            orientationManager.onServerResolutionChanged(isLandscape)
            updateStreamViewSize(baseWidth, baseHeight)
        }
    }

    private fun updateStreamViewSize(width: Int, height: Int, forceFixedSize: Boolean) {
        val screenSize = Point()
        currentTargetDisplay.getRealSize(screenSize)

        val exceedsScreenSize = width > screenSize.x || height > screenSize.y
        val useFixedSize = prefConfig.stretchVideo || (forceFixedSize && !exceedsScreenSize)

        if (useFixedSize) {
            streamView.setDesiredAspectRatio(0.0)
            streamView.holder.setFixedSize(width, height)
            LimeLog.info("Set fixed surface size: ${width}x${height} (screen: ${screenSize.x}x${screenSize.y})")
        } else {
            if (exceedsScreenSize) {
                LimeLog.info("Host resolution ${width}x${height} exceeds screen size ${screenSize.x}x${screenSize.y}, using aspect ratio scaling")
            }
            streamView.holder.setSizeFromLayout()
            streamView.setDesiredAspectRatio(width.toDouble() / height)
            streamView.requestLayout()
        }
    }

    private fun updateStreamViewSize(width: Int, height: Int) {
        updateStreamViewSize(width, height, false)
    }

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        controllerHandler.handleSetControllerLED(controllerNumber, r, g, b)
    }

    private fun prepareFramegenSurface(outputSurface: Surface, showEnabledToast: Boolean) {
        releaseFramegenCapture()

        val config = FramegenRuntimePlanner.configForStream(
            defaultPreferences(),
            prefConfig.width,
            prefConfig.height,
            prefConfig.fps,
            framegenInputHdrEnabled,
            prefConfig.hdrMode,
            shouldUseFullRangeHdr(prefConfig.hdrMode)
        ) ?: return
        applyFramegenConfig(config)
        decoderRenderer?.setFramegenCaptureSwitchReady(false)

        val capture = FramegenCapture.create(prefConfig.width, prefConfig.height)
        if (capture == null) {
            configureFramegenOutputSurface(null)
            LimeLog.warning("Framegen capture unavailable; using direct decoder output")
            return
        }

        framegenCapture = capture
        decoderRenderer?.framegenSurface = capture.surface
        prewarmFramegen(
            outputSurface,
            framegenSurfaceGeneration.incrementAndGet()
        )

        LimeLog.info(
            "Framegen capture armed ${prefConfig.width}x${prefConfig.height} " +
                "fps=${config.presentationFps} hdrIn=${config.inputHdrEnabled}"
        )
        if (showEnabledToast && !framegenEnabledToastShown) {
            framegenEnabledToastShown = true
            runOnUiThread {
                Toast.makeText(this, R.string.toast_framegen_stream_enabled, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyFramegenConfig(config: FramegenRuntimeConfig) {
        FramegenInterceptor.configureLosslessDllPath(config.losslessDllPath)
        FramegenInterceptor.configureHdrMode(config.inputHdrMode, config.inputHdrFullRange)
        FramegenInterceptor.configureOutputFrameRate(config.presentationFps)
        FramegenInterceptor.configureTuning(
            config.internalWidth,
            config.presentMode,
            config.slowFrameThresholdMs,
            config.presentQueueMax,
            config.allowAdaptiveWithoutDoubling
        )
        framegenAdaptiveController.configure(
            FramegenAdaptiveController.Config(
                inputFps = config.inputFps,
                presentationFps = config.presentationFps,
                adaptiveEnabled = config.adaptiveEnabled,
                allowAdaptiveWithoutDoubling = config.allowAdaptiveWithoutDoubling,
                internalWidth = config.internalWidth,
                presentMode = config.presentMode,
                slowFrameThresholdMs = config.slowFrameThresholdMs,
                presentQueueMax = config.presentQueueMax
            )
        )
    }

    private fun prewarmFramegen(
        outputSurface: Surface,
        generation: Int
    ) {
        enqueueFramegenSurfaceTask {
            val startedAtMs = SystemClock.uptimeMillis()
            FramegenInterceptor.configureOutputSurface(outputSurface)
            val ok = FramegenInterceptor.prewarmContext(prefConfig.width, prefConfig.height)
            if (framegenSurfaceGeneration.get() == generation) {
                decoderRenderer?.setFramegenCaptureSwitchReady(ok)
            }
            LimeLog.info(
                "Framegen prewarm ok=$ok elapsed=${SystemClock.uptimeMillis() - startedAtMs}ms"
            )
        }
    }

    private fun configureFramegenOutputSurface(surface: Surface?) {
        enqueueFramegenSurfaceTask {
            FramegenInterceptor.configureOutputSurface(surface)
        }
    }

    private fun enqueueFramegenSurfaceTask(task: () -> Unit) {
        try {
            framegenSurfaceExecutor.execute { task() }
        } catch (e: RejectedExecutionException) {
            LimeLog.warning("Framegen surface task ignored after shutdown")
        }
    }

    private fun releaseFramegenCapture() {
        framegenSurfaceGeneration.incrementAndGet()
        framegenCapture?.release()
        framegenCapture = null
        framegenAdaptiveController.reset()
        decoderRenderer?.setFramegenCaptureSwitchReady(false)
        decoderRenderer?.framegenSurface = null
        enqueueFramegenSurfaceTask {
            FramegenInterceptor.configureOutputSurface(null)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!surfaceCreated) {
            throw IllegalStateException("Surface changed before creation!")
        }

        val shouldPrepareFramegen =
            !attemptedConnection || (connected && framegenCapture == null && shouldUseFramegen())

        decoderRenderer?.setRenderTarget(holder)

        if (shouldPrepareFramegen) {
            prepareFramegenSurface(holder.surface, !attemptedConnection)
        } else if (framegenCapture != null) {
            configureFramegenOutputSurface(holder.surface)
        }

        if (!attemptedConnection) {
            attemptedConnection = true
            UiHelper.notifyStreamConnecting(this)

            val enableSystemAudioHaptics =
                audioVibrationService?.wantsSystemAudioCoupledDeviceHaptics() == true
            this.audioRenderer = com.limelight.binding.audio.SmartAudioRenderer(
                context = this,
                enableAudioFx = prefConfig.enableAudioFx,
                enableSpatializer = prefConfig.enableSpatializer,
                passthroughBufferBytes = prefConfig.audioPassthroughBufferBytes,
                enableSystemAudioHaptics = enableSystemAudioHaptics,
                onSystemAudioHapticsActiveChanged = { active ->
                    audioVibrationService?.setSystemAudioCoupledDeviceActive(active)
                },
                onAudioPresentationClock = { framePosition, systemNanoTime, sampleRate ->
                    audioVibrationService?.updateAudioPresentationClock(
                        framePosition,
                        systemNanoTime,
                        sampleRate
                    )
                }
            )
            conn?.start(this.audioRenderer!!, decoderRenderer!!, this)

            streamView.post { cursorServiceManager.syncCursorWithStream() }
        } else if (connected && isExtremeResumeEnabled) {
            streamView.post { cursorServiceManager.syncCursorWithStream() }
            audioRenderer?.resumeProcessing()
            decoderRenderer?.resumeProcessing()
        }

        panZoomHandler.handleSurfaceChange()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated = true

        val outputFps = framegenPresentationFps()
        val desiredFrameRate: Float = if (DisplayModeManager.mayReduceRefreshRate(prefConfig) || desiredRefreshRate < outputFps) {
            outputFps.toFloat()
        } else {
            desiredRefreshRate
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            holder.surface.setFrameRate(
                desiredFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.surface.setFrameRate(
                desiredFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
            )
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!surfaceCreated) {
            throw IllegalStateException("Surface destroyed before creation!")
        }

        cursorServiceManager.destroyLocalCursorRenderers()

        if (attemptedConnection) {
            if (isExtremeResumeEnabled && !isFinishing) {
                val globalPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                if (!globalPrefs.getBoolean("checkbox_background_audio", false)) {
                    audioRenderer?.pauseProcessing()
                    LimeLog.info("Extreme Resume: Audio muted for background.")
                }
                decoderRenderer?.pauseProcessing()
                releaseFramegenCapture()
                return
            } else {
                decoderRenderer?.prepareForStop()
                releaseFramegenCapture()
                if (connected) {
                    connectionCallbackHandler.stopConnection()
                }
            }
        } else {
            releaseFramegenCapture()
        }
    }

    @SuppressLint("BatteryLife")
    fun showKeepAliveNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    KEEP_ALIVE_NOTIFICATION_ID
                )
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val isResumeEnabled = prefs.getBoolean("checkbox_resume_stream", false)
                val hasRequestedOptimization = prefs.getBoolean("pref_battery_optimization_requested", false)

                if (isResumeEnabled && !hasRequestedOptimization) {
                    if (ContextCompat.checkSelfPermission(this, "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = "package:$packageName".toUri()
                            try {
                                startActivity(intent)
                                prefs.edit {
                                    putBoolean(
                                        "pref_battery_optimization_requested",
                                        true
                                    )
                                }
                            } catch (e: Exception) {
                                LimeLog.warning("Cannot open battery optimization settings: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        StreamNotificationService.start(this, pcName, appName)
    }

    fun cancelKeepAliveNotification() {
        StreamNotificationService.stop(this)
    }

    fun refreshLocalCursorState(enabled: Boolean) {
        cursorServiceManager.refreshLocalCursorState(enabled)
    }

    override fun mouseMove(deltaX: Int, deltaY: Int) {
        conn?.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }

    override fun mouseButtonEvent(buttonId: Int, down: Boolean) {
        val buttonIndex: Byte = when (buttonId) {
            EvdevListener.BUTTON_LEFT -> MouseButtonPacket.BUTTON_LEFT
            EvdevListener.BUTTON_MIDDLE -> MouseButtonPacket.BUTTON_MIDDLE
            EvdevListener.BUTTON_RIGHT -> MouseButtonPacket.BUTTON_RIGHT
            EvdevListener.BUTTON_X1 -> MouseButtonPacket.BUTTON_X1
            EvdevListener.BUTTON_X2 -> MouseButtonPacket.BUTTON_X2
            else -> {
                LimeLog.warning("Unhandled button: $buttonId")
                return
            }
        }
        if (down) conn?.sendMouseButtonDown(buttonIndex)
        else conn?.sendMouseButtonUp(buttonIndex)
    }

    override fun mouseVScroll(amount: Byte) {
        conn?.sendMouseScroll(amount)
    }

    override fun mouseHScroll(amount: Byte) {
        conn?.sendMouseHScroll(amount)
    }

    override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
        keyboardInputHandler.keyboardEvent(buttonDown, keyCode)
    }

    override fun touchpadEvent(
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
        deviceWidthMm: Short,
        deviceHeightMm: Short,
        buttonState: Byte
    ) {
        conn?.sendTouchpadEvent(
            eventType, pointerId, x, y, pressure,
            contactAreaMajor, contactAreaMinor, rotation,
            deviceWidthMm, deviceHeightMm, buttonState
        )
    }

    override fun touchpadFrameEvent(
        contactCount: Byte,
        eventTypes: ByteArray,
        pointerIds: IntArray,
        x: FloatArray,
        y: FloatArray,
        pressure: FloatArray,
        rotation: Short,
        deviceWidthMm: Short,
        deviceHeightMm: Short,
        buttonState: Byte
    ): Int {
        return conn?.sendTouchpadFrameEvent(
            contactCount, eventTypes, pointerIds,
            x, y, pressure, rotation,
            deviceWidthMm, deviceHeightMm, buttonState
        ) ?: MoonBridge.LI_ERR_UNSUPPORTED
    }

    @Deprecated("Deprecated in Java")
    override fun onSystemUiVisibilityChange(visibility: Int) {
        if (!connected) return
        if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ||
            (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
        ) {
            hideSystemUi(2000)
        }
    }

    override fun onPerfUpdateV(performanceInfo: PerformanceInfo) {
        if (framegenCapture != null) {
            framegenAdaptiveController.onPerformanceInfo(performanceInfo)
        }
        enrichFramegenPerformanceInfo(performanceInfo)
        performanceOverlayManager?.updatePerformanceInfo(performanceInfo)
    }

    override fun isPerfOverlayVisible(): Boolean {
        return performanceOverlayManager?.isPerfOverlayVisible() == true
    }

    override fun onPerfUpdateWG(performanceInfo: PerformanceInfo) {
        enrichFramegenPerformanceInfo(performanceInfo)
        // 缓存最新性能数据，供 ABR 服务使用
        latestPerfInfo = performanceInfo

        runOnUiThread {
            val currentRxBytes = TrafficStats.getTotalRxBytes()
            val timeMillis = System.currentTimeMillis()
            val timeMillisInterval = timeMillis - previousTimeMillis

            if (timeMillisInterval in 1..<5000) {
                performanceInfo.bandWidth = NetHelper.calculateBandwidth(currentRxBytes, previousRxBytes, timeMillisInterval)
            }

            previousTimeMillis = timeMillis
            previousRxBytes = currentRxBytes

            if (controllerManager != null && performanceInfoDisplays.isNotEmpty()) {
                val perfAttrs = HashMap<String, String>()
                perfAttrs[getString(R.string.perf_decoder)] = performanceInfo.decoder ?: ""
                perfAttrs[getString(R.string.perf_resolution)] = "${performanceInfo.initialWidth}x${performanceInfo.initialHeight}"
                perfAttrs[getString(R.string.perf_fps)] = String.format("%.0f", performanceInfo.totalFps)
                perfAttrs[getString(R.string.perf_rx_fps)] = String.format("%.0f", performanceInfo.receivedFps)
                perfAttrs[getString(R.string.perf_rd_fps)] = String.format("%.0f", performanceInfo.renderedFps)
                perfAttrs[getString(R.string.perf_fg_fps)] = if (performanceInfo.framegenFps > 0.5f) {
                    String.format("%.0f", performanceInfo.framegenFps)
                } else {
                    "0"
                }
                perfAttrs[getString(R.string.perf_frame_loss)] = String.format("%.1f", performanceInfo.lostFrameRate)
                perfAttrs[getString(R.string.perf_network_rtt)] = String.format("%d", (performanceInfo.rttInfo shr 32).toInt())
                perfAttrs[getString(R.string.perf_host_latency)] = String.format("%.2f", performanceInfo.aveHostProcessingLatency)
                perfAttrs[getString(R.string.perf_decode_time)] = String.format("%.2f", performanceInfo.decodeTimeMs)
                perfAttrs[getString(R.string.perf_bandwidth)] = performanceInfo.bandWidth ?: ""
                perfAttrs[getString(R.string.perf_render_latency)] = String.format("%.2f", performanceInfo.renderingLatencyMs)
                for (display in performanceInfoDisplays) {
                    display.display(perfAttrs)
                }
            }
        }
    }

    override fun onVideoFrameLoss(framesLost: Int, frameNumber: Int) {
        if (framegenCapture != null) {
            framegenAdaptiveController.onFrameLossEvent(framesLost, frameNumber)
        }
    }

    private fun enrichFramegenPerformanceInfo(performanceInfo: PerformanceInfo) =
        FramegenPerformanceEnricher.update(
            performanceInfo,
            framegenActive = framegenCapture != null,
            baseFps = prefConfig.fps,
            outputFps = framegenAdaptiveController.activePresentationFps
                .takeIf { it > 0 }
                ?: framegenPresentationFps()
        )

    fun removePerformanceInfoDisplay(display: PerformanceInfoDisplay) {
        performanceInfoDisplays.remove(display)
    }

    override fun onUsbPermissionPromptStarting() {
        suppressPipRefCount++
        updatePipAutoEnter()
    }

    override fun onUsbPermissionPromptCompleted() {
        suppressPipRefCount--
        updatePipAutoEnter()
    }

    override fun showGameMenu(device: GameInputDevice?) {
        when (crownSessionController.backKeyMenuMode) {
            BackKeyMenuMode.CROWN_MODE -> {
                if (controllerManager != null && prefConfig.enableCrownFeatures) {
                    controllerManager?.superPagesController?.returnOperation()
                }
            }
            BackKeyMenuMode.NO_MENU -> {
                if (prefConfig.enableCrownFeatures) {
                    controllerManager?.superPagesController?.returnOperation()
                }
            }
            BackKeyMenuMode.NO_MENU_LOCKED -> {}
            BackKeyMenuMode.GAME_MENU -> {
                val existingMenu = activeGameMenu
                if (existingMenu?.isShowing() == true) {
                    return
                }
                existingMenu?.dismiss()
                activeGameMenu = null

                val menu = GameMenu(this, app, conn!!, device) { dismissedMenu ->
                    if (activeGameMenu === dismissedMenu) {
                        activeGameMenu = null
                    }
                    device?.onGameMenuDismissed()
                }
                activeGameMenu = menu
            }
        }
    }

    override fun showGameMenuFromUsb(device: GameInputDevice): Boolean {
        showGameMenu(device)
        return activeGameMenu?.isShowing() == true
    }

    override fun dispatchUsbControllerMenuKey(event: KeyEvent): Boolean {
        val menu = activeGameMenu ?: return false
        if (!menu.dispatchControllerKeyEvent(event)) return false
        return activeGameMenu === menu && menu.isShowing()
    }

    override fun showUsbControllerShortcutHint() {
        controllerShortcutHintView?.apply {
            visibility = View.VISIBLE
            bringToFront()
        }
    }

    override fun hideUsbControllerShortcutHint() {
        controllerShortcutHintView?.visibility = View.GONE
    }

    override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
        return when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> keyboardInputHandler.handleKeyDown(keyEvent)
            KeyEvent.ACTION_UP -> keyboardInputHandler.handleKeyUp(keyEvent)
            KeyEvent.ACTION_MULTIPLE -> keyboardInputHandler.handleKeyMultiple(keyEvent)
            else -> false
        }
    }

    fun disconnect() {
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showGameMenu(null)
    }

    fun togglePerformanceOverlay() {
        val nextMode = when (performanceOverlayMode) {
            PerformanceOverlayMode.HIDDEN -> PerformanceOverlayMode.FLOATING
            PerformanceOverlayMode.FLOATING -> PerformanceOverlayMode.LOCKED
            PerformanceOverlayMode.LOCKED -> PerformanceOverlayMode.HIDDEN
        }
        setPerformanceOverlayMode(nextMode)
    }

    enum class PerformanceOverlayMode {
        HIDDEN,
        FLOATING,
        LOCKED
    }

    val performanceOverlayMode: PerformanceOverlayMode
        get() = when {
            !prefConfig.enablePerfOverlay -> PerformanceOverlayMode.HIDDEN
            prefConfig.perfOverlayLocked -> PerformanceOverlayMode.LOCKED
            else -> PerformanceOverlayMode.FLOATING
        }

    fun setPerformanceOverlayMode(mode: PerformanceOverlayMode) {
        if (performanceOverlayManager == null) return
        prefConfig.enablePerfOverlay = mode != PerformanceOverlayMode.HIDDEN
        prefConfig.perfOverlayLocked = mode == PerformanceOverlayMode.LOCKED
        performanceOverlayManager?.applyOverlayState()
        prefConfig.writePreferences(this)
    }

    fun toggleMicrophoneButton() {
        if (micButton != null) {
            if (micButton?.visibility == View.VISIBLE) {
                micButton?.visibility = View.GONE
                Toast.makeText(this, getString(R.string.toast_mic_button_hidden), Toast.LENGTH_SHORT).show()
            } else {
                micButton?.visibility = View.VISIBLE
                Toast.makeText(this, getString(R.string.toast_mic_button_shown), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleMicrophoneMenuAction() {
        if (prefConfig.micMenuActionMode == PreferenceConfiguration.MIC_MENU_ACTION_TOGGLE_MIC) {
            microphoneManager?.toggleMicrophone()
                ?: Toast.makeText(this, getString(R.string.toast_enable_mic_redirect), Toast.LENGTH_SHORT).show()
        } else {
            toggleMicrophoneButton()
        }
    }

    fun toggleVirtualController() {
        if (virtualController != null && virtualController?.elements?.isNotEmpty() == true) {
            if (isVirtualControllerVisible()) {
                virtualController?.hide()
                Toast.makeText(this, getString(R.string.toast_virtual_controller_hidden), Toast.LENGTH_SHORT).show()
            } else {
                virtualController?.show()
                Toast.makeText(this, getString(R.string.toast_virtual_controller_shown), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_virtual_controller_not_enabled), Toast.LENGTH_SHORT).show()
        }
    }

    fun isVirtualControllerVisible(): Boolean =
        virtualController?.elements?.firstOrNull()?.visibility == View.VISIBLE

    fun initializeControllerManager() {
        val manager = controllerManager ?: ControllerManager(streamView.parent as FrameLayout, this)
            .also { controllerManager = it }
        if (manager.keyboardUIController == null) {
            manager.keyboardUIController = getOrCreateKeyboardUIController()
        }
        manager.refreshLayout()
    }

    var isCrownFeatureEnabled: Boolean
        get() = prefConfig.enableCrownFeatures
        set(value) {
            prefConfig.enableCrownFeatures = value
            prefConfig.onscreenKeyboard = value
            if (value) {
                initializeControllerManager()
                controllerManager?.show()
            } else {
                controllerManager?.hide()
            }
        }

    fun addPerformanceInfoDisplay(performanceInfoDisplay: PerformanceInfoDisplay) {
        performanceInfoDisplays.add(performanceInfoDisplay)
    }

    fun refreshDisplayPosition() {
        DisplayPositionManager(this, prefConfig, streamView).refreshDisplayPosition(surfaceCreated)
    }

    val activeStreamView: StreamView?
        get() {
            if (externalDisplayManager?.isUsingExternalDisplay() == true && externalStreamView != null) {
                return externalStreamView
            }
            return streamView
        }

    fun getHandleMotionEvent(streamView: StreamView, event: MotionEvent): Boolean {
        return touchInputHandler.handleMotionEvent(streamView, event)
    }

    private fun applyLastSettingsToCurrentSession() {
        if (appSettingsManager != null) {
            val applied = appSettingsManager?.applyLastSettingsFromIntent(intent, prefConfig) == true
            if (applied) {
                Toast.makeText(this, getString(R.string.app_last_settings_start_with_last), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, getString(R.string.toast_enable_notification_for_bg), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        val REFERENCE_HORIZ_RES = 1280
        val REFERENCE_VERT_RES = 720

        val EXTRA_HOST = "Host"
        val EXTRA_PORT = "Port"
        val EXTRA_HTTPS_PORT = "HttpsPort"
        val EXTRA_APP_NAME = "AppName"
        val EXTRA_APP_ID = "AppId"
        val EXTRA_UNIQUEID = "UniqueId"
        val EXTRA_PC_UUID = "UUID"
        val EXTRA_PC_NAME = "PcName"
        val EXTRA_PAIR_NAME = "PairName"
        val EXTRA_APP_HDR = "HDR"
        val EXTRA_SERVER_CERT = "ServerCert"
        val EXTRA_PC_USEVDD = "usevdd"
        val EXTRA_APP_CMD = "CmdList"
        val EXTRA_DISPLAY_NAME = "DisplayName"
        val EXTRA_SCREEN_COMBINATION_MODE = "Screen combination mode"
        val EXTRA_FORCE_RESUME_CURRENT_SESSION = "ForceResumeCurrentSession"

        private const val KEEP_ALIVE_NOTIFICATION_ID = 1001
    }
}
