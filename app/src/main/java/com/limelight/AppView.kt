@file:Suppress("DEPRECATION")
package com.limelight

import java.io.IOException
import java.io.StringReader
import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.xmlpull.v1.XmlPullParserException

import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.computers.PairStatePreflight
import com.limelight.grid.AppGridAdapter
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.NvHTTP.DisplayInfo
import com.limelight.nvstream.http.PairingManager
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.AdapterFragment
import com.limelight.ui.AdapterFragmentCallbacks
import com.limelight.ui.AdapterRecyclerBridge
import com.limelight.ui.ScreenCombinationModePickerView
import com.limelight.ui.SelectionIndicatorAnimator
import com.limelight.utils.AppSettingsManager
import com.limelight.utils.BackgroundImageManager
import com.limelight.utils.CacheHelper
import com.limelight.utils.Dialog
import com.limelight.utils.FrameMetricsLogger
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import com.limelight.utils.SoftBackgroundColorExtractor
import com.limelight.utils.SpinnerDialog
import com.limelight.utils.UiHelper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.isNotEmpty
import androidx.preference.PreferenceManager
import kotlin.math.ceil

class AppView : Activity(), AdapterFragmentCallbacks {

    // 主线程作用域，用于收集 ComputerManagerService 的 Flow。
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollingCollectJob: Job? = null

    // ==================== 上下文菜单 ID ====================
    companion object {
        private const val START_OR_RESUME_ID = 1
        private const val QUIT_ID = 2
        private const val START_WITH_QUIT = 4
        private const val VIEW_DETAILS_ID = 5
        private const val CREATE_SHORTCUT_ID = 6
        private const val HIDE_APP_ID = 7
        private const val START_WITH_LAST_SETTINGS_ID = 8

        // ==================== Intent Extras & 偏好键 ====================
        const val HIDDEN_APPS_PREF_FILENAME = "HiddenApps"
        const val NAME_EXTRA = "Name"
        const val UUID_EXTRA = "UUID"
        const val NEW_PAIR_EXTRA = "NewPair"
        const val SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps"
        const val SELECTED_ADDRESS_EXTRA = "SelectedAddress"
        const val SELECTED_PORT_EXTRA = "SelectedPort"

        // ==================== 布局常量 ====================
        private const val DEFAULT_VERTICAL_SPAN_COUNT = 2
        private const val DEFAULT_HORIZONTAL_SPAN_COUNT = 1
        private const val VERTICAL_SINGLE_ROW_THRESHOLD = 5
        private const val BACKGROUND_CHANGE_DELAY = 300 // ms
        private const val DISPLAY_CHECK_DELAY_MS = 800L
        private const val NOT_PAIRED_EXIT_CONFIRMATION_UPDATES = 2
        private const val VIRTUAL_DISPLAY_ID = 212333
        private const val APPVIEW_PREFS_NAME = "AppView"
        private const val KEY_APP_BACKGROUND_MODE = "app_background_mode"
        private const val SCREEN_COMBINATION_MODE_PREF_KEY = "list_screen_combination_mode"
    }

    // ==================== 核心数据 ====================
    private var appGridAdapter: AppGridAdapter? = null
    private lateinit var uuidString: String
    private var computer: ComputerDetails? = null
    private lateinit var computerName: String
    private var lastRawApplist: String? = null
    private var lastRunningAppId = 0
    private var notPairedExitUpdateCount = 0
    private var suspendGridUpdates = false
    private var inForeground = false
    private var showHiddenApps = false
    private val hiddenAppIds = HashSet<Int>()

    // ==================== 服务 & 工具 ====================
    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var poller: ComputerManagerService.ApplistPoller? = null
    private lateinit var shortcutHelper: ShortcutHelper
    private var blockingLoadSpinner: SpinnerDialog? = null

    // ==================== UI 组件 - 背景 ====================
    private var appBackgroundImageBlur: ImageView? = null
    private lateinit var appBackgroundImageClear: ImageView
    private var backgroundImageManager: BackgroundImageManager? = null
    private val backgroundChangeHandler = Handler(Looper.getMainLooper())
    private var backgroundChangeRunnable: Runnable? = null
    private val displayCheckHandler = Handler(Looper.getMainLooper())
    private var displayCheckRunnable: Runnable? = null
    private lateinit var appBackgroundModeGroup: RadioGroup
    private var appBackgroundMode = AppBackgroundMode.Artwork
    private var activeBackgroundAppId: Int? = null
    private var activeBackgroundMode: AppBackgroundMode? = null
    private var backgroundRequestSerial = 0
    private val appSoftColorCache = ConcurrentHashMap<Int, Int>()

    // ==================== UI 组件 - 选中框 & 列表 ====================
    private var selectionAnimator: SelectionIndicatorAnimator? = null
    private var currentRecyclerView: RecyclerView? = null
    private var currentAdapterBridge: AdapterRecyclerBridge? = null
    private var pendingAdapterFragmentView: View? = null
    private var selectedPosition = -1
    private var isFirstFocus = true
    private var frameMetricsLogger: FrameMetricsLogger? = null

    // ==================== UI 组件 - 上一次设置 ====================
    private var appSettingsManager: AppSettingsManager? = null
    private lateinit var lastSettingsInfo: LinearLayout
    private lateinit var lastSettingsText: TextView
    private lateinit var useLastSettingsCheckbox: CheckBox

    // ==================== UI 组件 - 顶部下拉面板 & 显示器选择 ====================
    private lateinit var topPanelScrim: View
    private lateinit var topDropdownPanel: ScrollView
    private var isPanelOpen = false
    private lateinit var displaySelectionInfo: LinearLayout
    private lateinit var displayRadioGroup: RadioGroup
    private lateinit var screenCombinationModeLabel: TextView
    private lateinit var screenCombinationModeOverlay: FrameLayout
    private var selectedScreenCombinationMode = -1
    private var currentModeNames: Array<String>? = null
    private var currentModeValues: Array<String>? = null
    private var availableDisplays: List<DisplayInfo>? = null
    private val hostHttpLock = Any()
    private var hostHttpClient: NvHTTP? = null
    private var hostHttpKey: String? = null

    // ==================== 服务连接 ====================

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as ComputerManagerService.ComputerManagerBinder

            uiScope.launch {
                // Wait in IO to avoid stalling the UI
                val ready = withContext(Dispatchers.IO) {
                    localBinder.waitForReady()

                    val comp = localBinder.getComputer(uuidString) ?: return@withContext false
                    computer = comp

                    val selectedAddress = intent.getStringExtra(SELECTED_ADDRESS_EXTRA)
                    val selectedPort = intent.getIntExtra(SELECTED_PORT_EXTRA, -1)
                    if (selectedAddress != null && selectedPort > 0) {
                        computer?.activeAddress = ComputerDetails.AddressTuple(selectedAddress, selectedPort)
                    }

                    shortcutHelper.createAppViewShortcut(computer!!, true, intent.getBooleanExtra(NEW_PAIR_EXTRA, false))
                    shortcutHelper.reportComputerShortcutUsed(computer!!)

                    try {
                        appGridAdapter = AppGridAdapter(this@AppView,
                                PreferenceConfiguration.readPreferences(this@AppView),
                                computer!!, localBinder.getUniqueId(),
                                showHiddenApps)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext false
                    }

                    appGridAdapter?.updateHiddenApps(hiddenAppIds, true)
                    managerBinder = localBinder
                    localBinder.setForegroundComputer(uuidString)
                    true
                }

                if (!ready) {
                    finish()
                    return@launch
                }

                if (isFinishing || isChangingConfigurations) return@launch

                populateAppGridWithCache()
                startComputerUpdates()
                scheduleDisplayCheck()

                pendingAdapterFragmentView?.let { pendingView ->
                    pendingAdapterFragmentView = null
                    receiveAdapterView(pendingView)
                }

                try {
                    if (fragmentManager.findFragmentById(R.id.appFragmentContainer) == null) {
                        fragmentManager.beginTransaction()
                                .replace(R.id.appFragmentContainer, AdapterFragment())
                                .commitAllowingStateLoss()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            managerBinder = null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // If appGridAdapter is initialized, let it know about the configuration change.
        // If not, it will pick it up when it initializes.
        if (appGridAdapter != null) {
            // Update the app grid adapter to create grid items with the correct layout
            appGridAdapter?.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this))

            try {
                // Reinflate the app grid itself to pick up the layout change
                fragmentManager.beginTransaction()
                        .replace(R.id.appFragmentContainer, AdapterFragment())
                        .commitAllowingStateLoss()

                // 延迟检查布局，等待Fragment重新创建完成
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentRecyclerView != null) {
                        checkAndUpdateLayout(currentRecyclerView!!)
                    }
                }, 100)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    // ==================== 计算机轮询管理 ====================

