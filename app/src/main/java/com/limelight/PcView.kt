@file:Suppress("DEPRECATION")
package com.limelight

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException

import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.limelight.binding.PlatformBinding
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.computers.ComputerManagerService
import com.limelight.computers.PairStatePreflight
import com.limelight.dialogs.AddressSelectionDialog
import com.limelight.grid.PcGridAdapter
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.wol.WakeOnLanSender
import com.limelight.preferences.AddComputerManually
import com.limelight.preferences.BackgroundSource
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.StreamSettings
import com.limelight.services.KeyboardAccessibilityService
import com.limelight.ui.AdapterFragment
import com.limelight.ui.AdapterFragmentCallbacks
import com.limelight.ui.FeatureGuideRegistry
import com.limelight.ui.UiDismissKeyHandler
import com.limelight.ui.ViewFeatureGuide
import com.limelight.ui.ViewFeatureGuideStep
import com.limelight.utils.AboutDialogLauncher
import com.limelight.utils.AnalyticsManager
import com.limelight.utils.AppDialogStyler
import com.limelight.utils.AppActionSheet
import com.limelight.utils.AppCacheManager
import com.limelight.utils.CacheHelper
import com.limelight.utils.ConfigurationSyncScheduler
import com.limelight.utils.Dialog
import com.limelight.utils.easytier.EasyTierController
import com.limelight.utils.HelpLauncher
import com.limelight.utils.Iperf3Tester
import com.limelight.utils.NetHelper
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import com.limelight.utils.SpinnerDialog
import com.limelight.utils.UiHelper
import com.limelight.utils.UpdateManager
import com.squareup.seismic.ShakeDetector

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.zxing.integration.android.IntentIntegrator

import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParserException

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.hardware.SensorManager
import android.net.Uri
import android.net.VpnService
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import androidx.core.animation.doOnEnd
import android.view.animation.DecelerateInterpolator
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.ColorFilterTransformation
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PcView : Activity(), AdapterFragmentCallbacks, ShakeDetector.Listener, EasyTierController.VpnPermissionCallback {

    // Constants
    companion object {
        private const val REFRESH_DEBOUNCE_DELAY = 150L
        private const val STARTUP_UPDATE_CHECK_DELAY = 5000L
        private const val STARTUP_DIALOG_GAP_DELAY = 250L
        private const val SHAKE_DEBOUNCE_INTERVAL = 3000L
        private const val MAX_DAILY_REFRESH = 7
        private const val VPN_PERMISSION_REQUEST_CODE = 101
        private const val QR_SCAN_REQUEST_CODE = 102

        private const val REFRESH_PREF_NAME = "RefreshLimit"
        private const val REFRESH_COUNT_KEY = "refresh_count"
        private const val REFRESH_DATE_KEY = "refresh_date"
        private const val SCENE_PREF_NAME = "SceneConfigs"
        private const val SCENE_KEY_PREFIX = "scene_"

        // Menu item IDs
        private const val PAIR_ID = 2
        private const val UNPAIR_ID = 3
        private const val WOL_ID = 4
        private const val DELETE_ID = 5
        private const val RESUME_ID = 6
        private const val QUIT_ID = 7
        private const val VIEW_DETAILS_ID = 8
        private const val FULL_APP_LIST_ID = 9
        private const val TEST_NETWORK_ID = 10
        private const val GAMESTREAM_EOL_ID = 11
        private const val SLEEP_ID = 12
        private const val IPERF3_TEST_ID = 13
        private const val SECONDARY_SCREEN_ID = 14
        private const val DISABLE_IPV6_ID = 15
        private const val OPEN_WEBUI_ID = 16
        private const val MORE_ACTIONS_ID = 17

    }

    // UI Components
    private var noPcFoundLayout: View? = null
    private lateinit var pcGridAdapter: PcGridAdapter
    private var pcListView: AbsListView? = null
    private var backgroundImageView: ImageView? = null
    private var topSafeArea: Space? = null

    // State
    private var isFirstLoad = true
    private var freezeUpdates = false
    private var runningPolling = false
    private var inForeground = false
    private var completeOnCreateCalled = false
    private var pendingSplashFadeIn = true
    private var backgroundSourceDialogShowing = false
    private var startupUpdateCheckPending = false
    private var startupUpdateCheckRan = false
    private var lastShakeTime = 0L
    private var activeSceneNumber: Int? = null

    // Helpers
    private lateinit var shortcutHelper: ShortcutHelper
    private var easyTierController: EasyTierController? = null
    private var analyticsManager: AnalyticsManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var currentAddressDialog: AddressSelectionDialog? = null
    private var backgroundImageRefreshReceiver: BroadcastReceiver? = null

    // Single Job owning the current async background load. Replacing it on every
    // reload cancels any in-flight Glide work from the previous source so a late
    // completion cannot overpaint the newer selection.
    private var backgroundLoadJob: Job? = null
    private val backgroundTargetLock = Any()
    private var backgroundLoadGeneration = 0
    private var backgroundFutureTarget: FutureTarget<Bitmap>? = null
    private var lastBackgroundSource: BackgroundSource? = null
    private var backgroundPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // Managers
    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null

    // Handlers
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var pendingRefreshRunnable: Runnable? = null

    // 主线程作用域，用于收集 ComputerManagerService 的 Flow。onDestroy 时 cancel。
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollingCollectJob: Job? = null

    lateinit var clientName: String

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as ComputerManagerService.ComputerManagerBinder

            // 立即赋值并启动轮询，让 first-poll 的 SSL 握手与下面的预热并行——
            // pollComputer 路径只依赖 idManager.uniqueId（service.onCreate 已同步初始化），
            // 证书在 NvHTTP 第一次握手时才需要。原本在这里串行 waitForReady() +
            // getClientCertificate() 会把证书 IO/RSA keygen 的 100~2000ms 全部摞在
            // "PC 卡片亮起绿点" 的关键路径上，没必要。
            //
            // 即便预热慢于首次 tryPollIp，也只是把"加密初始化"这段时间从串行变成
            // 与网络往返重叠，最坏情况持平、最佳情况节省整段证书时间。
            managerBinder = localBinder
            startComputerUpdates()

            // 后台预热：等 DiscoveryService bind（mDNS 可能还没好），并把客户端证书
            // 提到内存。这两件事都不在已知 PC 的 first-poll 关键路径上。
            uiScope.launch(Dispatchers.IO) {
                localBinder.waitForReady()
                AndroidCryptoProvider(this@PcView).getClientCertificate()
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            managerBinder = null
        }
    }

    // Lifecycle Methods

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the SplashScreen API BEFORE super.onCreate so the system can hand off
        // the launch theme. On API 31+ this is the system SplashScreen; on lower APIs the
        // androidx core-splashscreen backport shows the static icon over splash_bg and then
        // hands control to postSplashScreenTheme (AppTheme).
        val splashScreen = installSplashScreen()
        // Hold the splash on screen until PcView's real content view is inflated
        // (completeOnCreate -> initializeViews -> setContentView(activity_pc_view)).
        // Without this, the system fires onExitAnimationListener as soon as the
        // GL-detection surfaceView reports its first frame, which is just a tiny
        // black surface — causing a visible "black flash" before the real UI.
        // Safety cap: 1500ms — never block UI longer than that even on slow GL probes.
        val splashDeadline = SystemClock.uptimeMillis() + 1500L
        splashScreen.setKeepOnScreenCondition {
            !completeOnCreateCalled && SystemClock.uptimeMillis() < splashDeadline
        }
        // 自定义退场：纯淡出 + 极轻微的放大（1f→1.04f），用 Decelerate 收尾。
        // 之前的 AnticipateInterpolator(1.2f) + scale 1→1.15→0.7 是“反向回弹再急速缩小”
        // 的弹射动画，与 PcView 内容淡入并行时观感非常跳。改为单调减速、轻位移，
        // 让 splash 静静淡出、PcView 同步淡入，形成连贯的 cross-fade。
        splashScreen.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            // provider.iconView is declared non-null in androidx core-splashscreen,
            // but on some OEM ROMs (e.g. Xiaomi HyperOS / Android 16) the system
            // SplashScreenView.getIconView() returns null and the Kotlin intrinsic
            // null-check (or the underlying NPE) crashes the launch. Guard it.
            val icon: View? = try { provider.iconView } catch (_: Throwable) { null }
            val animators = mutableListOf<android.animation.Animator>(
                ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
            )
            if (icon != null) {
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.04f)
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.04f)
                animators += ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f)
            }
            val set = AnimatorSet().apply {
                duration = 280L
                interpolator = DecelerateInterpolator(1.6f)
                playTogether(animators)
            }
            set.doOnEnd { provider.remove() }
            set.start()
        }
        super.onCreate(savedInstanceState)
        ConfigurationSyncScheduler.runNow(this)

        //自动获取无障碍权限
        try {
            val cn = ComponentName(this, KeyboardAccessibilityService::class.java)
            val myService = cn.flattenToString()
            var enabledServices = Settings.Secure.getString(contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

            if (enabledServices == null || !enabledServices.contains(myService)) {
                enabledServices = if (enabledServices.isNullOrEmpty()) {
                    myService
                } else {
                    "$enabledServices:$myService"
                }

                // 这里可能会抛异常
                Settings.Secure.putString(contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServices)
            }
        } catch (e: SecurityException) {
            // 没无障碍权限
        }

        easyTierController = EasyTierController(this, this)
        inForeground = true

        val glPrefs = GlPreferences.readPreferences(this)
        if (glPrefs.savedFingerprint != Build.FINGERPRINT || glPrefs.glRenderer.isEmpty()) {
            initGlRenderer(glPrefs)
        } else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer)
            completeOnCreate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (completeOnCreateCalled) {
            initializeViews()
        }
    }

    override fun onResume() {
        super.onResume()
        UiHelper.showDecoderCrashDialog(this)
        inForeground = true
        startComputerUpdates()

        analyticsManager?.startUsageTracking()
        startShakeDetector()
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        stopComputerUpdates(false)

        analyticsManager?.stopUsageTracking()
        stopShakeDetector()
    }

    override fun onStop() {
        super.onStop()
        Dialog.closeDialogs()
    }

    override fun onDestroy() {
        super.onDestroy()

        uiScope.cancel()
        easyTierController?.onDestroy()
        if (managerBinder != null) {
            unbindService(serviceConnection)
        }
        if (currentAddressDialog != null) {
            currentAddressDialog?.dismiss()
            currentAddressDialog = null
        }
        unregisterBackgroundReceiver()
        unregisterBackgroundPrefsListener()
        cancelPreviousBackgroundLoad()

        analyticsManager?.cleanup()
        if (pendingRefreshRunnable != null) {
            refreshHandler.removeCallbacks(pendingRefreshRunnable!!)
            pendingRefreshRunnable = null
        }
    }

    // Initialization Methods

    private fun initGlRenderer(glPrefs: GlPreferences) {
        val surfaceView = GLSurfaceView(this)
        surfaceView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig) {
                glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER)
                glPrefs.savedFingerprint = Build.FINGERPRINT
                glPrefs.writePreferences()
                LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer)
                runOnUiThread { completeOnCreate() }
            }

            override fun onSurfaceChanged(gl10: GL10, i: Int, i1: Int) {}

            override fun onDrawFrame(gl10: GL10) {}
        })
        setContentView(surfaceView)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun completeOnCreate() {
        completeOnCreateCalled = true
        shortcutHelper = ShortcutHelper(this)
        UiHelper.setLocale(this)

        analyticsManager = AnalyticsManager.getInstance(this)
        analyticsManager?.logAppLaunch()
        // 延后 5 秒再做更新检查，避免一打开 PcView 就被对话框打断浏览
        scheduleStartupUpdateCheck()

        bindService(Intent(this, ComputerManagerService::class.java), serviceConnection,
            BIND_AUTO_CREATE
        )

        pcGridAdapter = PcGridAdapter(this, PreferenceConfiguration.readPreferences(this))
        pcGridAdapter.setAvatarClickListener { computer, itemView -> handleAvatarClick(computer, itemView) }

        initShakeDetector()
        registerBackgroundReceiver()
        registerBackgroundPrefsListener()
        initializeViews()
    }

    private fun initializeViews() {
        val loadGeneration = cancelPreviousBackgroundLoad()
        setContentView(R.layout.activity_pc_view)
        UiHelper.notifyNewRootView(this)

        // PcView 根布局从 0 淡入，与 splash 退场同时开始，形成 cross-fade：
        // - splash 退场 280ms，内容淡入 320ms + Decelerate，后者略长以接住视觉重量
        // - splash_bg 与 advance_setting_background 同色，背景层不会闪烁
        // - 仅首次 inflation 执行，旋转/配置变化不重复
        if (pendingSplashFadeIn) {
            pendingSplashFadeIn = false
            findViewById<View>(R.id.pcViewRootLayout)?.let { root ->
                root.alpha = 0f
                root.animate()
                    .alpha(1f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator(1.4f))
                    .start()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false)
        }

        topSafeArea = findViewById(R.id.topSafeArea)
        topSafeArea?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.layoutParams.height = insets.top
                windowInsets
            }
        }

        clientName = Settings.Global.getString(contentResolver, "device_name")
            ?: Build.MODEL
            ?: "Moonlight V+ Client"
        backgroundImageView = findViewById(R.id.pcBackgroundImage)

        loadBackgroundImage(loadGeneration)
        setupBackgroundImageLongPress()
        initSceneButtons()
        maybeShowBackgroundSourceDialog()
        // Surface any uncaught crash captured by CrashReporter on a previous run
        // so the user can ship the log back to the dev with one tap.
        com.limelight.crash.CrashReportPrompt.maybeShow(this)

        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this))
        setupButtons()
        setupAdapterFragment()

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout)
        addAddComputerCard()
        updateNoPcFoundVisibility()
        handleInitialLoad()
        maybeShowPcViewFeatureGuide()
    }

    private fun maybeShowPcViewFeatureGuide() {
        ViewFeatureGuide.showWhenReady(
            activity = this,
            spec = FeatureGuideRegistry.PcViewDiscovery
        ) {
            val hostCard = currentGuideHostCard() ?: return@showWhenReady emptyList()

            buildList {
                add(ViewFeatureGuideStep(
                    { currentGuideHostCard() },
                    getString(R.string.pcview_guide_host_title),
                    getString(R.string.pcview_guide_host_body)
                ))
                findViewById<View>(R.id.pcToolbarMenuButton)?.let {
                    add(ViewFeatureGuideStep(
                        it,
                        getString(R.string.pcview_guide_more_title),
                        getString(R.string.pcview_guide_more_body)
                    ))
                }
                findViewById<View>(R.id.scenePresetContainer)?.let {
                    add(ViewFeatureGuideStep(
                        it,
                        getString(R.string.pcview_guide_scene_title),
                        getString(R.string.pcview_guide_scene_body)
                    ))
                }
            }
        }
    }

    private fun currentGuideHostCard(): View? {
        val list = pcListView ?: return null
        for (childIndex in 0 until list.childCount) {
            val adapterPosition = list.firstVisiblePosition + childIndex
            val item = pcGridAdapter.getItem(adapterPosition) as? ComputerObject ?: continue
            if (!PcGridAdapter.isAddComputerCard(item)) {
                return list.getChildAt(childIndex)
            }
        }
        return null
    }

    private fun setupButtons() {
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        val aboutButton = findViewById<ImageButton>(R.id.aboutButton)
        val easyTierButton = findViewById<ImageButton>(R.id.easyTierControlButton)
        val toolbarMenuButton = findViewById<ImageButton>(R.id.pcToolbarMenuButton)

        settingsButton.setOnClickListener { startActivity(Intent(this, StreamSettings::class.java)) }

        aboutButton?.setOnClickListener { AboutDialogLauncher.show(this) }
        easyTierButton?.setOnClickListener { showEasyTierControlDialog() }
        toolbarMenuButton?.setOnClickListener { showPcToolbarMenu(it) }
    }

    private fun showPcToolbarMenu(anchor: View) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val popupWidth = dpToPx(if (isLandscape) 256 else 236)

        lateinit var popup: PopupWindow
        val menu = object : LinearLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (UiDismissKeyHandler.handle(event.action, event.keyCode, popup::dismiss)) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
            background = ContextCompat.getDrawable(this@PcView, R.drawable.pc_toolbar_menu_panel_bg)
        }

        popup = PopupWindow(menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpToPx(8).toFloat()
            }
        }

        addToolbarMenuItem(
            menu,
            R.drawable.ic_restore,
            getString(R.string.pcview_toolbar_restore_session)
        ) {
            popup.dismiss()
            restoreLastSession()
        }

        val showUnpaired = pcGridAdapter.isShowUnpairedDevices()
        addToolbarMenuItem(
            menu,
            if (showUnpaired) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            getString(
                if (showUnpaired) R.string.pcview_toolbar_toggle_unpaired_hide
                else R.string.pcview_toolbar_toggle_unpaired_show
            )
        ) {
            popup.dismiss()
            toggleUnpairedDevices()
        }

        addToolbarMenuItem(
            menu,
            R.drawable.ic_theme_mode,
            getString(R.string.pcview_toolbar_theme),
            getThemeModeLabel(UiHelper.getAppThemeMode(this)),
            accent = true
        ) {
            popup.dismiss()
            showThemeModeDialog()
        }

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val menuHeight = menu.measuredHeight
        val anchorLeft = anchorLocation[0]
        val anchorTop = anchorLocation[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height

        val popupX = if (isLandscape) {
            anchorRight
        } else {
            anchorRight - popupWidth
        }.coerceIn(0, (screenWidth - popupWidth).coerceAtLeast(0))
        val desiredY = if (anchorBottom + menuHeight > screenHeight) {
            anchorTop - menuHeight
        } else {
            anchorBottom
        }
        val popupY = desiredY.coerceIn(0, (screenHeight - menuHeight).coerceAtLeast(0))
        popup.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, popupX, popupY)
    }

    private fun addToolbarMenuItem(
        parent: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String? = null,
        accent: Boolean = false,
        onClick: () -> Unit
    ) {
        val iconColor = ColorStateList.valueOf(ContextCompat.getColor(
            this,
            if (accent) R.color.app_dialog_accent_color else R.color.ui_shell_text_secondary
        ))
        val titleColor = ContextCompat.getColor(this, R.color.ui_shell_text_primary)
        val subtitleColor = ContextCompat.getColor(this, R.color.ui_shell_text_secondary)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@PcView, R.drawable.pc_toolbar_menu_item_bg)
            minimumHeight = dpToPx(if (subtitle == null) 46 else 50)
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            setOnClickListener { onClick() }
        }

        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = iconColor
        }, LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)).apply {
            marginEnd = dpToPx(10)
        })

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@PcView).apply {
                text = title
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(titleColor)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            if (subtitle != null) {
                addView(TextView(this@PcView).apply {
                    text = subtitle
                    textSize = 11f
                    setTextColor(subtitleColor)
                    includeFontPadding = false
                    setPadding(0, dpToPx(3), 0, 0)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        parent.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(1)
            bottomMargin = dpToPx(1)
        })
    }

    private fun showThemeModeDialog() {
        val modes = arrayOf(
            UiHelper.THEME_MODE_SYSTEM,
            UiHelper.THEME_MODE_LIGHT,
            UiHelper.THEME_MODE_DARK
        )
        val labels = modes.map { getThemeModeLabel(it) }.toTypedArray()
        val checked = modes.indexOf(UiHelper.getAppThemeMode(this)).coerceAtLeast(0)

        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.pcview_theme_dialog_title)
            .setSingleChoiceItems(labels, checked) { dialogInterface, which ->
                val mode = modes[which]
                UiHelper.setAppThemeMode(this, mode)
                showToast(getString(R.string.pcview_theme_applied, labels[which]))
                dialogInterface.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.dialog_button_close, null)
            .create()
        dialog.show()
        AppDialogStyler.applySystemChoiceList(dialog, this)
    }

    private fun getThemeModeLabel(mode: String): String {
        return when (mode) {
            UiHelper.THEME_MODE_LIGHT -> getString(R.string.pcview_theme_light)
            UiHelper.THEME_MODE_DARK -> getString(R.string.pcview_theme_dark)
            else -> getString(R.string.pcview_theme_follow_system)
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun setupAdapterFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.pcFragmentContainer, AdapterFragment())
                .commitAllowingStateLoss()
    }

    private fun updateNoPcFoundVisibility() {
        val isEmpty = pcGridAdapter.count == 0 ||
                (pcGridAdapter.count == 1 && PcGridAdapter.isAddComputerCard(pcGridAdapter.getItem(0) as ComputerObject))
        noPcFoundLayout?.visibility = if (isEmpty) View.VISIBLE else View.INVISIBLE
    }

    private fun handleInitialLoad() {
        if (isFirstLoad) {
            if (pendingRefreshRunnable != null) {
                refreshHandler.removeCallbacks(pendingRefreshRunnable!!)
                pendingRefreshRunnable = null
            }
            if (pcListView != null) {
                pcGridAdapter.notifyDataSetChanged()
            }
        } else {
            debouncedNotifyDataSetChanged()
        }
    }

    // Background Image Methods
    //
    // Design (issue #263 follow-up refactor): `BackgroundSource` is the single
    // authority over "where does the wallpaper come from" — it handles pref I/O,
    // URL construction, TV detection, legacy migration, and broadcasting
    // refreshes. This class just drives Glide against whatever target the
    // source returns, and cancels any in-flight load on reload so a stale
    // request can never overpaint a newer one.

    private fun loadBackgroundImage(existingGeneration: Int? = null) {
        if (backgroundImageView == null) return

        val loadGeneration = existingGeneration ?: cancelPreviousBackgroundLoad()

        val orientation = resources.configuration.orientation
        val resolved = BackgroundSource.resolveCurrentTarget(this, orientation)
        val source = resolved.source
        val target = resolved.target
        lastBackgroundSource = source

        if (target == null) {
            // "none" or self-demoted bad state → leave the view empty.
            backgroundImageView?.setImageDrawable(null)
            return
        }

        backgroundLoadJob = uiScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeBackgroundBitmap(resolved, loadGeneration)
                }
                if (isActive) {
                    applyBlurredBackground(bitmap)
                }
            } catch (_: CancellationException) {
                // Superseded by a newer load; nothing to do.
            } catch (e: ExecutionException) {
                handleGlideException(e)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Glide target normalization: HTTP URLs go straight, filesystem paths become Files. */
    private fun resolveGlideTarget(target: String): Any {
        if (target.startsWith("http")) return target
        val localFile = File(target)
        return if (localFile.exists()) localFile
        else target // let Glide surface the error
    }

    /**
     * Local backgrounds never need more pixels than the display. Decoding a
     * gallery image at its original dimensions can exceed Android's Canvas
     * bitmap limit before the ImageView can scale it (issue #447).
     */
    private fun backgroundDecodeSize(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }

    private fun decodeBackgroundBitmap(
        resolved: BackgroundSource.ResolvedTarget,
        loadGeneration: Int
    ): Bitmap {
        val target = requireNotNull(resolved.target)
        val request = Glide.with(this@PcView as Context)
            .asBitmap()
            .load(resolveGlideTarget(target))
            .skipMemoryCache(true)
            .signature(ObjectKey(resolved.cacheKey))
            // Keep the exact response bytes so settings can reuse the image
            // selected by random-image APIs instead of issuing a second draw.
            .diskCacheStrategy(DiskCacheStrategy.DATA)

        val futureTarget = if (resolved.source !== BackgroundSource.Local) {
            // Preserve the existing decode and cache behavior for network
            // backgrounds, including full-resolution long-press saves.
            request.submit()
        } else {
            val (width, height) = backgroundDecodeSize()
            request
                .apply(
                    RequestOptions()
                        .override(width, height)
                        .downsample(DownsampleStrategy.CENTER_INSIDE)
                        .format(DecodeFormat.PREFER_RGB_565)
                )
                .submit(width, height)
        }

        val retained = synchronized(backgroundTargetLock) {
            if (loadGeneration != backgroundLoadGeneration) {
                false
            } else {
                backgroundFutureTarget = futureTarget
                true
            }
        }
        if (!retained) {
            futureTarget.cancel(true)
            Glide.with(applicationContext).clear(futureTarget)
            throw CancellationException("Background load was superseded")
        }

        return futureTarget.get()
    }

    /**
     * Stops the previous decode after releasing both Glide targets. Returns a
     * token that prevents an older request from replacing a newer wallpaper.
     */
    private fun cancelPreviousBackgroundLoad(): Int {
        backgroundLoadJob?.cancel()
        backgroundImageView?.let { Glide.with(applicationContext).clear(it) }

        val (generation, previousTarget) = synchronized(backgroundTargetLock) {
            backgroundLoadGeneration += 1
            backgroundLoadGeneration to backgroundFutureTarget.also {
                backgroundFutureTarget = null
            }
        }
        previousTarget?.let {
            it.cancel(true)
            Glide.with(applicationContext).clear(it)
        }
        return generation
    }

    private fun applyBlurredBackground(bitmap: Bitmap) {
        if (backgroundImageView == null) return
        Glide.with(this as Context)
                .load(bitmap)
                .apply(RequestOptions.bitmapTransform(BlurTransformation(2, 3)))
                .transform(ColorFilterTransformation(Color.argb(120, 0, 0, 0)))
                .into(backgroundImageView!!)
    }

    private fun handleGlideException(e: ExecutionException) {
        val cause = e.cause
        if (cause != null) {
            val msg = cause.message
            if (msg != null && (msg.contains("HttpException") || msg.contains("SocketException") || msg.contains("MediaMetadataRetriever"))) {
                LimeLog.warning("Background image download failed: $msg")
                return
            }
        }
        e.printStackTrace()
    }

    private fun setupBackgroundImageLongPress() {
        backgroundImageView?.setOnLongClickListener {
            saveImageWithPermissionCheck()
            true
        }
    }

    private fun scheduleStartupUpdateCheck(delayMs: Long = STARTUP_UPDATE_CHECK_DELAY) {
        refreshHandler.postDelayed({ runStartupUpdateCheckWhenIdle() }, delayMs)
    }

    private fun runStartupUpdateCheckWhenIdle() {
        if (startupUpdateCheckRan || isFinishing || isDestroyed) {
            return
        }
        if (backgroundSourceDialogShowing) {
            startupUpdateCheckPending = true
            return
        }

        startupUpdateCheckPending = false
        startupUpdateCheckRan = true
        UpdateManager.checkForUpdatesOnStartup(this)
    }

    private fun resumePendingStartupUpdateCheck() {
        if (!startupUpdateCheckPending || backgroundSourceDialogShowing) {
            return
        }
        scheduleStartupUpdateCheck(STARTUP_DIALOG_GAP_DELAY)
    }

    /**
     * First-launch background source picker (issue #263).
     *
     * - TV / Leanback devices: no dialog ever. They are auto-switched to Picsum
     *   (photography, family-friendly) silently because D-pad dialogs on a
     *   shared TV are worse UX than picking the safe option.
     * - Everyone else on a fresh install: one-time picker so nobody is
     *   ambushed by animé wallpapers in public / at work.
     * - Anyone who already picked, or who already saw+dismissed the dialog,
     *   is skipped (idempotent).
     */
    private fun maybeShowBackgroundSourceDialog() {
        if (BackgroundSource.isDialogShown(this)) return

        // Running current() also runs the legacy migration once, so after this
        // call `background_source` is authoritative.
        val existing = BackgroundSource.current(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.contains(BackgroundSource.KEY_SOURCE) && existing !is BackgroundSource.Auto) {
            // User already picked something explicit; just remember we're done.
            BackgroundSource.markDialogShown(this)
            return
        }
        if (BackgroundSource.isTvDevice(this)) {
            BackgroundSource.setActive(this, BackgroundSource.Picsum)
            return
        }

        val choices = listOf(
            BackgroundSource.Picsum to R.string.background_source_picsum,
            BackgroundSource.Pipw   to R.string.background_source_pipw,
            BackgroundSource.None   to R.string.background_source_none,
        )

        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setCustomTitle(createBackgroundSourceDialogHeader())
            .setItems(choices.map { getString(it.second) }.toTypedArray()) { _, which ->
                BackgroundSource.setActive(this, choices[which].first)
            }
            .setNegativeButton(R.string.background_source_dialog_later) { _, _ ->
                // Defer: remember the nag, keep Auto default.
                BackgroundSource.setActive(this, BackgroundSource.Auto)
            }
            .setOnCancelListener {
                BackgroundSource.setActive(this, BackgroundSource.Auto)
            }
            .create()

        dialog.setOnDismissListener {
            backgroundSourceDialogShowing = false
            resumePendingStartupUpdateCheck()
        }
        backgroundSourceDialogShowing = true
        dialog.show()
        AppDialogStyler.applySystemChoiceList(dialog, this)
    }

    private fun createBackgroundSourceDialogHeader(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        fun matchWrapParams() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(22), dp(28), dp(8))
            addView(TextView(this@PcView).apply {
                text = getString(R.string.background_source_dialog_title)
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@PcView, R.color.app_dialog_title_color))
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }, matchWrapParams())
            addView(TextView(this@PcView).apply {
                text = getString(R.string.background_source_dialog_message)
                gravity = Gravity.CENTER
                textSize = 14f
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(0, dp(8), 0, 0)
                setTextColor(ContextCompat.getColor(this@PcView, R.color.app_dialog_subtitle_color))
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }, matchWrapParams())
        }
    }

    private fun refreshBackgroundImage(isFromShake: Boolean) {
        if (backgroundImageView == null) return

        val loadGeneration = cancelPreviousBackgroundLoad()
        BackgroundSource.invalidateResolvedTarget()

        val orientation = resources.configuration.orientation
        val resolved = BackgroundSource.resolveCurrentTarget(this, orientation)
        val source = resolved.source
        val target = resolved.target
        lastBackgroundSource = source

        if (target == null) {
            backgroundImageView?.setImageDrawable(null)
            return
        }
        backgroundLoadJob = uiScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeBackgroundBitmap(resolved, loadGeneration)
                }
                if (isActive) {
                    applyBlurredBackground(bitmap)
                    if (isFromShake) {
                        showToast(getString(R.string.background_refreshed_with_remaining, getRemainingRefreshCount()))
                    }
                }
            } catch (_: CancellationException) {
                // superseded
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(getString(R.string.refresh_failed_with_error, friendlyNetworkError(e)))
            }
        }
    }

    /** 把 Glide / 网络异常翻译成给用户看的简短提示，避免展示 stack trace 风格的英文。 */
    private fun friendlyNetworkError(e: Throwable): String {
        // 拆掉 ExecutionException 包装
        val cause = (e as? ExecutionException)?.cause ?: e
        val msg = cause.message ?: ""
        return when {
            msg.contains("HttpException") || msg.contains("status code") ||
            msg.contains("SocketException") || msg.contains("UnknownHost") ||
            msg.contains("timeout", ignoreCase = true) ->
                getString(R.string.refresh_failed_network)
            else -> cause.javaClass.simpleName
        }
    }

    // Image Save Methods

    private fun saveImageWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showToast(getString(R.string.storage_permission_required))
            requestStoragePermission()
            return
        }
        saveImage()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun saveImage() {
        val resolved = BackgroundSource.resolveCurrentTarget(
            this,
            resources.configuration.orientation
        )
        if (resolved.target == null || backgroundImageView?.drawable == null) {
            showToast(getString(R.string.image_not_loaded_please_retry))
            return
        }

        showToast(getString(R.string.downloading_image_please_wait))
        saveResolvedBackground(resolved)
    }

    /**
     * Save only the bytes belonging to the wallpaper currently on screen.
     * A cache miss must never fall back to a fresh network request because
     * random-image endpoints can return a different wallpaper for the same URL.
     */
    private fun saveResolvedBackground(resolved: BackgroundSource.ResolvedTarget) {
        uiScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    saveResolvedBackgroundFromCache(resolved)
                }
                refreshSystemPic(file)
                showToast(getString(R.string.image_saved_successfully))
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(getString(R.string.image_save_failed_with_error, e.message))
            }
        }
    }

    private fun saveResolvedBackgroundFromCache(
        resolved: BackgroundSource.ResolvedTarget
    ): File {
        val target = requireNotNull(resolved.target)
        val futureTarget = Glide.with(applicationContext)
            .asBitmap()
            .load(resolveGlideTarget(target))
            .apply(
                RequestOptions()
                    .signature(ObjectKey(resolved.cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .onlyRetrieveFromCache(true)
            )
            .submit()

        try {
            return writeBitmapToFile(futureTarget.get())
        } finally {
            Glide.with(applicationContext).clear(futureTarget)
        }
    }

    private fun writeBitmapToFile(bitmap: Bitmap): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "setu")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create directory")
        }

        val fileName = "pipw-${System.currentTimeMillis()}.png"
        val file = File(dir, fileName)
        FileOutputStream(file).use { outputStream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw IOException("Failed to encode bitmap")
            }
            outputStream.flush()
        }
        return file
    }

    private fun refreshSystemPic(file: File) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(file)
        sendBroadcast(intent)
    }

    // Shake Detection Methods

    private fun initShakeDetector() {
        shakeDetector = ShakeDetector(this)
        shakeDetector?.setSensitivity(ShakeDetector.SENSITIVITY_MEDIUM)
    }

    private fun startShakeDetector() {
        if (shakeDetector == null) return
        try {
            shakeDetector?.start(getSystemService(SENSOR_SERVICE) as SensorManager)
        } catch (e: Exception) {
            LimeLog.warning("shakeDetector start failed: " + e.message)
        }
    }

    private fun stopShakeDetector() {
        if (shakeDetector == null) return
        try {
            shakeDetector?.stop()
        } catch (e: Exception) {
            LimeLog.warning("shakeDetector stop failed: " + e.message)
        }
    }

    override fun hearShake() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastShakeTime < SHAKE_DEBOUNCE_INTERVAL) {
            val remaining = (SHAKE_DEBOUNCE_INTERVAL - (currentTime - lastShakeTime)) / 1000
            runOnUiThread { showToast(getString(R.string.please_wait_seconds, remaining)) }
            return
        }

        if (!canRefreshToday()) {
            runOnUiThread { showToast(getString(R.string.daily_limit_reached)) }
            return
        }

        lastShakeTime = currentTime
        incrementRefreshCount()
        val remaining = getRemainingRefreshCount()

        runOnUiThread {
            showToast(getString(R.string.refreshing_with_remaining, remaining))
            refreshBackgroundImage(true)
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun canRefreshToday(): Boolean {
        val prefs = getSharedPreferences(REFRESH_PREF_NAME, MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(REFRESH_DATE_KEY, "")
        val count = prefs.getInt(REFRESH_COUNT_KEY, 0)

        if (today != savedDate) {
            prefs.edit {
                putString(REFRESH_DATE_KEY, today)
                    .putInt(REFRESH_COUNT_KEY, 0)
            }
            return true
        }
        return count < MAX_DAILY_REFRESH
    }

    private fun getRemainingRefreshCount(): Int {
        val prefs = getSharedPreferences(REFRESH_PREF_NAME, MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(REFRESH_DATE_KEY, "")
        val count = prefs.getInt(REFRESH_COUNT_KEY, 0)

        return if (today == savedDate) 0.coerceAtLeast(MAX_DAILY_REFRESH - count) else MAX_DAILY_REFRESH
    }

    private fun incrementRefreshCount() {
        val prefs = getSharedPreferences(REFRESH_PREF_NAME, MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(REFRESH_DATE_KEY, "")
        val count = if (today == savedDate) prefs.getInt(REFRESH_COUNT_KEY, 0) else 0

        prefs.edit {
            putString(REFRESH_DATE_KEY, today)
                .putInt(REFRESH_COUNT_KEY, count + 1)
        }
    }

    // Background Receiver Methods

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBackgroundReceiver() {
        backgroundImageRefreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BackgroundSource.ACTION_REFRESH == intent.action) {
                    refreshBackgroundImage(false)
                }
            }
        }

        val filter = IntentFilter(BackgroundSource.ACTION_REFRESH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backgroundImageRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(backgroundImageRefreshReceiver, filter)
        }
    }

    private fun unregisterBackgroundReceiver() {
        if (backgroundImageRefreshReceiver != null) {
            try {
                unregisterReceiver(backgroundImageRefreshReceiver)
            } catch (e: IllegalArgumentException) {
                LimeLog.warning("Failed to unregister background receiver: " + e.message)
            }
        }
    }

    /**
     * Live-reload the background when any related preference changes (ListPreference
     * in Settings, EditTextPreference for custom API URL, or the local path written
     * by LocalImagePickerPreference). This replaces the previous onResume-snapshot
     * hack — as soon as the user confirms a change in Settings, we react.
     */
    private fun registerBackgroundPrefsListener() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        backgroundPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                BackgroundSource.KEY_SOURCE,
                BackgroundSource.KEY_API_URL,
                BackgroundSource.KEY_LOCAL_PATH -> loadBackgroundImage()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(backgroundPrefsListener)
    }

    private fun unregisterBackgroundPrefsListener() {
        backgroundPrefsListener?.let {
            PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        backgroundPrefsListener = null
    }

    // Computer Manager Methods

    private fun startComputerUpdates() {
        val binder = managerBinder ?: return
        if (runningPolling || !inForeground) return

        freezeUpdates = false
        pollingCollectJob?.cancel()
        pollingCollectJob = uiScope.launch {
            binder.computerUpdates
                .filter { !freezeUpdates }
                .collect { details ->
                    updateComputer(details)
                    if (details.pairState == PairState.PAIRED) {
                        shortcutHelper.createAppViewShortcutForOnlineHost(details)
                    }
                }
        }
        binder.startPolling()
        runningPolling = true
    }

    private fun stopComputerUpdates(wait: Boolean) {
        if (managerBinder == null || !runningPolling) return

        freezeUpdates = true
        pollingCollectJob?.cancel()
        pollingCollectJob = null
        managerBinder?.stopPolling()

        if (wait) {
            managerBinder?.waitForPollingStopped()
        }
        runningPolling = false
    }

    private fun debouncedNotifyDataSetChanged() {
        if (pendingRefreshRunnable != null) {
            refreshHandler.removeCallbacks(pendingRefreshRunnable!!)
        }

        pendingRefreshRunnable = Runnable {
            // FLIP 重排动画：对当前可见 item 在 notifyDataSetChanged 前后做位置 diff，
            // 把"被搬走"的 view 先视觉上拉回旧位置，再 animate 到新位置，O(可见数) 开销。
            animateReorderAndNotify()
            pendingRefreshRunnable = null
        }

        refreshHandler.postDelayed(pendingRefreshRunnable!!, REFRESH_DEBOUNCE_DELAY)
    }

    private fun animateReorderAndNotify() {
        val listView = pcListView
        if (listView == null || isFirstLoad) {
            pcGridAdapter.notifyDataSetChanged()
            return
        }

        // 1. 快照可见 item 的顺序与位置：UUID -> (left, top)（含 translation 兼容上一轮 FLIP）
        // 注意：GridView 同行交换 top 不变，必须同时记录 left 才能感知水平位移
        // ⚠️ UUID 必须从 view.tag 反查（pc_grid_item 在 getView 中绑定），不能调 adapter.getItem(pos)
        //     —— 调用方在 resort() 之后才进来，此时 itemList 已经是 NEW 顺序，但可见 children 还在旧布局
        val firstVisible = listView.firstVisiblePosition
        val visibleCount = listView.childCount
        val oldPositions = HashMap<String, IntArray>(visibleCount)
        val oldOrder = ArrayList<String>(visibleCount)
        for (i in 0 until visibleCount) {
            val v = listView.getChildAt(i)
            val uuid = v.getTag(R.id.grid_text) as? String ?: continue
            oldPositions[uuid] = intArrayOf(
                v.left + v.translationX.toInt(),
                v.top + v.translationY.toInt()
            )
            oldOrder.add(uuid)
        }

        pcGridAdapter.notifyDataSetChanged()

        if (oldOrder.isEmpty()) return

        // 早退：可见区 UUID 顺序未变 → 跳过 FLIP（绝大多数轮询通知都走这条路）
        var orderChanged = false
        for (i in oldOrder.indices) {
            val pos = firstVisible + i
            val item = pcGridAdapter.getItem(pos) as? ComputerObject
            if (item?.details?.uuid != oldOrder[i]) {
                orderChanged = true
                break
            }
        }
        if (!orderChanged) return

        // 2. 下一帧布局完成时：把每个仍可见的旧 UUID 视图先拉到旧位置，再动画归零
        listView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                listView.viewTreeObserver.removeOnPreDrawListener(this)
                for (i in 0 until listView.childCount) {
                    val view = listView.getChildAt(i)
                    val uuid = view.getTag(R.id.grid_text) as? String ?: continue
                    val old = oldPositions[uuid] ?: continue
                    val dx = old[0] - view.left
                    val dy = old[1] - view.top
                    if (dx == 0 && dy == 0) continue
                    // 取消上一轮 FLIP 动画，避免叠加导致跳变
                    view.animate().cancel()
                    view.translationX = dx.toFloat()
                    view.translationY = dy.toFloat()
                    view.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(280L)
                        .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                        .start()
                }
                return true
            }
        })
    }

    private fun updateComputer(details: ComputerDetails) {
        if (PcGridAdapter.ADD_COMPUTER_UUID == details.uuid) return

        val existingEntry = findComputerByUuid(details.uuid)

        if (existingEntry != null) {
            existingEntry.details = details
            pcGridAdapter.resort()
        } else {
            addNewComputer(details)
        }

        debouncedNotifyDataSetChanged()
    }

    private fun findComputerByUuid(uuid: String?): ComputerObject? {
        for (i in 0 until pcGridAdapter.rawCount) {
            val computer = pcGridAdapter.getRawItem(i)
            if (!PcGridAdapter.isAddComputerCard(computer) && uuid != null && uuid == computer.details.uuid) {
                return computer
            }
        }
        return null
    }

    private fun addNewComputer(details: ComputerDetails) {
        val newComputer = ComputerObject(details)
        pcGridAdapter.addComputer(newComputer)

        val isUnpaired = details.state == ComputerDetails.State.ONLINE
                && details.pairState == PairState.NOT_PAIRED

        if (isUnpaired && !pcGridAdapter.isShowUnpairedDevices()) {
            pcGridAdapter.setShowUnpairedDevices(true)
            showToast(getString(R.string.new_unpaired_device_shown))
        }

        noPcFoundLayout?.visibility = View.INVISIBLE

        if (pcListView != null && !isFirstLoad) {
            pcListView?.scheduleLayoutAnimation()
        }
    }

    private fun removeComputer(details: ComputerDetails) {
        if (PcGridAdapter.ADD_COMPUTER_UUID == details.uuid) return

        managerBinder?.removeComputer(details)
        DiskAssetLoader(this).deleteAssetsForComputer(details.uuid!!)

        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit {
                    remove(details.uuid)
                }

        for (i in 0 until pcGridAdapter.rawCount) {
            val computer = pcGridAdapter.getRawItem(i)
            if (!PcGridAdapter.isAddComputerCard(computer) && details == computer.details) {
                shortcutHelper.disableComputerShortcut(details, getString(R.string.scut_deleted_pc))
                pcGridAdapter.removeComputer(computer)
                pcGridAdapter.notifyDataSetChanged()

                if (countRealComputers() == 0) {
                    noPcFoundLayout?.visibility = View.VISIBLE
                }
                break
            }
        }
    }

    private fun countRealComputers(): Int {
        var count = 0
        for (i in 0 until pcGridAdapter.rawCount) {
            if (!PcGridAdapter.isAddComputerCard(pcGridAdapter.getRawItem(i))) {
                count++
            }
        }
        return count
    }

    private fun addAddComputerCard() {
        for (i in 0 until pcGridAdapter.rawCount) {
            if (PcGridAdapter.isAddComputerCard(pcGridAdapter.getRawItem(i))) {
                return
            }
        }

        val addDetails = ComputerDetails()
        addDetails.uuid = PcGridAdapter.ADD_COMPUTER_UUID
        try {
            addDetails.name = getString(R.string.title_add_pc)
        } catch (e: Exception) {
            addDetails.name = "添加电脑"
        }
        addDetails.state = ComputerDetails.State.UNKNOWN

        pcGridAdapter.addComputer(ComputerObject(addDetails))
        pcGridAdapter.notifyDataSetChanged()

        noPcFoundLayout?.visibility = View.INVISIBLE
    }

    // Toggle Unpaired Hosts

    private fun toggleUnpairedDevices() {
        val newState = !pcGridAdapter.isShowUnpairedDevices()
        pcGridAdapter.setShowUnpairedDevices(newState)
        showToast(if (newState) getString(R.string.unpaired_devices_shown) else getString(R.string.unpaired_devices_hidden))
    }

    // Scene Configuration Methods

    private data class SceneConfiguration(
            val width: Int,
            val height: Int,
            val fps: Int,
            val bitrate: Int,
            val enableAdaptiveBitrate: Boolean,
            val abrMode: String,
            val videoFormat: PreferenceConfiguration.FormatOption,
            val framePacing: Int,
            val stretchVideo: Boolean,
            val enableSops: Boolean,
            val unlockFps: Boolean,
            val reduceRefreshRate: Boolean,
            val fullRange: Boolean,
            val enableHdr: Boolean,
            val enableHdrHighBrightness: Boolean,
            val hdrMode: Int,
            val enablePerfOverlay: Boolean,
            val perfOverlayLocked: Boolean,
            val perfOverlayBgOpacity: Int,
            val perfOverlayOrientation: PreferenceConfiguration.PerfOverlayOrientation,
            val perfOverlayPosition: PreferenceConfiguration.PerfOverlayPosition
    ) {
        fun applyTo(prefs: PreferenceConfiguration): PreferenceConfiguration {
            prefs.width = width
            prefs.height = height
            prefs.fps = fps
            prefs.bitrate = bitrate
            prefs.enableAdaptiveBitrate = enableAdaptiveBitrate
            prefs.abrMode = abrMode
            prefs.videoFormat = videoFormat
            prefs.framePacing = framePacing
            prefs.stretchVideo = stretchVideo
            prefs.enableSops = enableSops
            prefs.unlockFps = unlockFps
            prefs.reduceRefreshRate = reduceRefreshRate
            prefs.fullRange = fullRange
            prefs.enableHdr = enableHdr
            prefs.enableHdrHighBrightness = enableHdrHighBrightness
            prefs.hdrMode = hdrMode
            prefs.enablePerfOverlay = enablePerfOverlay
            prefs.perfOverlayLocked = perfOverlayLocked
            prefs.perfOverlayBgOpacity = perfOverlayBgOpacity
            prefs.perfOverlayOrientation = perfOverlayOrientation
            prefs.perfOverlayPosition = perfOverlayPosition
            return prefs
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("width", width)
                put("height", height)
                put("fps", fps)
                put("bitrate", bitrate)
                put("enableAdaptiveBitrate", enableAdaptiveBitrate)
                put("abrMode", abrMode)
                put("videoFormat", videoFormat.name)
                put("framePacing", framePacing)
                put("stretchVideo", stretchVideo)
                put("enableSops", enableSops)
                put("unlockFps", unlockFps)
                put("reduceRefreshRate", reduceRefreshRate)
                put("fullRange", fullRange)
                put("enableHdr", enableHdr)
                put("enableHdrHighBrightness", enableHdrHighBrightness)
                put("hdrMode", hdrMode)
                put("enablePerfOverlay", enablePerfOverlay)
                put("perfOverlayLocked", perfOverlayLocked)
                put("perfOverlayBgOpacity", perfOverlayBgOpacity)
                put("perfOverlayOrientation", perfOverlayOrientation.name)
                put("perfOverlayPosition", perfOverlayPosition.name)
            }
        }

        companion object {
            fun fromPreferences(prefs: PreferenceConfiguration): SceneConfiguration {
                return SceneConfiguration(
                        width = prefs.width,
                        height = prefs.height,
                        fps = prefs.fps,
                        bitrate = prefs.bitrate,
                        enableAdaptiveBitrate = prefs.enableAdaptiveBitrate,
                        abrMode = prefs.abrMode,
                        videoFormat = prefs.videoFormat,
                        framePacing = prefs.framePacing,
                        stretchVideo = prefs.stretchVideo,
                        enableSops = prefs.enableSops,
                        unlockFps = prefs.unlockFps,
                        reduceRefreshRate = prefs.reduceRefreshRate,
                        fullRange = prefs.fullRange,
                        enableHdr = prefs.enableHdr,
                        enableHdrHighBrightness = prefs.enableHdrHighBrightness,
                        hdrMode = prefs.hdrMode,
                        enablePerfOverlay = prefs.enablePerfOverlay,
                        perfOverlayLocked = prefs.perfOverlayLocked,
                        perfOverlayBgOpacity = prefs.perfOverlayBgOpacity,
                        perfOverlayOrientation = prefs.perfOverlayOrientation,
                        perfOverlayPosition = prefs.perfOverlayPosition)
            }

            fun fromJson(json: JSONObject, fallback: PreferenceConfiguration): SceneConfiguration {
                return SceneConfiguration(
                        width = json.optInt("width", fallback.width),
                        height = json.optInt("height", fallback.height),
                        fps = json.optInt("fps", fallback.fps),
                        bitrate = json.optInt("bitrate", fallback.bitrate),
                        enableAdaptiveBitrate = json.optBoolean("enableAdaptiveBitrate", fallback.enableAdaptiveBitrate),
                        abrMode = json.optString("abrMode", fallback.abrMode),
                        videoFormat = parseEnum(json.optString("videoFormat", fallback.videoFormat.name), fallback.videoFormat),
                        framePacing = json.optInt("framePacing", fallback.framePacing),
                        stretchVideo = json.optBoolean("stretchVideo", fallback.stretchVideo),
                        enableSops = json.optBoolean("enableSops", fallback.enableSops),
                        unlockFps = json.optBoolean("unlockFps", fallback.unlockFps),
                        reduceRefreshRate = json.optBoolean("reduceRefreshRate", fallback.reduceRefreshRate),
                        fullRange = json.optBoolean("fullRange", fallback.fullRange),
                        enableHdr = json.optBoolean("enableHdr", fallback.enableHdr),
                        enableHdrHighBrightness = json.optBoolean("enableHdrHighBrightness", fallback.enableHdrHighBrightness),
                        hdrMode = json.optInt("hdrMode", fallback.hdrMode),
                        enablePerfOverlay = json.optBoolean("enablePerfOverlay", fallback.enablePerfOverlay),
                        perfOverlayLocked = json.optBoolean("perfOverlayLocked", fallback.perfOverlayLocked),
                        perfOverlayBgOpacity = json.optInt("perfOverlayBgOpacity", fallback.perfOverlayBgOpacity),
                        perfOverlayOrientation = parseEnum(json.optString("perfOverlayOrientation", fallback.perfOverlayOrientation.name), fallback.perfOverlayOrientation),
                        perfOverlayPosition = parseEnum(json.optString("perfOverlayPosition", fallback.perfOverlayPosition.name), fallback.perfOverlayPosition))
            }

            private inline fun <reified T : Enum<T>> parseEnum(value: String, fallback: T): T {
                return runCatching { enumValueOf<T>(value.uppercase(Locale.US)) }.getOrDefault(fallback)
            }
        }
    }

    private fun initSceneButtons() {
        try {
            val sceneButtonIds = intArrayOf(R.id.scene1Btn, R.id.scene2Btn, R.id.scene3Btn, R.id.scene4Btn, R.id.scene5Btn)

            for (i in sceneButtonIds.indices) {
                val sceneNumber = i + 1
                val btn = findViewById<ImageButton>(sceneButtonIds[i])

                if (btn == null) {
                    LimeLog.warning("Scene button $sceneNumber not found!")
                    continue
                }

                btn.setOnClickListener { applySceneConfiguration(sceneNumber) }
                btn.setOnLongClickListener {
                    showSaveConfirmationDialog(sceneNumber)
                    true
                }
            }
            updateSceneButtonStates()
        } catch (e: Exception) {
            LimeLog.warning("Scene init failed: $e")
        }
    }

    private fun updateSceneButtonStates() {
        val sceneButtonIds = intArrayOf(R.id.scene1Btn, R.id.scene2Btn, R.id.scene3Btn, R.id.scene4Btn, R.id.scene5Btn)
        val prefs = getSharedPreferences(SCENE_PREF_NAME, MODE_PRIVATE)

        for (i in sceneButtonIds.indices) {
            val sceneNumber = i + 1
            val btn = findViewById<ImageButton>(sceneButtonIds[i]) ?: continue
            val isConfigured = prefs.contains(SCENE_KEY_PREFIX + sceneNumber)
            val isActive = activeSceneNumber == sceneNumber

            btn.alpha = when {
                isActive -> 1.0f
                isConfigured -> 0.9f
                else -> 0.68f
            }
            btn.imageTintList = ColorStateList.valueOf(when {
                isActive -> Color.WHITE
                isConfigured -> Color.argb(230, 255, 255, 255)
                else -> Color.argb(190, 255, 255, 255)
            })
            btn.contentDescription = getString(
                    if (isConfigured) R.string.scene_configured_content_description else R.string.scene_empty_content_description,
                    sceneNumber)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun applySceneConfiguration(sceneNumber: Int) {
        try {
            val prefs = getSharedPreferences(SCENE_PREF_NAME, MODE_PRIVATE)
            val configJson = prefs.getString(SCENE_KEY_PREFIX + sceneNumber, null)

            if (configJson == null) {
                showUnconfiguredSceneDialog(sceneNumber)
                return
            }

            val configPrefs = PreferenceConfiguration.readPreferences(this)
            SceneConfiguration.fromJson(JSONObject(configJson), configPrefs).applyTo(configPrefs)

            if (!configPrefs.writeScenePreferences(this)) {
                showToast(getString(R.string.config_save_failed))
                return
            }

            pcGridAdapter.updateLayoutWithPreferences(this, configPrefs)
            activeSceneNumber = sceneNumber
            updateSceneButtonStates()
            showToast(getString(R.string.scene_config_applied, sceneNumber, configPrefs.width, configPrefs.height,
                    configPrefs.fps, configPrefs.bitrate / 1000.0, configPrefs.videoFormat.toString(),
                    if (configPrefs.enableHdr) "On" else "Off"))

        } catch (e: Exception) {
            LimeLog.warning("Scene apply failed: $e")
            showToast(getString(R.string.config_apply_failed))
        }
    }

    private fun showUnconfiguredSceneDialog(sceneNumber: Int) {
        AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(getString(R.string.scene_empty_title, sceneNumber))
                .setMessage(getString(R.string.scene_empty_message, sceneNumber))
                .setPositiveButton(R.string.save_current_config_to_scene) { _, _ -> saveCurrentConfiguration(sceneNumber) }
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show()
                .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun showSaveConfirmationDialog(sceneNumber: Int) {
        AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(getString(R.string.save_to_scene, sceneNumber))
                .setMessage(getString(R.string.overwrite_scene_config, sceneNumber))
                .setPositiveButton(R.string.dialog_button_save) { _, _ -> saveCurrentConfiguration(sceneNumber) }
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show()
                .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun saveCurrentConfiguration(sceneNumber: Int) {
        try {
            val config = SceneConfiguration
                    .fromPreferences(PreferenceConfiguration.readPreferences(this))
                    .toJson()

            getSharedPreferences(SCENE_PREF_NAME, MODE_PRIVATE)
                    .edit {
                        putString(SCENE_KEY_PREFIX + sceneNumber, config.toString())
                    }

            activeSceneNumber = sceneNumber
            updateSceneButtonStates()
            showToast(getString(R.string.scene_saved_successfully, sceneNumber))
        } catch (e: JSONException) {
            showToast(getString(R.string.config_save_failed))
        }
    }

    // PC Actions

    private fun doPair(computer: ComputerDetails) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            showToast(getString(R.string.pair_pc_offline))
            return
        }
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        showToast(getString(R.string.pairing))
        uiScope.launch {
            var message: String?
            var success = false

            try {
                stopComputerUpdates(true)

                val result = withContext(Dispatchers.IO) {
                    val httpConn = NvHTTP(
                            ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort,
                            (managerBinder?.getUniqueId() ?: ""),
                            clientName,
                            computer.serverCert,
                            PlatformBinding.getCryptoProvider(this@PcView)
                    )

                    if (httpConn.getPairState() == PairState.PAIRED) {
                        return@withContext Triple<String?, Boolean, Pair<String, String>?>(null, true, null)
                    }

                    val pinStr = PairingManager.generatePinString()
                    withContext(Dispatchers.Main) {
                        Dialog.displayDialog(this@PcView,
                                getString(R.string.pair_pairing_title),
                                getString(R.string.pair_pairing_msg) + " " + pinStr + "\n\n" + getString(R.string.pair_pairing_help),
                                false)
                    }

                    val pm = httpConn.pairingManager
                    val pairResult = pm.pair(httpConn.getServerInfo(true), pinStr)

                    when (pairResult.state) {
                        PairState.PIN_WRONG ->
                            Triple<String?, Boolean, Pair<String, String>?>(getString(R.string.pair_incorrect_pin), false, null)
                        PairState.FAILED ->
                            Triple<String?, Boolean, Pair<String, String>?>(
                                if (computer.runningGameId != 0) getString(R.string.pair_pc_ingame) else getString(R.string.pair_fail),
                                false, null
                            )
                        PairState.ALREADY_IN_PROGRESS ->
                            Triple<String?, Boolean, Pair<String, String>?>(getString(R.string.pair_already_in_progress), false, null)
                        PairState.PAIRED -> {
                            managerBinder?.getComputer(computer.uuid!!)!!.serverCert = pm.pairedCert
                            Triple<String?, Boolean, Pair<String, String>?>(null, true, computer.uuid!! to pairResult.pairName)
                        }
                        else -> Triple<String?, Boolean, Pair<String, String>?>(null, false, null)
                    }
                }

                message = result.first
                success = result.second
                result.third?.let { (uuid, pairName) ->
                    getSharedPreferences("pair_name_map", MODE_PRIVATE)
                            .edit {
                                putString(uuid, pairName)
                            }
                    managerBinder?.invalidateStateForComputer(uuid)
                }
            } catch (e: UnknownHostException) {
                message = getString(R.string.error_unknown_host)
            } catch (e: FileNotFoundException) {
                message = getString(R.string.error_404)
            } catch (e: InterruptedException) {
                message = getString(R.string.pair_fail)
            } catch (e: XmlPullParserException) {
                message = e.message
            } catch (e: IOException) {
                message = e.message
            } finally {
                Dialog.closeDialogs()
            }

            if (message != null) {
                showToast(message)
            }
            if (success) {
                doAppList(computer, newlyPaired = true, showHiddenGames = false)
            } else {
                startComputerUpdates()
            }
        }
    }

    private fun showAddComputerDialog() {
        val items = arrayOf(
            getString(R.string.addpc_manual),
            getString(R.string.addpc_qr_scan)
        )
        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(getString(R.string.title_add_pc_choose))
            .setItems(items) { _, which ->
                if (which == 0) {
                    startActivity(Intent(this, AddComputerManually::class.java))
                } else {
                    startQrScan()
                }
            }
            .create()
        dialog.show()
        AppDialogStyler.applySystemChoiceList(dialog, this)
    }

    private fun startQrScan() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt(getString(R.string.qr_scan_prompt))
        integrator.setBeepEnabled(false)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }

    private fun handleQrPairResult(url: String) {
        val uri = url.toUri()
        if ("moonlight" != uri.scheme || "pair" != uri.host) {
            showToast(getString(R.string.qr_invalid_code))
            return
        }

        val host = uri.getQueryParameter("host")
        val portStr = uri.getQueryParameter("port")
        val pin = uri.getQueryParameter("pin")

        if (host == null || pin == null) {
            showToast(getString(R.string.qr_invalid_code))
            return
        }

        var port = NvHTTP.DEFAULT_HTTP_PORT
        if (portStr != null) {
            try { port = portStr.toInt() } catch (ignored: NumberFormatException) {}
        }

        showToast(getString(R.string.qr_pairing))
        val finalPort = port
        uiScope.launch {
            var message: String?
            var success = false
            var pairedComputer: ComputerDetails? = null

            try {
                stopComputerUpdates(true)

                val result = withContext(Dispatchers.IO) {
                    // Add the computer first
                    val addDetails = ComputerDetails()
                    addDetails.manualAddress = ComputerDetails.AddressTuple(host, finalPort)
                    val added = managerBinder?.addComputerBlocking(addDetails) == true
                    if (!added) {
                        return@withContext QrPairResult(getString(R.string.addpc_fail), false, null, null)
                    }

                    // addComputerBlocking fills addDetails in-place (uuid, httpsPort, etc.)
                    var computer = managerBinder?.getComputer(addDetails.uuid!!)
                    if (computer == null) {
                        computer = addDetails
                    }

                    val httpConn = NvHTTP(
                        ServerHelper.getCurrentAddressFromComputer(computer),
                        computer.httpsPort,
                        (managerBinder?.getUniqueId() ?: ""),
                        clientName,
                        computer.serverCert,
                        PlatformBinding.getCryptoProvider(this@PcView)
                    )

                    if (httpConn.getPairState() == PairState.PAIRED) {
                        return@withContext QrPairResult(null, true, computer, null)
                    }

                    val pm = httpConn.pairingManager
                    val pairResult = pm.pair(httpConn.getServerInfo(true), pin)
                    when (pairResult.state) {
                        PairState.PIN_WRONG ->
                            QrPairResult(getString(R.string.pair_incorrect_pin), false, null, null)
                        PairState.FAILED ->
                            QrPairResult(getString(R.string.pair_fail), false, null, null)
                        PairState.ALREADY_IN_PROGRESS ->
                            QrPairResult(getString(R.string.pair_already_in_progress), false, null, null)
                        PairState.PAIRED -> {
                            managerBinder?.getComputer(computer.uuid!!)!!.serverCert = pm.pairedCert
                            QrPairResult(null, true, computer, computer.uuid!! to pairResult.pairName)
                        }
                        else -> QrPairResult(null, false, null, null)
                    }
                }

                message = result.message
                success = result.success
                pairedComputer = result.computer
                result.saveName?.let { (uuid, pairName) ->
                    getSharedPreferences("pair_name_map", MODE_PRIVATE)
                        .edit {
                            putString(uuid, pairName)
                        }
                    managerBinder?.invalidateStateForComputer(uuid)
                }
            } catch (e: Exception) {
                message = e.message
            }

            if (message != null) {
                showToast(message)
            }
            if (success) {
                showToast(getString(R.string.qr_pair_success))
                if (pairedComputer != null) {
                    doAppList(pairedComputer, newlyPaired = true, showHiddenGames = false)
                } else {
                    startComputerUpdates()
                }
            } else {
                startComputerUpdates()
            }
        }
    }

    private data class QrPairResult(
        val message: String?,
        val success: Boolean,
        val computer: ComputerDetails?,
        val saveName: Pair<String, String>?
    )

    private fun findComputerByAddress(host: String): ComputerDetails? {
        if (managerBinder == null) return null
        for (i in 0 until pcGridAdapter.count) {
            val obj = pcGridAdapter.getItem(i) as ComputerObject
            if (PcGridAdapter.isAddComputerCard(obj)) continue
            val d = obj.details
            if (d.manualAddress != null && host == d.manualAddress?.address) return d
            if (d.localAddress != null && host == d.localAddress?.address) return d
            if (d.remoteAddress != null && host == d.remoteAddress?.address) return d
        }
        return null
    }

    private fun doWakeOnLan(computer: ComputerDetails) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            showToast(getString(R.string.wol_pc_online))
            return
        }
        if (computer.macAddress == null) {
            showToast(getString(R.string.wol_no_mac))
            return
        }

        uiScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    WakeOnLanSender.sendWolPacket(computer)
                    getString(R.string.wol_waking_msg)
                } catch (e: IOException) {
                    getString(R.string.wol_fail)
                }
            }
            showToast(message)
        }
    }

    private fun doUnpair(computer: ComputerDetails) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            showToast(getString(R.string.error_pc_offline))
            return
        }
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        showToast(getString(R.string.unpairing))
        val binder = managerBinder!!
        uiScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val httpConn = NvHTTP(
                            ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort,
                            binder.getUniqueId(),
                            clientName,
                            computer.serverCert,
                            PlatformBinding.getCryptoProvider(this@PcView)
                    )

                    if (httpConn.getPairState() == PairState.PAIRED) {
                        httpConn.unpair()
                        if (httpConn.getPairState() == PairState.NOT_PAIRED) {
                            binder.markComputerNotPaired(computer, "Local unpair")
                            getString(R.string.unpair_success)
                        } else {
                            getString(R.string.unpair_fail)
                        }
                    } else {
                        getString(R.string.unpair_error)
                    }
                } catch (e: UnknownHostException) {
                    getString(R.string.error_unknown_host)
                } catch (e: FileNotFoundException) {
                    getString(R.string.error_404)
                } catch (e: XmlPullParserException) {
                    e.message ?: getString(R.string.unpair_fail)
                } catch (e: IOException) {
                    e.message ?: getString(R.string.unpair_fail)
                } catch (e: InterruptedException) {
                    getString(R.string.error_interrupted)
                }
            }
            showToast(message)
        }
    }

    private fun doAppList(computer: ComputerDetails, newlyPaired: Boolean, showHiddenGames: Boolean) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            showToast(getString(R.string.error_pc_offline))
            return
        }
        val binder = managerBinder
        if (binder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        val target = prepareComputerWithAddress(computer)
        if (target == null || target.activeAddress == null) {
            showToast(getString(R.string.error_pc_offline))
            return
        }

        if (PairStatePreflight.hasTrustedPairState(target)) {
            openAppList(target, newlyPaired, showHiddenGames)
            return
        }

        val spinner = SpinnerDialog.displayDialog(this,
                resources.getString(R.string.applist_refresh_title),
                resources.getString(R.string.applist_refresh_msg),
                false)

        uiScope.launch {
            val isNotPaired = PairStatePreflight.isConfirmedNotPaired(target, binder, "Opening app list")

            spinner.dismiss()
            if (isFinishing || isDestroyed) {
                return@launch
            }

            if (isNotPaired) {
                showToast(getString(R.string.scut_not_paired))
            } else {
                openAppList(target, newlyPaired, showHiddenGames)
            }
        }
    }

    private fun openAppList(computer: ComputerDetails, newlyPaired: Boolean, showHiddenGames: Boolean) {
        val i = Intent(this, AppView::class.java)
        i.putExtra(AppView.NAME_EXTRA, computer.name)
        i.putExtra(AppView.UUID_EXTRA, computer.uuid)
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired)
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames)

        if (computer.activeAddress != null) {
            i.putExtra(AppView.SELECTED_ADDRESS_EXTRA, computer.activeAddress?.address)
            i.putExtra(AppView.SELECTED_PORT_EXTRA, computer.activeAddress?.port)
        }

        startActivity(i)
    }

    private fun doSecondaryScreenStream(computer: ComputerDetails) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            showToast(getString(R.string.error_pc_offline))
            return
        }
        if (computer.vddCapabilityVersion == 0) {
            showToast(getString(R.string.error_vdd_unsupported))
            return
        }
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        quickStartStreamWithScreenMode(computer, null, true, 4)
    }

    // Quick Start Stream Methods

    private fun handleAvatarClick(computer: ComputerDetails, itemView: View) {
        quickStartStream(computer, itemView, false)
    }

    private fun quickStartStream(computer: ComputerDetails, itemView: View?, isSecondaryScreen: Boolean) {
        if (computer.state != ComputerDetails.State.ONLINE || computer.pairState != PairState.PAIRED) {
            if (itemView != null) {
                showHostActionSheet(computer)
            }
            return
        }

        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        uiScope.launch {
            val targetApp = withContext(Dispatchers.IO) {
                if (computer.runningGameId != 0)
                    getNvAppById(computer.runningGameId, computer.uuid!!)
                else
                    getDefaultQuickStartApp(computer)
            }

            if (targetApp == null) {
                fallbackToAppList(computer)
                return@launch
            }

            val targetComputer = prepareComputerWithAddress(computer)
            if (targetComputer == null) {
                showToast(getString(R.string.error_pc_offline))
                return@launch
            }
            if (targetComputer.hasMultipleLanAddresses()) {
                showAddressSelectionDialog(targetComputer)
                return@launch
            }
            if (PairStatePreflight.isConfirmedNotPaired(targetComputer, managerBinder!!, "Quick start")) {
                showToast(getString(R.string.scut_not_paired))
                return@launch
            }

            ServerHelper.doStart(
                this@PcView,
                targetApp,
                targetComputer,
                managerBinder!!
            )
        }
    }

    private fun quickStartStreamWithScreenMode(computer: ComputerDetails, itemView: View?, isSecondaryScreen: Boolean, screenMode: Int) {
        if (computer.state != ComputerDetails.State.ONLINE || computer.pairState != PairState.PAIRED) {
            if (itemView != null) {
                showHostActionSheet(computer)
            }
            return
        }

        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        uiScope.launch {
            val targetApp = withContext(Dispatchers.IO) {
                if (computer.runningGameId != 0)
                    getNvAppById(computer.runningGameId, computer.uuid!!)
                else
                    getDefaultQuickStartApp(computer)
            }

            if (targetApp == null) {
                fallbackToAppList(computer)
                return@launch
            }

            val targetComputer = prepareComputerWithAddress(computer)
            if (targetComputer == null) {
                showToast(getString(R.string.error_pc_offline))
                return@launch
            }
            if (targetComputer.hasMultipleLanAddresses()) {
                showAddressSelectionDialog(targetComputer)
                return@launch
            }
            if (PairStatePreflight.isConfirmedNotPaired(targetComputer, managerBinder!!, "Secondary screen start")) {
                showToast(getString(R.string.scut_not_paired))
                return@launch
            }

            val intent = ServerHelper.createStartIntent(
                this@PcView,
                targetApp,
                targetComputer,
                managerBinder!!,
                lastSettings = null,
                useVdd = if (isSecondaryScreen) true else null
            )
            if (screenMode != -1) {
                intent.putExtra(Game.EXTRA_SCREEN_COMBINATION_MODE, screenMode)
            }
            startActivity(intent)
        }
    }

    private fun prepareComputerWithAddress(computer: ComputerDetails): ComputerDetails? {
        val temp = ComputerDetails(computer)
        if (temp.activeAddress == null) {
            val best = temp.selectBestAddress() ?: return null
            temp.activeAddress = best
        }
        return temp
    }

    private fun fallbackToAppList(computer: ComputerDetails) {
        runOnUiThread {
            showToast(getString(R.string.quick_start_load_app_list_first))
            val target = prepareComputerWithAddress(computer)
            doAppList(target ?: computer, newlyPaired = false, showHiddenGames = false)
        }
    }

    // App Cache Methods

    private fun getAppListFromCache(uuid: String): List<NvApp>? {
        try {
            val rawAppList = CacheHelper.readInputStreamToString(
                    CacheHelper.openCacheFileForInput(cacheDir, "applist", uuid))
            return if (rawAppList.isEmpty()) null else NvHTTP.getAppListByReader(StringReader(rawAppList))
        } catch (e: IOException) {
            LimeLog.warning("Failed to read app list from cache: " + e.message)
            return null
        } catch (e: XmlPullParserException) {
            LimeLog.warning("Failed to read app list from cache: " + e.message)
            return null
        }
    }

    private fun getDefaultQuickStartApp(computer: ComputerDetails): NvApp? {
        if (computer.supportsDesktopSpecialApp) {
            return NvApp(NvApp.DESKTOP_APP_NAME, NvApp.DESKTOP_APP_ID, false)
        }

        val appList = getAppListFromCache(computer.uuid!!)
        return if (!appList.isNullOrEmpty()) appList[0] else null
    }

    private fun getNvAppById(appId: Int, uuid: String): NvApp? {
        val appList = getAppListFromCache(uuid)
        if (appList != null) {
            for (app in appList) {
                if (app.appId == appId) {
                    AppCacheManager(this).saveAppInfo(uuid, app)
                    return app
                }
            }
        }
        return AppCacheManager(this).getAppInfo(uuid, appId)
    }

    private fun restoreLastSession() {
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        var target: ComputerDetails? = null
        for (i in 0 until pcGridAdapter.rawCount) {
            val computer = pcGridAdapter.getRawItem(i)
            if (computer.details.state == ComputerDetails.State.ONLINE
                    && computer.details.pairState == PairState.PAIRED
                    && computer.details.runningGameId != 0) {
                target = computer.details
                break
            }
        }

        if (target == null) {
            showToast(getString(R.string.no_online_computer_with_running_game))
            return
        }

        var app = getNvAppById(target.runningGameId, target.uuid!!)
        if (app == null) {
            app = NvApp("app", target.runningGameId, false)
        }

        showToast(getString(R.string.restoring_session, target.name))
        uiScope.launch {
            if (PairStatePreflight.isConfirmedNotPaired(target, managerBinder!!, "Restore session")) {
                showToast(getString(R.string.scut_not_paired))
                return@launch
            }
            ServerHelper.doStart(this@PcView, app, target, managerBinder!!, forceResumeCurrentSession = true)
        }
    }

    private fun showAddressSelectionDialog(computer: ComputerDetails) {
        val dialog = AddressSelectionDialog(this, computer) { address ->
            val temp = ComputerDetails(computer)
            temp.activeAddress = address
            doAppList(temp, newlyPaired = false, showHiddenGames = false)
        }
        dialog.show()
    }

    // Host Action Sheet

    private fun showHostActionSheet(details: ComputerDetails) {
        stopComputerUpdates(false)

        val status = when (details.state) {
            ComputerDetails.State.ONLINE -> getString(R.string.pcview_menu_header_online)
            ComputerDetails.State.OFFLINE -> getString(R.string.pcview_menu_header_offline)
            else -> getString(R.string.pcview_menu_header_unknown)
        }
        AppActionSheet.show(
            context = this,
            title = details.name.orEmpty(),
            subtitle = status,
            activeStatus = details.state == ComputerDetails.State.ONLINE,
            actions = buildHostPrimaryActions(details),
            onAction = { handleHostAction(it.id, details) },
            onDismiss = { selectedAction ->
                if (selectedAction?.id != MORE_ACTIONS_ID) {
                    startComputerUpdates()
                }
            }
        )
    }

    private fun buildHostPrimaryActions(details: ComputerDetails): List<AppActionSheet.Action> = buildList {
        fun add(
            id: Int,
            titleRes: Int,
            destructive: Boolean = false,
            sectionStart: Boolean = false,
            opensSubmenu: Boolean = false
        ) {
            add(AppActionSheet.Action(
                id = id,
                title = getString(titleRes),
                destructive = destructive,
                sectionStart = sectionStart,
                opensSubmenu = opensSubmenu
            ))
        }

        val paired = details.pairState == PairState.PAIRED
        if (details.state == ComputerDetails.State.OFFLINE || details.state == ComputerDetails.State.UNKNOWN) {
            add(WOL_ID, R.string.pcview_menu_send_wol)
        } else if (!paired) {
            add(PAIR_ID, R.string.pcview_menu_pair_pc)
        } else {
            if (details.runningGameId != 0) {
                add(RESUME_ID, R.string.applist_menu_resume)
            }
            add(FULL_APP_LIST_ID, R.string.pcview_menu_app_list)
            if (details.vddCapabilityVersion != 0) {
                add(SECONDARY_SCREEN_ID, R.string.pcview_menu_secondary_screen)
            }
        }

        add(TEST_NETWORK_ID, R.string.pcview_menu_test_network, sectionStart = true)
        add(
            MORE_ACTIONS_ID,
            R.string.pcview_menu_more_actions,
            opensSubmenu = true
        )
        if (paired && details.runningGameId != 0) {
            add(QUIT_ID, R.string.applist_menu_quit, destructive = true, sectionStart = true)
        }
    }

    private fun showHostMoreActionSheet(details: ComputerDetails) {
        stopComputerUpdates(false)
        AppActionSheet.show(
            context = this,
            title = details.name.orEmpty(),
            subtitle = getString(R.string.pcview_menu_more_actions),
            actions = buildHostMoreActions(details),
            onAction = { handleHostAction(it.id, details) },
            onDismiss = { startComputerUpdates() }
        )
    }

    private fun buildHostMoreActions(details: ComputerDetails): List<AppActionSheet.Action> = buildList {
        fun add(id: Int, titleRes: Int, destructive: Boolean = false, sectionStart: Boolean = false) {
            add(AppActionSheet.Action(
                id = id,
                title = getString(titleRes),
                destructive = destructive,
                sectionStart = sectionStart
            ))
        }

        add(IPERF3_TEST_ID, R.string.network_bandwidth_test)
        if (!details.nvidiaServer && resolveSunshineWebUiUrl(details) != null) {
            add(OPEN_WEBUI_ID, R.string.pcview_menu_open_webui)
        }
        add(VIEW_DETAILS_ID, R.string.pcview_menu_details, sectionStart = true)
        if (details.pairState == PairState.PAIRED && details.state == ComputerDetails.State.ONLINE) {
            add(SLEEP_ID, R.string.send_sleep_command)
        }
        add(
            DISABLE_IPV6_ID,
            if (details.ipv6Disabled) R.string.pcview_menu_enable_ipv6
            else R.string.pcview_menu_disable_ipv6
        )
        if (details.nvidiaServer) {
            add(GAMESTREAM_EOL_ID, R.string.pcview_menu_eol)
        }
        add(DELETE_ID, R.string.pcview_menu_delete_pc, destructive = true, sectionStart = true)
    }

    private fun handleHostAction(itemId: Int, details: ComputerDetails): Boolean {
        return when (itemId) {
            PAIR_ID -> {
                doPair(details)
                true
            }
            UNPAIR_ID -> {
                doUnpair(details)
                true
            }
            WOL_ID -> {
                doWakeOnLan(details)
                true
            }
            DELETE_ID -> {
                handleDeletePc(details)
                true
            }
            FULL_APP_LIST_ID -> {
                doAppList(details, newlyPaired = false, showHiddenGames = true)
                true
            }
            RESUME_ID -> {
                handleResume(details)
                true
            }
            QUIT_ID -> {
                handleQuit(details)
                true
            }
            SLEEP_ID -> {
                handleSleep(details)
                true
            }
            VIEW_DETAILS_ID -> {
                Dialog.displayDetailsDialog(this, getString(R.string.title_details), details.toString(), false)
                true
            }
            TEST_NETWORK_ID -> {
                ServerHelper.doNetworkTest(this)
                true
            }
            IPERF3_TEST_ID -> {
                handleIperf3Test(details)
                true
            }
            SECONDARY_SCREEN_ID -> {
                handleSecondaryScreen(details)
                true
            }
            GAMESTREAM_EOL_ID -> {
                HelpLauncher.launchGameStreamEolFaq(this)
                true
            }
            DISABLE_IPV6_ID -> {
                handleToggleIpv6Disabled(details)
                true
            }
            OPEN_WEBUI_ID -> {
                handleOpenSunshineWebUi(details)
                true
            }
            MORE_ACTIONS_ID -> {
                showHostMoreActionSheet(details)
                true
            }
            else -> false
        }
    }

    /**
     * 解析 Sunshine 主机的 WebUI 地址：使用当前激活地址（在线时）或备选已知地址，
     * 端口 = HTTP 端口 + 1（Sunshine 默认 47989 -> 47990，HTTPS）。
     */
    private fun resolveSunshineWebUiUrl(details: ComputerDetails): String? {
        val tuple = details.activeAddress
            ?: details.localAddress
            ?: details.manualAddress
            ?: details.remoteAddress
            ?: details.ipv6Address
            ?: return null
        val host = if (tuple.address.contains(":")) "[${tuple.address}]" else tuple.address
        val webPort = tuple.port + 1
        return "https://$host:$webPort"
    }

    private fun handleOpenSunshineWebUi(details: ComputerDetails) {
        val url = resolveSunshineWebUiUrl(details)
        if (url == null) {
            showToast(getString(R.string.pcview_open_webui_unavailable))
            return
        }
        // LAN（私网 IP）下 Sunshine 用自签证书，Android System WebView 147+ 在
        // 私网 IP + self-signed 组合下即使 onReceivedSslError → proceed() 也会
        // 进入 ERR_TOO_MANY_RETRIES 死循环（chromium 不缓存 trust 决定）。
        // 此场景跳转系统浏览器，由用户在浏览器里"高级 → 继续访问"绕过。
        if (NetHelper.isPrivateNetworkUrl(url)) {
            try {
                val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(view)
                return
            } catch (e: Exception) {
                LimeLog.warning("No external browser for $url: ${e.message}")
                showToast(getString(R.string.pcview_open_webui_no_browser))
                return
            }
        }
        try {
            val certDer = details.serverCert?.encoded
            val intent = SunshineWebUiActivity.createIntent(
                this,
                url,
                getString(R.string.pcview_menu_open_webui) + " - " + (details.name ?: ""),
                certDer
            )
            startActivity(intent)
        } catch (e: Exception) {
            LimeLog.warning("Failed to launch in-app WebUI for $url: ${e.message}")
            showToast(getString(R.string.pcview_open_webui_no_browser))
        }
    }

    private fun handleDeletePc(details: ComputerDetails) {
        if (ActivityManager.isUserAMonkey()) {
            LimeLog.info("Ignoring delete PC request from monkey")
            return
        }
        UiHelper.displayDeletePcConfirmationDialog(this, details, {
            if (managerBinder == null) {
                showToast(getString(R.string.error_manager_not_running))
                return@displayDeletePcConfirmationDialog
            }
            removeComputer(details)
        }, null)
    }

    private fun handleResume(details: ComputerDetails) {
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }
        var app = getNvAppById(details.runningGameId, details.uuid!!)
        if (app == null) {
            app = NvApp("app", details.runningGameId, false)
        }
        val binder = managerBinder!!
        uiScope.launch {
            if (PairStatePreflight.isConfirmedNotPaired(details, binder, "Resume session")) {
                showToast(getString(R.string.scut_not_paired))
                return@launch
            }
            ServerHelper.doStart(this@PcView, app, details, binder, forceResumeCurrentSession = true)
        }
    }

    private fun handleQuit(details: ComputerDetails) {
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }
        UiHelper.displayQuitConfirmationDialog(this,
                { ServerHelper.doQuit(this, details, NvApp("app", 0, false), managerBinder!!, null) },
                null)
    }

    private fun handleSleep(details: ComputerDetails) {
        val binder = managerBinder
        if (binder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }
        UiHelper.displaySleepConfirmationDialog(this, details, {
            ServerHelper.pcSleep(this, details, binder, null)
        }, null)
    }

    private fun handleIperf3Test(details: ComputerDetails) {
        try {
            val ip = ServerHelper.getCurrentAddressFromComputer(details).address
            Iperf3Tester(this, ip).show()
        } catch (e: IOException) {
            showToast(getString(R.string.unable_to_get_pc_address, e.message))
        }
    }

    private fun handleSecondaryScreen(details: ComputerDetails) {
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }
        doSecondaryScreenStream(details)
    }

    private fun handleToggleIpv6Disabled(details: ComputerDetails) {
        if (managerBinder == null) {
            showToast(getString(R.string.error_manager_not_running))
            return
        }

        // 切换IPv6禁用状态
        details.ipv6Disabled = !details.ipv6Disabled

        // 如果禁用了IPv6，清空所有IPv6相关地址
        if (details.ipv6Disabled) {
            details.ipv6Address = null

            // 如果activeAddress是IPv6，清空它
            if (ComputerDetails.isIpv6Address(details.activeAddress)) {
                details.activeAddress = null
            }

            // 从availableAddresses中移除所有IPv6地址
            details.availableAddresses.removeIf { ComputerDetails.isIpv6Address(it) }
        }

        // 更新数据库
        managerBinder?.updateComputer(details)

        // 显示Toast提示用户当前状态
        if (details.ipv6Disabled) {
            showToast(getString(R.string.pcview_ipv6_disabled))
        } else {
            showToast(getString(R.string.pcview_ipv6_enabled))
        }
        // 刷新列表
        startComputerUpdates()
    }

    // Adapter Fragment Callbacks

    override fun getAdapterFragmentLayoutId(): Int {
        return R.layout.pc_grid_view
    }

    override fun receiveAbsListView(gridView: View?) {
        if (gridView == null) return

        receiveAdapterView(gridView)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun receiveAdapterView(view: View) {
        if (view !is AbsListView) return

        val listView = view
        pcListView = listView
        listView.setSelector(android.R.color.transparent)
        listView.setAdapter(pcGridAdapter)

        setupListAnimation(listView)
        handleFirstLoadAnimation(listView)
        setupListItemClick(listView)
        setupGridColumnWidth(view)
        setupEmptyAreaLongPress(listView)

        UiHelper.applyStatusBarPadding(listView)
        setupHostActionSheet(listView)
    }

    private fun setupListAnimation(listView: AbsListView) {
        // stagger 0.08：在 splash 退场窗口（~280ms）内启动级联，留一点呼吸感避免“齐刷刷”僵硬。
        // 单卡 380ms，整体在 splash 收尾后再多 120ms 内完成 — 视觉接力而非二段式。
        val controller = LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.pc_grid_item_sort), 0.08f)
        controller.order = LayoutAnimationController.ORDER_NORMAL
        listView.layoutAnimation = controller
    }

    private fun handleFirstLoadAnimation(listView: AbsListView) {
        if (!isFirstLoad) return

        // 首屏不再额外加 listView 整体淡入（之前 250ms postDelayed + 200ms alpha）——
        // 那样会让卡片在 splash 退完后才入场，造成明显脱节。
        // 现在直接调度 layoutAnimation，让卡片与 splash 退场同步澌起。
        listView.alpha = 1f
        pcGridAdapter.notifyDataSetChanged()
        listView.scheduleLayoutAnimation()
        isFirstLoad = false
    }

    private fun setupListItemClick(listView: AbsListView) {
        listView.setOnItemClickListener { _, view, pos, _ ->
            val computer = pcGridAdapter.getItem(pos) as ComputerObject

            if (PcGridAdapter.isAddComputerCard(computer)) {
                showAddComputerDialog()
                return@setOnItemClickListener
            }

            if (computer.details.state == ComputerDetails.State.UNKNOWN
                    || computer.details.state == ComputerDetails.State.OFFLINE) {
                showHostActionSheet(computer.details)
            } else if (computer.details.pairState != PairState.PAIRED) {
                doPair(computer.details)
            } else if (computer.details.hasMultipleLanAddresses()) {
                showAddressSelectionDialog(computer.details)
            } else {
                val temp = prepareComputerWithAddress(computer.details)
                if (temp != null) {
                    doAppList(temp, newlyPaired = false, showHiddenGames = false)
                } else {
                    showToast(getString(R.string.error_pc_offline))
                }
            }
        }
    }

    private fun setupGridColumnWidth(view: View) {
        if (view is GridView) {
            calculateDynamicColumnWidth(view)
        }
    }

    private fun setupHostActionSheet(listView: AbsListView) {
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val computer = pcGridAdapter.getItem(position) as ComputerObject
            if (PcGridAdapter.isAddComputerCard(computer)) {
                false
            } else {
                showHostActionSheet(computer.details)
                true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEmptyAreaLongPress(listView: AbsListView) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (listView.pointToPosition(e.x.toInt(), e.y.toInt()) == AdapterView.INVALID_POSITION) {
                    saveImageWithPermissionCheck()
                }
            }
        })

        listView.setOnTouchListener { _, event ->
            if (listView.pointToPosition(event.x.toInt(), event.y.toInt()) == AdapterView.INVALID_POSITION) {
                detector.onTouchEvent(event)
            }
            false
        }
    }

    private fun calculateDynamicColumnWidth(gridView: GridView) {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - gridView.paddingStart - gridView.paddingEnd
        val spacingPx = (15f * density).toInt()
        val minColumnPx = (180f * density).toInt()

        val numColumns = 1.coerceAtLeast((availableWidth + spacingPx) / (minColumnPx + spacingPx))
        val columnWidth = (availableWidth - (numColumns - 1) * spacingPx) / numColumns

        gridView.columnWidth = columnWidth
    }

    // Dialogs

    private fun showEasyTierControlDialog() {
        easyTierController?.showControlDialog()
    }

    // VPN Permission

    override fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_PERMISSION_REQUEST_CODE)
        } else {
            onActivityResult(VPN_PERMISSION_REQUEST_CODE, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Handle ZXing scan result
        val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scanResult != null) {
            if (scanResult.contents != null) {
                handleQrPairResult(scanResult.contents.trim())
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST_CODE && easyTierController != null) {
            easyTierController?.handleVpnPermissionResult(resultCode)
        } else if (requestCode == UpdateManager.INSTALL_PERMISSION_REQUEST_CODE) {
            UpdateManager.onInstallPermissionResult(this)
        }
    }

    // Utility

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Inner Classes

    class ComputerObject(var details: ComputerDetails) {

        override fun toString(): String {
            return details.name!!
        }
    }
}