    private fun startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        val binder = managerBinder ?: return
        if (!inForeground) return

        pollingCollectJob?.cancel()
        pollingCollectJob = uiScope.launch {
            binder.computerUpdates
                .filter { !suspendGridUpdates }
                .filter { it.uuid.equals(uuidString, ignoreCase = true) }
                .collect { details -> handleComputerUpdate(details) }
        }
        binder.startPolling()

        if (poller == null) {
            poller = binder.createAppListPoller(computer!!)
        }
        poller?.start()
    }

    private fun handleComputerUpdate(details: ComputerDetails) {
        if (details.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (shouldExitForNotPaired(details)) {
            shortcutHelper.disableComputerShortcut(details,
                    resources.getString(R.string.scut_not_paired))
            Toast.makeText(this, resources.getText(R.string.scut_not_paired), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        computer = details

        // App list is the same or empty
        if (details.rawAppList == null || details.rawAppList == lastRawApplist) {
            if (details.runningGameId != lastRunningAppId) {
                lastRunningAppId = details.runningGameId
                updateUiWithServerinfo(details)
            }
            return
        }

        lastRunningAppId = details.runningGameId
        lastRawApplist = details.rawAppList

        try {
            updateUiWithAppList(NvHTTP.getAppListByReader(StringReader(details.rawAppList)))
            updateUiWithServerinfo(details)

            if (blockingLoadSpinner != null) {
                blockingLoadSpinner?.dismiss()
                blockingLoadSpinner = null
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun shouldExitForNotPaired(details: ComputerDetails): Boolean {
        if (details.state != ComputerDetails.State.ONLINE ||
            details.pairState == PairingManager.PairState.PAIRED
        ) {
            notPairedExitUpdateCount = 0
            return false
        }

        notPairedExitUpdateCount++
        if (notPairedExitUpdateCount < NOT_PAIRED_EXIT_CONFIRMATION_UPDATES) {
            LimeLog.warning(
                "AppView: deferring NOT_PAIRED update for ${details.name ?: details.uuid} " +
                        "($notPairedExitUpdateCount/$NOT_PAIRED_EXIT_CONFIRMATION_UPDATES)"
            )
            return false
        }

        return true
    }

    private fun stopComputerUpdates() {
        poller?.stop()

        pollingCollectJob?.cancel()
        pollingCollectJob = null
        managerBinder?.stopPolling()

        appGridAdapter?.cancelQueuedOperations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true

        shortcutHelper = ShortcutHelper(this)

        UiHelper.setLocale(this)

        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        setContentView(R.layout.activity_app_view)

        // Initialize background image views
        appBackgroundImageBlur = findViewById(R.id.appBackgroundImageBlur)
        appBackgroundImageClear = findViewById(R.id.appBackgroundImageClear)
        // 竖屏：仅保留模糊层铺满屏幕（blur 图本身经 RenderEffect / StackBlur 处理后已是装饰性背景）
        // 横屏：保留双层（模糊+清晰）原视觉
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        backgroundImageManager = if (isPortrait) {
            appBackgroundImageClear.visibility = View.GONE
            BackgroundImageManager(this, null, appBackgroundImageBlur!!, blurOnly = true)
        } else {
            BackgroundImageManager(this, appBackgroundImageBlur, appBackgroundImageClear)
        }

        // Initialize app settings manager and UI components
        appSettingsManager = AppSettingsManager(this)
        lastSettingsInfo = findViewById(R.id.lastSettingsInfo)
        lastSettingsText = findViewById(R.id.lastSettingsText)
        useLastSettingsCheckbox = findViewById(R.id.useLastSettingsCheckbox)

        // Initialize top dropdown panel
        topPanelScrim = findViewById(R.id.topPanelScrim)
        topPanelScrim.setOnClickListener {
            closeTopPanel()
        }
        topDropdownPanel = findViewById(R.id.topDropdownPanel)
        appBackgroundMode = readAppBackgroundMode()
        appBackgroundModeGroup = findViewById(R.id.appBackgroundModeGroup)
        setupAppBackgroundModeControls()

        // Initialize display selection UI components
        displaySelectionInfo = findViewById(R.id.displaySelectionInfo)
        displayRadioGroup = findViewById(R.id.displayRadioGroup)
        screenCombinationModeLabel = findViewById(R.id.screenCombinationModeLabel)
        screenCombinationModeOverlay = findViewById(R.id.screenCombinationModeOverlay)
        findViewById<TextView>(R.id.clearDisplaySelectionButton).setOnClickListener {
            clearDisplaySelection()
        }
        screenCombinationModeLabel.let { label ->
            label.paintFlags = label.paintFlags or Paint.UNDERLINE_TEXT_FLAG

            // 点击组合模式标签时打开全屏选择视图
            label.setOnClickListener { showScreenCombinationModeView() }
        }
        refreshScreenCombinationModeFromPreferences()

        // 监听 RadioGroup 选中变化，动态更新组合模式选项
        displayRadioGroup.setOnCheckedChangeListener { _, _ ->
            refreshScreenCombinationModeOptions()
            updateScreenCombinationModeLabel()
        }

        // Set up event listeners
        useLastSettingsCheckbox.setOnCheckedChangeListener { _, isChecked -> appSettingsManager?.setUseLastSettingsEnabled(isChecked) }

        // Initialize selection indicator animator
        val selectionIndicator = findViewById<View>(R.id.selectionIndicator)
        selectionAnimator = SelectionIndicatorAnimator(
                selectionIndicator,
                null, // RecyclerView will be set later
                null, // Adapter will be set later
                findViewById(android.R.id.content)
        )
        selectionAnimator?.setPositionProvider { selectedPosition }

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false)
        }

        UiHelper.notifyNewRootView(this)

        showHiddenApps = intent.getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false)
        uuidString = intent.getStringExtra(UUID_EXTRA) ?: ""

        val hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
        for (hiddenAppIdStr in (hiddenAppsPrefs.getStringSet(uuidString, HashSet()) ?: emptySet())) {
            hiddenAppIds.add(hiddenAppIdStr.toInt())
        }

        computerName = intent.getStringExtra(NAME_EXTRA) ?: ""

        val label = findViewById<TextView>(R.id.appListText)
        title = computerName
        label.text = computerName

        // 点击标题恢复串流
        label.setOnClickListener {
            LimeLog.info("Title clicked, lastRunningAppId=$lastRunningAppId")
            if (lastRunningAppId != 0 && appGridAdapter != null) {
                for (i in 0 until (appGridAdapter?.count ?: 0)) {
                    val app = appGridAdapter?.getItem(i) as AppObject
                    if (app.app.appId == lastRunningAppId) {
                            startStreamWithLastSettingsIfEnabled(app, forceResumeCurrentSession = true)
                        break
                    }
                }
            }
        }

        // Setup top panel toggle handle
        val topPanelToggle = findViewById<View>(R.id.topPanelToggle)
        topPanelToggle.setOnClickListener { toggleTopPanel() }

        // 动态设置手柄 margin 使其精确贴合状态栏底部
        topPanelToggle.setOnApplyWindowInsetsListener { v, insets ->
            val statusBarHeight = insets.systemWindowInsetTop
            val params = v.layoutParams as android.widget.RelativeLayout.LayoutParams
            params.topMargin = statusBarHeight
            v.layoutParams = params
            insets
        }

        // Setup settings entry in panel
        val settingsEntry = findViewById<TextView>(R.id.settingsEntry)
        settingsEntry.setOnClickListener {
            closeTopPanel()
            val intent = Intent(this@AppView, com.limelight.preferences.StreamSettings::class.java)
            startActivity(intent)
        }

        // Bind to the computer manager service
        bindService(Intent(this, ComputerManagerService::class.java), serviceConnection,
            BIND_AUTO_CREATE
        )

    }

    // ==================== UI 更新 ====================

    private fun updateHiddenApps(hideImmediately: Boolean) {
        val hiddenAppIdStringSet = HashSet<String>()

        for (hiddenAppId in hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString())
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit {
                    putStringSet(uuidString, hiddenAppIdStringSet)
                }

        appGridAdapter?.updateHiddenApps(hiddenAppIds, hideImmediately)
    }

    /**
     * 更新标题中恢复箭头的显示
     */
    private fun updateRestoreButtonVisibility(hasRunningApp: Boolean) {
        // 找到当前选中的应用名，或运行中的应用名
        var appName: String? = null
        if (selectedPosition >= 0 && appGridAdapter != null && selectedPosition < (appGridAdapter?.count ?: 0)) {
            val app = appGridAdapter?.getItem(selectedPosition) as AppObject
            appName = app.app.appName
        } else if (hasRunningApp && appGridAdapter != null) {
            // 没有选中项时，尝试显示运行中应用的名称
            for (i in 0 until (appGridAdapter?.count ?: 0)) {
                val app = appGridAdapter?.getItem(i) as AppObject
                if (app.app.appId == lastRunningAppId) {
                    appName = app.app.appName
                    break
                }
            }
        }
        updateTitle(appName)
    }

    @SuppressLint("SetTextI18n")
    private fun updateTitle(appName: String?) {
        val label = findViewById<TextView>(R.id.appListText)
        val hasRunningApp = lastRunningAppId != 0
        val arrow = if (hasRunningApp) " ▸" else ""

        if (!appName.isNullOrEmpty()) {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val separator = if (isLandscape) " - " else "\n"
            val text = "$computerName$separator$appName$arrow"

            val spannableString = SpannableString(text)
            val appNameStart = computerName.length + separator.length

            spannableString.setSpan(
                    RelativeSizeSpan(0.85f),
                    appNameStart,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            label.text = spannableString
        } else {
            label.text = "$computerName$arrow"
        }
    }

    private fun changeBackgroundWithDebounce(app: AppObject?) {
        requestAppBackground(app, debounce = true)
    }

    private fun requestAppBackground(app: AppObject?, debounce: Boolean, force: Boolean = false) {
        backgroundChangeRunnable?.let { backgroundChangeHandler.removeCallbacks(it) }
        if (app == null || !isBackgroundEligible(app)) {
            backgroundChangeRunnable = null
            return
        }

        val runnable = Runnable {
            applyAppBackground(app, force)
            backgroundChangeRunnable = null
        }
        backgroundChangeRunnable = runnable

        if (debounce) {
            backgroundChangeHandler.postDelayed(runnable, BACKGROUND_CHANGE_DELAY.toLong())
        } else {
            runnable.run()
        }
    }

    private fun applyAppBackground(appObject: AppObject, force: Boolean = false) {
        if (isFinishing || isDestroyed) {
            return
        }

        val manager = backgroundImageManager ?: return
        val mode = appBackgroundMode
        val appId = appObject.app.appId
        if (!force && activeBackgroundAppId == appId && activeBackgroundMode == mode && manager.hasBackground) {
            return
        }

        activeBackgroundAppId = appId
        activeBackgroundMode = mode
        val requestId = ++backgroundRequestSerial

        when (mode) {
            AppBackgroundMode.Artwork -> {
                val loader = appGridAdapter?.getLoader() ?: return
                loader.loadFullBitmap(appObject.app) { bitmap ->
                    runOnUiThread {
                        if (requestId == backgroundRequestSerial) {
                            manager.setBackgroundSmoothly(bitmap)
                        }
                    }
                }
            }
            AppBackgroundMode.SoftColor -> {
                applySoftColorBackground(appObject, requestId, manager)
            }
        }
    }

    private fun applySoftColorBackground(appObject: AppObject, requestId: Int, manager: BackgroundImageManager) {
        val app = appObject.app
        appSoftColorCache[app.appId]?.let {
            manager.setBackgroundColorSmoothly(it)
            return
        }

        val fallbackColor = SoftBackgroundColorExtractor.fallbackFor(app)
        manager.setBackgroundColorSmoothly(fallbackColor)

        val loader = appGridAdapter?.getLoader() ?: return
        loader.loadFullBitmap(app) { bitmap ->
            uiScope.launch(Dispatchers.Default) {
                val color = SoftBackgroundColorExtractor.fromBitmap(bitmap, fallbackColor)
                appSoftColorCache[app.appId] = color
                withContext(Dispatchers.Main.immediate) {
                    if (!isFinishing &&
                            !isDestroyed &&
                            requestId == backgroundRequestSerial &&
                            appBackgroundMode == AppBackgroundMode.SoftColor &&
                            activeBackgroundAppId == app.appId) {
                        manager.setBackgroundColorSmoothly(color)
                    }
                }
            }
        }
    }

    private fun resolveCurrentBackgroundCandidate(): AppObject? {
        val adapter = appGridAdapter ?: return null
        val focused = if (selectedPosition in 0 until adapter.count) {
            adapter.getItem(selectedPosition) as? AppObject
        } else {
            null
        }
        if (focused != null && isBackgroundEligible(focused)) return focused

        findFirstBackgroundCandidate { it.isRunning }?.let { return it }
        findFirstBackgroundCandidate { it.app.appName.equals("desktop", ignoreCase = true) }?.let { return it }
        return findFirstBackgroundCandidate { true }
    }

    private fun findFirstBackgroundCandidate(predicate: (AppObject) -> Boolean): AppObject? {
        val adapter = appGridAdapter ?: return null
        for (i in 0 until adapter.count) {
            val app = adapter.getItem(i) as? AppObject ?: continue
            if (isBackgroundEligible(app) && predicate(app)) {
                return app
            }
        }
        return null
    }

    private fun isBackgroundEligible(app: AppObject): Boolean = !app.isHidden || showHiddenApps

    /**
     * 计算最优的spanCount
     *
     * @param orientation 屏幕方向
     * @return 最优的行数
     */
    private fun calculateOptimalSpanCount(orientation: Int): Int {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return DEFAULT_HORIZONTAL_SPAN_COUNT
        } else {
            // 竖屏：根据app数量固定阈值判断
            if (appGridAdapter == null) {
                return DEFAULT_VERTICAL_SPAN_COUNT
            }

            val appCount = appGridAdapter?.count ?: 0
            if (appCount == 0) {
                return DEFAULT_VERTICAL_SPAN_COUNT
            }

            return if (appCount <= VERTICAL_SINGLE_ROW_THRESHOLD) {
                DEFAULT_HORIZONTAL_SPAN_COUNT
            } else {
                DEFAULT_VERTICAL_SPAN_COUNT
            }
        }
    }

    /**
     * 处理选中项变化
     *
     * @param position 选中位置
     * @param app      选中的应用对象
     */
    private fun handleSelectionChange(position: Int, app: AppObject) {
        selectedPosition = position
        updateTitle(app.app.appName)
        if (appGridAdapter != null) {
            appGridAdapter?.selectedPosition = position
        }

        // 防抖切换背景
        changeBackgroundWithDebounce(app)

        // 移动选中框动画
        if (selectionAnimator != null) {
            selectionAnimator?.moveToPosition(position, isFirstFocus)
            isFirstFocus = false // 第一次后设置为false
        }

        updateLastSettingsInfo(app)
    }

    /**
     * 更新上一次设置信息显示
     *
     * @param app 应用对象
     */
    private fun updateLastSettingsInfo(app: AppObject) {
        if (appSettingsManager == null || computer == null) {
            return
        }

        val settingsSummary = appSettingsManager?.getSettingsSummary(computer?.uuid!!, app.app)
        val noneSettingsText = getString(R.string.app_last_settings_none)

        val hasValidSettings = settingsSummary != null && settingsSummary != noneSettingsText

        if (hasValidSettings) {
            val displayText = getString(R.string.app_last_settings_title) + " " + settingsSummary
            lastSettingsText.text = displayText
            lastSettingsInfo.visibility = View.VISIBLE

            // 同步复选框状态(避免不必要的更新)
            val useLastSettings = appSettingsManager?.isUseLastSettingsEnabled
            if (useLastSettingsCheckbox.isChecked != (useLastSettings == true)) {
                useLastSettingsCheckbox.isChecked = useLastSettings == true
            }
        } else {
            lastSettingsInfo.visibility = View.GONE
        }
    }

    /**
     * 启动串流，如果勾选了使用上一次设置则使用上一次设置
     *
     * @param app 应用对象
     */
    private fun startStreamWithLastSettingsIfEnabled(app: AppObject, forceResumeCurrentSession: Boolean = false) {
        var displayGuid: String? = null
        var useVdd: Boolean? = null

        if (displaySelectionInfo.isVisible && availableDisplays != null) {
            val selectedId = displayRadioGroup.checkedRadioButtonId
            if (selectedId == VIRTUAL_DISPLAY_ID) {
                useVdd = true
            } else if (selectedId >= 0 && selectedId < (availableDisplays?.size ?: 0)) {
                val selectedDisplay = availableDisplays!![selectedId]
                displayGuid = selectedDisplay.guid.ifEmpty { selectedDisplay.name }
                useVdd = false
            }
        }

            doStartStream(app, displayGuid, useVdd, forceResumeCurrentSession)
    }

    // ==================== 顶部下拉面板 ====================

    /**
     * 切换顶部下拉面板的显示/隐藏
     */
    private fun toggleTopPanel() {
        if (isPanelOpen) {
            closeTopPanel()
        } else {
            openTopPanel()
        }
    }

    /**
     * 打开顶部面板 (带动画)
     */
    @SuppressLint("SetTextI18n")
    private fun openTopPanel() {
        if (isPanelOpen) return
        isPanelOpen = true

        // 更新手柄箭头方向 ▴
        val toggle = findViewById<TextView>(R.id.topPanelToggle)
        toggle?.text = "\u2699 \u25B4"

        topPanelScrim.visibility = View.VISIBLE
        topPanelScrim.bringToFront()
        toggle?.bringToFront()
        topDropdownPanel.bringToFront()
        topDropdownPanel.scrollTo(0, 0)
        topDropdownPanel.alpha = 0f
        topDropdownPanel.translationY = -20f
        topDropdownPanel.visibility = View.VISIBLE
        constrainTopPanelHeight()
        topDropdownPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // 面板打开后将焦点移入第一个可聚焦元素（控制器友好）
                    val settingsEntry = findViewById<View>(R.id.settingsEntry)
                    settingsEntry?.requestFocus()
                }
                .start()
    }

    /**
     * 关闭顶部面板 (带动画)
     */
    @SuppressLint("CutPasteId", "SetTextI18n")
    private fun closeTopPanel() {
        if (!isPanelOpen) return
        isPanelOpen = false

        // 恢复手柄箭头方向 ▾
        val toggle = findViewById<TextView>(R.id.topPanelToggle)
        toggle?.text = "\u2699 \u25BE"

        topDropdownPanel.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(150)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    topDropdownPanel.visibility = View.GONE
                    topDropdownPanel.translationY = 0f
                    topPanelScrim.visibility = View.GONE
                    // 关闭后将焦点还给触发手柄
                    val toggleView = findViewById<View>(R.id.topPanelToggle)
                    toggleView?.requestFocus()
                }
                .start()
    }

    private fun constrainTopPanelHeight() {
        topDropdownPanel.post {
            val rootView = findViewById<View>(android.R.id.content) ?: return@post
            val availableHeight = rootView.height - topDropdownPanel.top - dp(16)
            val preferredMaxHeight = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                (rootView.height * 0.68f).toInt()
            } else {
                (rootView.height * 0.42f).toInt()
            }
            val maxHeight = kotlin.math.min(availableHeight, preferredMaxHeight).coerceAtLeast(dp(120))
            val contentHeight = (topDropdownPanel.getChildAt(0)?.measuredHeight ?: 0) +
                    topDropdownPanel.paddingTop + topDropdownPanel.paddingBottom
            val targetHeight = if (contentHeight > maxHeight) {
                maxHeight
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val params = topDropdownPanel.layoutParams
            if (params.height != targetHeight) {
                params.height = targetHeight
                topDropdownPanel.layoutParams = params
            }
        }
    }

    private fun setupAppBackgroundModeControls() {
        appBackgroundModeGroup.check(
            when (appBackgroundMode) {
                AppBackgroundMode.Artwork -> R.id.appBackgroundModeArtwork
                AppBackgroundMode.SoftColor -> R.id.appBackgroundModeSoftColor
            }
        )
        appBackgroundModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.appBackgroundModeSoftColor -> AppBackgroundMode.SoftColor
                else -> AppBackgroundMode.Artwork
            }
            if (newMode == appBackgroundMode) return@setOnCheckedChangeListener

            appBackgroundMode = newMode
            getSharedPreferences(APPVIEW_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_BACKGROUND_MODE, newMode.prefValue)
                .apply()
            resolveCurrentBackgroundCandidate()?.let {
                requestAppBackground(it, debounce = false, force = true)
            }
        }
    }

    private fun readAppBackgroundMode(): AppBackgroundMode {
        val value = getSharedPreferences(APPVIEW_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_APP_BACKGROUND_MODE, null)
        return AppBackgroundMode.fromPrefValue(value)
    }

    private fun scheduleDisplayCheck() {
        displayCheckRunnable?.let { displayCheckHandler.removeCallbacks(it) }
        displayCheckRunnable = Runnable {
            displayCheckRunnable = null
            checkDisplaysAndUpdateUI()
        }
        displayCheckHandler.postDelayed(displayCheckRunnable!!, DISPLAY_CHECK_DELAY_MS)
    }

    private fun getHostHttpClient(): NvHTTP? {
        val comp = computer ?: return null
        val address = comp.activeAddress ?: return null
        val binder = managerBinder ?: return null
        val cert = comp.serverCert ?: return null
        val key = "${address}|${comp.httpsPort}|${cert.hashCode()}"

        synchronized(hostHttpLock) {
            if (hostHttpClient == null || hostHttpKey != key) {
                hostHttpClient = NvHTTP(
                    address, comp.httpsPort,
                    binder.getUniqueId(), "", cert,
                    PlatformBinding.getCryptoProvider(this@AppView)
                )
                hostHttpKey = key
                LimeLog.info("AppView HTTP client prepared for $address")
            }

            return hostHttpClient
        }
    }

    // ==================== 显示器选择 ====================

    /**
     * 检查显示器并更新UI
     */
    private fun checkDisplaysAndUpdateUI() {
        if (computer == null || computer?.activeAddress == null || managerBinder == null) {
            displaySelectionInfo.visibility = View.GONE
            constrainTopPanelHeight()
            return
        }

        uiScope.launch {
            try {
                val displays = withContext(Dispatchers.IO) {
                    getHostHttpClient()?.getDisplays() ?: emptyList()
                }
                if (displays.isNotEmpty()) {
                    updateDisplaySelectionUI(displays)
                } else {
                    displaySelectionInfo.visibility = View.GONE
                    constrainTopPanelHeight()
                }
            } catch (e: Exception) {
                LimeLog.warning("Failed to get displays: " + e.message)
                displaySelectionInfo.visibility = View.GONE
                constrainTopPanelHeight()
            }
        }
    }

    /**
     * 更新显示器选择UI
     *
     * @param displays 显示器列表
     */
    private fun updateDisplaySelectionUI(displays: List<DisplayInfo>) {
        availableDisplays = displays
        displayRadioGroup.removeAllViews()

        LimeLog.info("Displays: " + displays.size)

        // 添加所有物理显示器选项
        for (i in displays.indices) {
            val display = displays[i]
            val displayName = display.name.ifEmpty { "Display " + (display.index + 1) }
            LimeLog.info("Display " + (display.index + 1) + ": " + display.name + " (guid: " + display.guid + ")")

            displayRadioGroup.addView(createDisplayRadioButton(i, displayName))
        }

        displayRadioGroup.addView(createDisplayRadioButton(
                VIRTUAL_DISPLAY_ID,
                resources.getString(R.string.applist_menu_start_with_vdd)))

        displaySelectionInfo.visibility = View.VISIBLE
        displayRadioGroup.clearCheck()
        refreshScreenCombinationModeFromPreferences()
        constrainTopPanelHeight()
    }

    /**
     * 创建显示器选择单选按钮
     *
     * @param id 按钮ID
     * @param text 按钮文本
     * @return 配置好的单选按钮
     */
    private fun createDisplayRadioButton(id: Int, text: String): RadioButton {
        val radioButton = RadioButton(this)
        radioButton.id = id
        radioButton.layoutParams = RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        radioButton.minHeight = dp(32)
        radioButton.text = text
        radioButton.setTextColor(0xCCFFFFFF.toInt())
        radioButton.textSize = 12f
        radioButton.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        radioButton.buttonTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        radioButton.setPadding(0, 0, 20, 0)
        return radioButton
    }

    /**
     * 执行启动串流
     *
     * @param app 应用对象
     * @param displayName 选择的显示器名称，如果为null则不指定显示器
     * @param useVdd 是否使用VDD虚拟显示器
     */
    private fun doStartStream(app: AppObject, displayName: String?, useVdd: Boolean?, forceResumeCurrentSession: Boolean = false) {
        val comp = computer ?: run {
            Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
            return
        }
        val binder = managerBinder ?: run {
            Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
            return
        }

        if (PairStatePreflight.hasTrustedPairState(comp)) {
            launchStartStream(app, comp, binder, displayName, useVdd, forceResumeCurrentSession)
            return
        }

        uiScope.launch {
            if (PairStatePreflight.isConfirmedNotPaired(comp, binder, "Starting stream")) {
                if (!isFinishing && !isDestroyed) {
                    shortcutHelper.disableComputerShortcut(comp,
                            resources.getString(R.string.scut_not_paired))
                    Toast.makeText(this@AppView, resources.getText(R.string.scut_not_paired), Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            if (!isFinishing && !isDestroyed) {
                launchStartStream(app, comp, binder, displayName, useVdd, forceResumeCurrentSession)
            }
        }
    }

    private fun launchStartStream(
        app: AppObject,
        comp: ComputerDetails,
        binder: ComputerManagerService.ComputerManagerBinder,
        displayName: String?,
        useVdd: Boolean?,
        forceResumeCurrentSession: Boolean
    ) {
        if (appSettingsManager != null) {
            // 使用AppSettingsManager统一管理启动逻辑
            val startIntent = appSettingsManager?.createStartIntentWithLastSettingsIfEnabled(
                    this, app.app, comp, binder,
                    useVdd = useVdd,
                    forceResumeCurrentSession = forceResumeCurrentSession)
            if (displayName != null) {
                startIntent?.putExtra(Game.EXTRA_DISPLAY_NAME, displayName)
            }
            // 传递屏幕组合模式
            startIntent?.let { addScreenCombinationModeToIntent(it) }
            startIntent?.let { startActivity(it) }
        } else {
            // 回退到默认方式启动
            val startIntent = ServerHelper.createStartIntent(
                    this, app.app, comp, binder,
                    useVdd = useVdd,
                    forceResumeCurrentSession = forceResumeCurrentSession)
            if (displayName != null) {
                startIntent.putExtra(Game.EXTRA_DISPLAY_NAME, displayName)
            }
            addScreenCombinationModeToIntent(startIntent)
            startActivity(startIntent)
        }
    }

    /**
     * 将屏幕组合模式添加到 Intent
     */
    private fun addScreenCombinationModeToIntent(intent: Intent) {
        if (selectedScreenCombinationMode != -1) {
            intent.putExtra(Game.EXTRA_SCREEN_COMBINATION_MODE, selectedScreenCombinationMode)
        }
    }

    /**
     * 更新屏幕组合模式标签显示文本
     */
    private fun refreshScreenCombinationModeOptions() {
        currentModeNames = resources.getStringArray(R.array.screen_combination_mode_names)
        currentModeValues = resources.getStringArray(R.array.screen_combination_mode_values)
    }

    private fun refreshScreenCombinationModeFromPreferences() {
        refreshScreenCombinationModeOptions()
        selectedScreenCombinationMode = PreferenceConfiguration.readPreferences(this).screenCombinationMode
        updateScreenCombinationModeLabel()
        screenCombinationModeLabel.visibility = View.VISIBLE
    }

    private fun persistScreenCombinationMode() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putString(SCREEN_COMBINATION_MODE_PREF_KEY, selectedScreenCombinationMode.toString())
        }
    }

    private fun clearDisplaySelection() {
        displayRadioGroup.clearCheck()
        refreshScreenCombinationModeFromPreferences()
    }

    private fun updateScreenCombinationModeLabel() {
        if (currentModeNames == null || currentModeNames?.isEmpty() == true) {
            return
        }
        // 找到当前选中值对应的名称
        var currentName = currentModeNames!![0] // 默认第一项
        if (currentModeValues != null) {
            val targetValue = selectedScreenCombinationMode.toString()
            for (i in currentModeValues?.indices ?: IntRange.EMPTY) {
                if (currentModeValues!![i] == targetValue) {
                    currentName = currentModeNames!![i]
                    break
                }
            }
        }
        screenCombinationModeLabel.text = getString(R.string.screen_combination_mode_label, currentName)
    }

    /**
     * 打开屏幕组合模式全屏选择视图。
     */
    private fun showScreenCombinationModeView() {
        if (currentModeNames == null || currentModeValues == null) {
            return
        }

        if (isPanelOpen) {
            closeTopPanel()
        }

        val checkedIndex = findScreenCombinationModeIndex()
        val descriptions = resources.getStringArray(R.array.screen_combination_mode_descriptions)
        screenCombinationModeOverlay.removeAllViews()
        screenCombinationModeOverlay.addView(
            ScreenCombinationModePickerView(
                context = this,
                names = currentModeNames!!,
                descriptions = descriptions,
                values = currentModeValues!!,
                checkedIndex = checkedIndex,
                onClose = { hideScreenCombinationModeView() },
                onModeSelected = { modeValue ->
                    selectedScreenCombinationMode = modeValue
                    persistScreenCombinationMode()
                    updateScreenCombinationModeLabel()
                    hideScreenCombinationModeView()
                }
            ),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        screenCombinationModeOverlay.visibility = View.VISIBLE
        screenCombinationModeOverlay.requestFocus()
    }

    private fun hideScreenCombinationModeView() {
        screenCombinationModeOverlay.visibility = View.GONE
        screenCombinationModeOverlay.removeAllViews()
        screenCombinationModeLabel.requestFocus()
    }

    private fun findScreenCombinationModeIndex(): Int {
        val values = currentModeValues ?: return 0
        val targetValue = selectedScreenCombinationMode.toString()
        return values.indexOfFirst { it == targetValue }.takeIf { it >= 0 } ?: 0
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 获取当前使用的item宽度
     *
     * @return item宽度（像素）
     */
    private fun getCurrentItemWidth(): Int {
        // 获取当前显示模式
        val isLargeMode = isLargeItemMode()

        // 根据模式返回对应的宽度
        return if (isLargeMode) {
            // 大图标模式：180dp
            (180 * resources.displayMetrics.density).toInt()
        } else {
            // 小图标模式：120dp
            (120 * resources.displayMetrics.density).toInt()
        }
    }

    /**
     * 判断当前是否为大图标模式
     *
     * @return true为大图标模式，false为小图标模式
     */
    private fun isLargeItemMode(): Boolean {
        // 根据PreferenceConfiguration判断显示模式
        val prefs = PreferenceConfiguration.readPreferences(this)
        return !prefs.smallIconMode // smallIconMode为false表示大图标模式
    }

    /**
     * 检查并更新布局（竖屏时根据app数量调整行数）
     */
    private fun checkAndUpdateLayout(recyclerView: RecyclerView) {
        if (appGridAdapter == null) {
            return
        }

        // 检查LayoutManager是否已经设置
        if (recyclerView.layoutManager == null) {
            return
        }

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            val currentSpanCount = (recyclerView.layoutManager as GridLayoutManager).spanCount
            val optimalSpanCount = calculateOptimalSpanCount(orientation)

            if (currentSpanCount != optimalSpanCount) {
                // 需要更新布局
                val newGlm = GridLayoutManager(this, optimalSpanCount, GridLayoutManager.HORIZONTAL, false)
                recyclerView.layoutManager = newGlm
            }
        }

        // 屏幕旋转后，延迟重新计算选中框位置，等待布局完成
        if (selectionAnimator != null && selectedPosition >= 0) {
            recyclerView.post { selectionAnimator?.moveToPosition(selectedPosition, false) }
        }
    }

    private fun populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(cacheDir, "applist", uuidString))
            val applist = NvHTTP.getAppListByReader(StringReader(lastRawApplist!!))
            updateUiWithAppList(applist)
            LimeLog.info("Loaded applist from cache xxxx")
        } catch (e: IOException) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: $lastRawApplist")
                e.printStackTrace()
            }
            LimeLog.info("Loading applist from the network")
            // We'll need to load from the network
            loadAppsBlocking()
        } catch (e: XmlPullParserException) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: $lastRawApplist")
                e.printStackTrace()
            }
            LimeLog.info("Loading applist from the network")
            loadAppsBlocking()
        }
    }

    private fun loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, resources.getString(R.string.applist_refresh_title),
                resources.getString(R.string.applist_refresh_msg), true)
    }

    override fun onDestroy() {
        super.onDestroy()

        uiScope.cancel()
        SpinnerDialog.closeDialogs(this)
        Dialog.closeDialogs()

        // Cancel any pending image loading operations
        appGridAdapter?.cancelQueuedOperations()

        // Clear background image to prevent memory leaks
        backgroundImageManager?.clearBackground()

        // 清理防抖Handler
        if (backgroundChangeRunnable != null) {
            backgroundChangeHandler.removeCallbacks(backgroundChangeRunnable!!)
            backgroundChangeRunnable = null
        }
        displayCheckRunnable?.let { displayCheckHandler.removeCallbacks(it) }
        displayCheckRunnable = null

        managerBinder?.clearForegroundComputer(uuidString)
        if (managerBinder != null) {
            unbindService(serviceConnection)
        }

        // 清理AdapterRecyclerBridge
        if (currentAdapterBridge != null) {
            currentAdapterBridge?.cleanup()
            currentAdapterBridge = null
        }

        frameMetricsLogger?.stop()
        frameMetricsLogger = null
    }

    override fun onResume() {
        super.onResume()

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this)

        inForeground = true
        managerBinder?.setForegroundComputer(uuidString)
        refreshScreenCombinationModeFromPreferences()
        startComputerUpdates()

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsLogger?.stop(reportIfNeeded = false)
            frameMetricsLogger = FrameMetricsLogger(this, "AppView").also { it.start() }
        }
    }

    override fun onPause() {
        super.onPause()

        inForeground = false
        managerBinder?.clearForegroundComputer(uuidString)
        displayCheckRunnable?.let { displayCheckHandler.removeCallbacks(it) }
        displayCheckRunnable = null
        stopComputerUpdates()
        frameMetricsLogger?.stop()
        frameMetricsLogger = null
    }

    // ==================== 上下文菜单 ====================

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        var position = -1
        var targetView: View? = null

        if (menuInfo is AdapterContextMenuInfo) {
            // AbsListView的情况
            position = menuInfo.position
            targetView = menuInfo.targetView
        } else if (v is RecyclerView) {
            // RecyclerView的情况，需要从当前选中的位置获取
            if (appGridAdapter != null && selectedPosition >= 0 && selectedPosition < (appGridAdapter?.count ?: 0)) {
                position = selectedPosition
                val viewHolder = v.findViewHolderForAdapterPosition(selectedPosition)
                if (viewHolder != null) {
                    targetView = viewHolder.itemView
                }
            }
        } else if (selectedPosition >= 0) {
            position = selectedPosition
        }

        if (position < 0 || appGridAdapter == null || position >= (appGridAdapter?.count ?: 0)) return

        val selectedApp = appGridAdapter?.getItem(position) as AppObject

        menu.setHeaderTitle(selectedApp.app.appName)

        if (lastRunningAppId != 0) {
            if (lastRunningAppId == selectedApp.app.appId) {
                menu.add(Menu.NONE, START_OR_RESUME_ID, 1, resources.getString(R.string.applist_menu_resume))
                menu.add(Menu.NONE, QUIT_ID, 2, resources.getString(R.string.applist_menu_quit))
            } else {
                menu.add(Menu.NONE, START_WITH_QUIT, 1, resources.getString(R.string.applist_menu_quit_and_start))
            }
        }

        // Only show the hide checkbox if this is not the currently running app or it's already hidden
        if (lastRunningAppId != selectedApp.app.appId || selectedApp.isHidden) {
            // Add "Start with Last Settings" option if last settings exist
            if (appSettingsManager != null && computer?.uuid != null &&
                appSettingsManager?.hasLastSettings(computer?.uuid!!, selectedApp.app) == true) {
                menu.add(Menu.NONE, START_WITH_LAST_SETTINGS_ID, 1, resources.getString(R.string.applist_menu_start_with_last_settings))
            }

            val hideAppItem = menu.add(Menu.NONE, HIDE_APP_ID, 2, resources.getString(R.string.applist_menu_hide_app))
            hideAppItem.isCheckable = true
            hideAppItem.isChecked = selectedApp.isHidden
        }

        menu.add(Menu.NONE, VIEW_DETAILS_ID, 4, resources.getString(R.string.applist_menu_details))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Only add an option to create shortcut if box art is loaded
            // and when we're in grid-mode (not list-mode).
            if (targetView != null) {
                val appImageView = targetView.findViewById<ImageView>(R.id.grid_image)
                if (appImageView != null) {
                    // We have a grid ImageView, so we must be in grid-mode
                    val drawable = appImageView.drawable as? BitmapDrawable
                    if (drawable != null && drawable.bitmap != null) {
                        // We have a bitmap loaded too
                        menu.add(Menu.NONE, CREATE_SHORTCUT_ID, 5, resources.getString(R.string.applist_menu_scut))
                    }
                }
            }
        }
    }

    override fun onContextMenuClosed(menu: Menu) {
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val position: Int
        var targetView: View? = null

        val menuInfo = item.menuInfo
        if (menuInfo is AdapterContextMenuInfo) {
            // AbsListView的情况
            position = menuInfo.position
            targetView = menuInfo.targetView
        } else {
            // RecyclerView的情况，使用当前选中的位置
            position = selectedPosition
        }

        if (position < 0 || appGridAdapter == null || position >= (appGridAdapter?.count ?: 0)) return false

        val app = appGridAdapter?.getItem(position) as AppObject
        when (item.itemId) {
            START_WITH_QUIT -> {
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this,
                        { startStreamWithLastSettingsIfEnabled(app) },
                        null)
                return true
            }

            START_OR_RESUME_ID -> {
                startStreamWithLastSettingsIfEnabled(app, forceResumeCurrentSession = true)
                return true
            }

            START_WITH_LAST_SETTINGS_ID -> {
                startStreamWithLastSettingsIfEnabled(app)
                return true
            }

            QUIT_ID -> {
                val comp = computer ?: run {
                    Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
                    return true
                }
                val binder = managerBinder ?: run {
                    Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
                    return true
                }
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, {
                    suspendGridUpdates = true
                    ServerHelper.doQuit(this, comp, app.app, binder) {
                        // Trigger a poll immediately
                        suspendGridUpdates = false
                        poller?.pollNow()
                    }
                }, null)
                return true
            }

            VIEW_DETAILS_ID -> {
                Dialog.displayDetailsDialog(this@AppView, resources.getString(R.string.title_details), app.app.toString(), false)
                return true
            }

            HIDE_APP_ID -> {
                if (item.isChecked) {
                    // Transitioning hidden to shown
                    hiddenAppIds.remove(app.app.appId)
                } else {
                    // Transitioning shown to hidden
                    hiddenAppIds.add(app.app.appId)
                }
                updateHiddenApps(false)
                return true
            }

            CREATE_SHORTCUT_ID -> {
                val comp = computer ?: run {
                    Toast.makeText(this, resources.getText(R.string.lost_connection), Toast.LENGTH_SHORT).show()
                    return true
                }
                // 对于RecyclerView，我们需要从缓存中获取bitmap
                var appBitmap: Bitmap? = null

                // 首先尝试从目标视图获取bitmap
                if (targetView != null) {
                    val appImageView = targetView.findViewById<ImageView>(R.id.grid_image)
                    if (appImageView != null && appImageView.drawable is BitmapDrawable) {
                        val drawable = appImageView.drawable as BitmapDrawable
                        appBitmap = drawable.bitmap
                    }
                }

                // 如果从视图获取失败,尝试从缓存获取
                if (appBitmap == null && appGridAdapter != null && appGridAdapter?.getLoader() != null) {
                    val tuple = CachedAppAssetLoader.LoaderTuple(comp, app.app)
                    val cachedBitmap = appGridAdapter?.getLoader()?.getBitmapFromCache(tuple)
                    if (cachedBitmap != null) {
                        appBitmap = cachedBitmap.bitmap
                    }
                }

                // 创建快捷方式
                if (appBitmap != null) {
                    if (!shortcutHelper.createPinnedGameShortcut(comp, app.app, appBitmap)) {
                        Toast.makeText(this@AppView, resources.getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@AppView, resources.getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show()
                }
                return true
            }

            else -> return super.onContextItemSelected(item)
        }
    }

    private fun updateUiWithServerinfo(details: ComputerDetails) {
        runOnUiThread {
            var updated = false
            var hasRunningApp = false

            // Look through our current app list to tag the running app
            for (i in 0 until (appGridAdapter?.count ?: 0)) {
                val existingApp = appGridAdapter?.getItem(i) as AppObject

                // There can only be one or zero apps running.
                if (existingApp.isRunning &&
                        existingApp.app.appId == details.runningGameId) {
                    // This app was running and still is, so we're done now
                    // 但仍需要确保箭头可见
                    updateRestoreButtonVisibility(details.runningGameId != 0)
                    return@runOnUiThread
                } else if (existingApp.app.appId == details.runningGameId) {
                    // This app wasn't running but now is
                    hasRunningApp = true
                    existingApp.isRunning = true
                    updated = true
                } else if (existingApp.isRunning) {
                    // This app was running but now isn't
                    existingApp.isRunning = false
                    updated = true
                } else {
                    // This app wasn't running and still isn't
                }
            }

            if (updated) {
                appGridAdapter?.notifyDataSetChanged()
                if (selectedPosition < 0) {
                    requestAppBackground(resolveCurrentBackgroundCandidate(), debounce = false)
                }
            }

            // 根据是否有运行中的应用来显示/隐藏恢复按钮
            updateRestoreButtonVisibility(details.runningGameId != 0)
        }
    }

    private fun updateUiWithAppList(appList: List<NvApp>) {
        runOnUiThread {
            // Prepare list of AppObjects in server order
            val newAppObjects = ArrayList<AppObject>()

            // Create AppObjects from server list, preserving order
            for (app in appList) {
                // Look for existing AppObject to preserve running state
                var existingApp: AppObject? = null
                for (i in 0 until (appGridAdapter?.count ?: 0)) {
                    val candidate = appGridAdapter?.getItem(i) as AppObject
                    if (candidate.app.appId == app.appId) {
                        existingApp = candidate
                        // Update app properties if needed
                        if (candidate.app.appName != app.appName) {
                            candidate.app.appName = app.appName
                        }
                        break
                    }
                }

                if (existingApp != null) {
                    // Use existing AppObject to preserve state (like isRunning)
                    newAppObjects.add(existingApp)
                } else {
                    // Create new AppObject for new app
                    val newAppObject = AppObject(app)
                    newAppObjects.add(newAppObject)

                    // Enable shortcuts for new apps
                    shortcutHelper.enableAppShortcut(computer!!, app)
                }
            }

            // Handle removed apps - disable shortcuts
            for (i in 0 until (appGridAdapter?.count ?: 0)) {
                val existingApp = appGridAdapter?.getItem(i) as AppObject
                var stillExists = false

                for (app in appList) {
                    if (existingApp.app.appId == app.appId) {
                        stillExists = true
                        break
                    }
                }

                if (!stillExists) {
                    shortcutHelper.disableAppShortcut(computer!!, existingApp.app, "App removed from PC")
                }
            }

            // Rebuild the entire list in server order
            appGridAdapter?.rebuildAppList(newAppObjects)
            appGridAdapter?.notifyDataSetChanged()

            // Establish the initial background from the same priority path used by focus changes.
            setFirstAppAsBackground(newAppObjects)

            // 检查并更新布局（竖屏时根据app数量调整行数）
            if (currentRecyclerView != null) {
                checkAndUpdateLayout(currentRecyclerView!!)

                // 重新计算居中布局
                val orientation = resources.configuration.orientation
                val spanCount = calculateOptimalSpanCount(orientation)
                setupCenterAlignment(currentRecyclerView!!, spanCount)
            }
        }
    }

    private fun setFirstAppAsBackground(appObjects: List<AppObject>) {
        // Check if activity is still valid
        if (isFinishing || isDestroyed) {
            return
        }

        // Only set background if we don't have one already and there are apps
        if (backgroundImageManager?.hasBackground != true &&
            appObjects.isNotEmpty() &&
            appBackgroundImageBlur != null) {

            requestAppBackground(resolveCurrentBackgroundCandidate(), debounce = false)
        }
    }

    override fun getAdapterFragmentLayoutId(): Int {
        return if (PreferenceConfiguration.readPreferences(this@AppView).smallIconMode)
            R.layout.app_grid_view_small else R.layout.app_grid_view
    }

    fun receiveAbsListView(listView: AbsListView) {
        // Backwards-compatible wrapper: if a RecyclerView was passed as a View,
        // AdapterFragmentCallbacks signature was generalized but compile-time this
        // method remains for binary compat. Delegate to the View-based method.
        receiveAdapterView(listView)
    }

    override fun receiveAbsListView(gridView: View?) {
        // Implementation for the generalized interface method
        if (gridView == null) {
            LimeLog.warning("AdapterFragment callback did not include a fragment view; ignoring setup")
            return
        }

        receiveAdapterView(gridView)
    }

    // New generalized receiver to accept RecyclerView or legacy AbsListView
    fun receiveAdapterView(view: View) {
        if (appGridAdapter == null) {
            pendingAdapterFragmentView = view
            LimeLog.warning("AdapterFragment callback arrived before AppView adapter initialization; deferring setup")
            return
        }

        pendingAdapterFragmentView = null

        if (view is RecyclerView) {
            setupRecyclerView(view)
        } else if (view is AbsListView) {
            setupAbsListView(view)
        }
    }

    // ==================== RecyclerView 设置 ====================

    private fun setupRecyclerView(rv: RecyclerView) {
        val adapter = appGridAdapter ?: run {
            pendingAdapterFragmentView = rv
            return
        }

        currentRecyclerView = rv

        // 更新selectionAnimator的RecyclerView和Adapter引用
        selectionAnimator?.updateReferences(rv, adapter)

        // 创建并设置bridge adapter
        setupBridgeAdapter(rv)

        // 配置布局管理器
        setupLayoutManager(rv)

        // 优化RecyclerView性能
        optimizeRecyclerViewPerformance(rv)

        // 设置事件监听器
        setupRecyclerViewListeners(rv)

        // 应用UI配置
        UiHelper.applyStatusBarPadding(rv)
        registerForContextMenu(rv)
    }

    /**
     * 将焦点设置到第一个应用上
     */
    private fun focusFirstApp(rv: RecyclerView) {
        // 确保布局完成后再设置焦点
        rv.post {
            // 再次延迟，确保所有布局计算都已完成
            rv.postDelayed({
                if (appGridAdapter != null && (appGridAdapter?.count ?: 0) > 0) {
                    val holder = rv.findViewHolderForAdapterPosition(0)
                    if (holder != null) {
                        // 确保itemView已经完成布局测量
                        if (holder.itemView.width > 0 && holder.itemView.height > 0) {
                            holder.itemView.requestFocus()
                            // 触发选中状态变化
                            val app = appGridAdapter?.getItem(0) as AppObject
                            handleSelectionChange(0, app)
                        } else {
                            // 如果布局还未完成，再次延迟
                            rv.postDelayed({ focusFirstApp(rv) }, 50)
                        }
                    }
                }
            }, 100)
        }
    }

    private fun setupBridgeAdapter(rv: RecyclerView) {
        val bridge = AdapterRecyclerBridge(this, appGridAdapter)
        rv.adapter = bridge

        // 清理之前的bridge并保存新的引用
        if (currentAdapterBridge != null) {
            currentAdapterBridge?.cleanup()
        }
        currentAdapterBridge = bridge

        // 设置点击监听器
        bridge.setOnItemClickListener { position, item -> handleItemClick(position, item) }

        // 设置按键监听器
        bridge.setOnItemKeyListener { position, item, keyCode, event -> handleItemKey(position, item, keyCode, event) }

        // 设置长按监听器
        bridge.setOnItemLongClickListener { position, item -> handleItemLongClick(position, item) }
    }

    private fun setupLayoutManager(rv: RecyclerView) {
        val orientation = resources.configuration.orientation
        val spanCount = calculateOptimalSpanCount(orientation)
        val glm = GridLayoutManager(this, spanCount, GridLayoutManager.HORIZONTAL, false)
        rv.layoutManager = glm

        // 设置预加载
        glm.initialPrefetchItemCount = (spanCount * 4).coerceAtLeast(4)

        // 设置居中布局，并标记需要在布局完成后聚焦第一个应用
        setupCenterAlignment(rv, spanCount, true)
    }

    /**
     * 设置RecyclerView的居中对齐
     */
    private fun setupCenterAlignment(rv: RecyclerView, spanCount: Int) {
        setupCenterAlignment(rv, spanCount, false)
    }

    /**
     * 设置RecyclerView的居中对齐
     * @param rv RecyclerView
     * @param spanCount 列数
     * @param shouldFocusFirstApp 是否在布局完成后聚焦第一个应用
     */
    private fun setupCenterAlignment(rv: RecyclerView, spanCount: Int, shouldFocusFirstApp: Boolean) {
        rv.post {
            if (appGridAdapter == null) {
                return@post
            }

            val itemCount = appGridAdapter?.count ?: 0
            val totalRows = ceil(itemCount.toDouble() / spanCount).toInt()
            val screenWidth = resources.displayMetrics.widthPixels
            var actualItemSize = getCurrentItemWidth()

            // 如果RecyclerView已经有子视图,优先使用实际测量的尺寸
            if (rv.isNotEmpty()) {
                val firstChild = rv.getChildAt(0)
                if (firstChild != null && firstChild.width > 0) {
                    actualItemSize = firstChild.width
                }
            }

            // 计算并设置居中padding
            val totalWidth = actualItemSize * totalRows
            val horizontalPadding = if (totalWidth < screenWidth) (screenWidth - totalWidth) / 2 else 0
            rv.setPadding(horizontalPadding, rv.paddingTop, horizontalPadding, rv.paddingBottom)

            // 如果需要聚焦第一个应用，等待布局完成后再设置焦点和聚焦框位置
            if (shouldFocusFirstApp) {
                rv.post {
                    // 再次延迟，确保padding生效后布局完全完成
                    rv.postDelayed({
                        if (isFirstFocus && appGridAdapter != null && (appGridAdapter?.count ?: 0) > 0) {
                            focusFirstApp(rv)
                        }
                    }, 50)
                }
            }
        }
    }

    private fun optimizeRecyclerViewPerformance(rv: RecyclerView) {
        // 基础性能优化
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(24)
        rv.isNestedScrollingEnabled = false

        // 滑动性能优化
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        rv.itemAnimator = null

        // 回收池优化
        val pool = rv.recycledViewPool
        pool.setMaxRecycledViews(0, 32)
    }

    private fun setupRecyclerViewListeners(rv: RecyclerView) {
        // 添加滚动监听器
        rv.addOnScrollListener(createScrollListener())

        // 添加子项焦点变化监听
        rv.addOnChildAttachStateChangeListener(createChildAttachStateChangeListener(rv))
    }

    private fun createScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    selectionAnimator?.showIndicator()
                    updateSelectionPosition()
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    selectionAnimator?.hideIndicator()
                }
            }
        }
    }

    private fun createChildAttachStateChangeListener(rv: RecyclerView): RecyclerView.OnChildAttachStateChangeListener {
        return object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (!hasFocus) return@OnFocusChangeListener

                    // 延迟处理焦点变化，确保点击事件优先处理
                    v.post {
                        if (!v.hasFocus()) return@post

                        val pos = rv.getChildAdapterPosition(v)
                        if (pos < 0 || pos >= (appGridAdapter?.count ?: 0)) return@post

                        val app = appGridAdapter?.getItem(pos) as AppObject
                        handleSelectionChange(pos, app)
                    }
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                view.onFocusChangeListener = null
            }
        }
    }

    // ==================== 事件处理 ====================

    private fun handleItemClick(position: Int, item: Any) {
        val app = item as AppObject
        handleSelectionChange(position, app)

        if (lastRunningAppId != 0) {
            showContextMenuForPosition(position)
        } else {
            startStreamWithLastSettingsIfEnabled(app)
        }
    }

    private fun handleItemKey(position: Int, item: Any, keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (event.action != android.view.KeyEvent.ACTION_DOWN) {
            return false
        }

        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X ||
                keyCode == android.view.KeyEvent.KEYCODE_BUTTON_Y) {
            val app = item as AppObject
            handleSelectionChange(position, app)
            showContextMenuForPosition(position)
            return true
        }

        return false
    }

    private fun handleItemLongClick(position: Int, item: Any): Boolean {
        val app = item as AppObject
        handleSelectionChange(position, app)
        return showContextMenuForPosition(position)
    }

    private fun showContextMenuForPosition(position: Int): Boolean {
        if (currentRecyclerView == null) return false

        val viewHolder = currentRecyclerView?.findViewHolderForAdapterPosition(position)
        if (viewHolder != null) {
            openContextMenu(viewHolder.itemView)
            return true
        }
        return false
    }

    private fun updateSelectionPosition() {
        if (selectedPosition >= 0 && selectionAnimator != null) {
            // 尝试更新到当前选中位置
            val positionUpdated = selectionAnimator?.updatePosition(selectedPosition)

            // 如果更新失败（item滑出屏幕外），隐藏焦点框
            if (positionUpdated != true) {
                selectionAnimator?.hideIndicator()
            }
        }
    }

    private fun setupAbsListView(listView: AbsListView) {
        val adapter = appGridAdapter ?: run {
            pendingAdapterFragmentView = listView
            return
        }

        listView.setAdapter(adapter)
        listView.setOnItemClickListener { _, _, pos, _ ->
            val app = adapter.getItem(pos) as AppObject
            handleSelectionChange(pos, app)

            if (lastRunningAppId != 0) {
                openContextMenu(listView)
            } else {
                startStreamWithLastSettingsIfEnabled(app)
            }
        }

        UiHelper.applyStatusBarPadding(listView)
        registerForContextMenu(listView)
    }

    // ==================== 顶部面板 - 事件处理 ====================

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // 面板打开时点击外部自动关闭
        if (isPanelOpen && ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val x = ev.rawX
            val y = ev.rawY
            if (!isTouchInsideView(topDropdownPanel, x, y)
                    && !isTouchInsideView(findViewById(R.id.topPanelToggle), x, y)) {
                closeTopPanel()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (screenCombinationModeOverlay.isVisible && (keyCode == android.view.KeyEvent.KEYCODE_BACK
                || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B)) {
            hideScreenCombinationModeView()
            return true
        }

        // 面板打开时按返回键/B键关闭面板而非退出界面
        if (isPanelOpen && (keyCode == android.view.KeyEvent.KEYCODE_BACK
                || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B)) {
            closeTopPanel()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 判断触摸点是否在指定 View 的范围内
     */
    private fun isTouchInsideView(view: View?, x: Float, y: Float): Boolean {
        if (view == null) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width
                && y >= location[1] && y <= location[1] + view.height
    }

    // ==================== 内部类 ====================

    private enum class AppBackgroundMode(val prefValue: String) {
        Artwork("artwork"),
        SoftColor("soft_color");

        companion object {
            fun fromPrefValue(value: String?): AppBackgroundMode =
                values().firstOrNull { it.prefValue == value } ?: Artwork
        }
    }

    class AppObject(val app: NvApp) {
        var isRunning = false
        var isHidden = false

        override fun toString(): String {
            return app.appName
        }
    }
}
