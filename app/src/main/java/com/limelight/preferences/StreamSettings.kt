@file:Suppress("DEPRECATION")
package com.limelight.preferences

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.DisplayCutout
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.limelight.LimeLog
import com.limelight.PcView
import com.limelight.R
import com.limelight.ExternalDisplayManager
import com.limelight.TargetDisplayResolver
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.share.CrownProfileShareManager
import com.limelight.binding.input.advance_setting.share.GitHubCrownProfileStorePublisher
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.handbook.HandbookLauncher
import com.limelight.utils.AboutDialogLauncher
import com.limelight.utils.AspectRatioConverter
import com.limelight.utils.ConfigurationSyncManager
import com.limelight.utils.ConfigurationSyncScheduler
import com.limelight.utils.Dialog
import com.limelight.utils.AppDialogStyler
import com.limelight.utils.HdrCapabilityHelper
import com.limelight.ui.ScreenCombinationModePickerView
import com.limelight.utils.UiHelper
import com.limelight.utils.UpdateManager

import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.ColorFilterTransformation

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.sqrt

class StreamSettings : AppCompatActivity() {

    private lateinit var previousPrefs: PreferenceConfiguration
    private var previousDisplayPixelCount = 0
    private var externalDisplayManager: ExternalDisplayManager? = null

    // 抽屉菜单相关
    private var drawerLayout: DrawerLayout? = null // 竖屏时使用，横屏时为 null
    private var categoryList: RecyclerView? = null
    private var categoryAdapter: CategoryAdapter? = null
    private val categories: MutableList<CategoryItem> = ArrayList()
    private var selectedCategoryIndex = 0

    // 搜索栏相关（仅竖屏 layout 提供，横屏 layout 不渲染搜索控件）
    private var searchBar: View? = null
    private var searchInput: EditText? = null
    private var searchToggle: ImageView? = null
    private var menuToggleView: ImageView? = null
    private var screenCombinationModeReturnFocus: View? = null
    private var lastNightMode = false

    // 状态保存键
    companion object {
        private const val KEY_SELECTED_CATEGORY = "selected_category_index"
        private const val REQUEST_CODE_PICK_FRAMEGEN_DLL = 4
        private const val REQUEST_CODE_CREATE_CONFIG_SYNC = 5
        private const val REQUEST_CODE_OPEN_CONFIG_SYNC = 6
        private const val REQUEST_CODE_OPEN_CONFIG_SYNC_DIRECTORY = 7
        private const val REQUEST_CODE_CREATE_CROWN_SHARE = 8
        private const val REQUEST_CODE_OPEN_CROWN_SHARE = 9
        private const val CROWN_STORE_INDEX_URL = "https://raw.githubusercontent.com/qiin2333/crown-profiles/main/index/v1.json"
        private const val CROWN_STORE_MAX_INDEX_BYTES = 256 * 1024
        private const val CROWN_SHARE_MAX_DOWNLOAD_BYTES = 512 * 1024

        // HACK for Android 9
        var displayCutoutP: DisplayCutout? = null

        /**
         * 获取分类对应的 Phosphor 矢量图标资源 ID（与鸿蒙项目一致）。
         */
        private fun getIconForCategory(key: String): Int {
            return when (key) {
                "category_basic_settings" -> R.drawable.phc_settings
                "category_screen_position" -> R.drawable.phc_video_camera
                "category_display_behavior" -> R.drawable.phc_perf_resolution
                "category_audio_settings" -> R.drawable.phc_audio
                "category_gamepad_settings" -> R.drawable.phc_gamepad
                "category_input_settings" -> R.drawable.phc_keyboard
                "category_enhanced_touch" -> R.drawable.phc_touch
                "category_onscreen_controls" -> R.drawable.phc_game_controller
                "category_float_ball" -> R.drawable.phc_eye
                "category_crown_features" -> R.drawable.phc_crown
                "category_host_settings" -> R.drawable.phc_host
                "category_connection_settings" -> R.drawable.phc_plug
                "category_ui_settings" -> R.drawable.phc_lightbulb
                "category_advanced_settings" -> R.drawable.phc_settings    // legacy
                "category_advanced_features" -> R.drawable.phc_lightning   // 性能与流畅度
                "category_help" -> R.drawable.phc_info
                else -> R.drawable.phc_list
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    fun reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mode = windowManager.defaultDisplay.mode
            previousDisplayPixelCount = mode.physicalWidth * mode.physicalHeight
        }
        supportFragmentManager.beginTransaction().replace(
                R.id.preference_container, SettingsFragment()
        ).commitAllowingStateLoss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用带阴影的主题
        theme.applyStyle(R.style.PreferenceThemeWithShadow, true)

        super.onCreate(savedInstanceState)
        ConfigurationSyncScheduler.runNow(this)

        previousPrefs = PreferenceConfiguration.readPreferences(this)

        // 初始化外接显示器管理器
        if (previousPrefs.useExternalDisplay) {
            externalDisplayManager = ExternalDisplayManager(
                this,
                previousPrefs,
                TargetDisplayResolver(this)
            )
            externalDisplayManager?.initialize()
        }

        UiHelper.setLocale(this)

        // 设置自定义布局
        setContentView(R.layout.activity_stream_settings)
        lastNightMode = isNightMode()

        // 启用沉浸式顶栏：内容延伸到状态栏/导航栏下方，系统栏完全透明
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        applySettingsThemeSurfaces()

        UiHelper.notifyNewRootView(this)
        applyNavigationBarInsets()

        // 恢复保存的状态（屏幕旋转时）
        if (savedInstanceState != null) {
            selectedCategoryIndex = savedInstanceState.getInt(KEY_SELECTED_CATEGORY, 0)
        }

        // 初始化抽屉菜单
        initDrawerMenu()

        // 加载背景图片
        loadBackgroundImage()

        // 设置版本号
        setupVersionInfo()
    }

    private fun applyNavigationBarInsets() {
        val insetSource = findViewById<View>(R.id.drawer_layout)
            ?: findViewById(R.id.settings_layout_root)
        val drawerMenu = findViewById<View>(R.id.drawer_menu)
        val preferenceContainer = findViewById<View>(R.id.preference_container)
        val initialDrawerPaddingLeft = drawerMenu.paddingLeft
        val initialDrawerPaddingTop = drawerMenu.paddingTop
        val initialDrawerPaddingRight = drawerMenu.paddingRight
        val initialDrawerPaddingBottom = drawerMenu.paddingBottom
        val initialPaddingLeft = preferenceContainer.paddingLeft
        val initialPaddingTop = preferenceContainer.paddingTop
        val initialPaddingRight = preferenceContainer.paddingRight
        val initialPaddingBottom = preferenceContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(insetSource) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val safeLeft = maxOf(systemBars.left, displayCutout.left)
            val safeRight = maxOf(systemBars.right, displayCutout.right)
            val safeBottom = maxOf(systemBars.bottom, displayCutout.bottom)

            if (isLandscape) {
                drawerMenu.setPadding(
                    initialDrawerPaddingLeft + safeLeft,
                    initialDrawerPaddingTop,
                    initialDrawerPaddingRight,
                    initialDrawerPaddingBottom + safeBottom
                )
            }

            preferenceContainer.setPadding(
                initialPaddingLeft,
                initialPaddingTop,
                initialPaddingRight + if (isLandscape) safeRight else 0,
                initialPaddingBottom + if (isLandscape) safeBottom else 0
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(insetSource)
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySettingsThemeSurfaces() {
        findViewById<View>(R.id.settingsBackgroundOverlay)?.setBackgroundColor(
                ContextCompat.getColor(this, R.color.settings_background_overlay)
        )
        val useDarkSystemIcons = !isNightMode()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkSystemIcons
            isAppearanceLightNavigationBars = useDarkSystemIcons
        }
    }

    /**
     * 设置版本号显示
     */
    @SuppressLint("SetTextI18n")
    private fun setupVersionInfo() {
        val versionText = findViewById<TextView>(R.id.drawer_version) ?: return
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = "v$versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            versionText.visibility = View.GONE
        }
    }

    /**
     * 初始化抽屉菜单
     * 竖屏使用 DrawerLayout，横屏使用并排布局
     */
    private fun initDrawerMenu() {
        // 横屏布局使用独立根容器 ID，因此这里只会取得竖屏 DrawerLayout。
        drawerLayout = findViewById(R.id.drawer_layout)

        categoryList = findViewById(R.id.category_list)

        setupMenuToggle()
        setupCategoryList()
        setupDrawerListener()
        setupSearchBar()
    }

    /**
     * 设置菜单按钮（仅竖屏有效）
     */
    private fun setupMenuToggle() {
        val menuToggle = findViewById<ImageView>(R.id.settings_menu_toggle) ?: return
        menuToggle.setOnClickListener { openDrawer() }
        menuToggle.isFocusable = true
        menuToggle.isFocusableInTouchMode = false
    }

    /**
     * 设置浮动搜索按钮 + 顶部搜索栏（仅竖屏 layout 提供这些 view，
     * 横屏 layout 不包含搜索控件，findViewById 返回 null，自动跳过）。
     */
    private fun setupSearchBar() {
        searchBar = findViewById(R.id.settings_search_bar)
        searchInput = findViewById(R.id.settings_search_input)
        searchToggle = findViewById(R.id.settings_search_toggle)
        menuToggleView = findViewById(R.id.settings_menu_toggle)
        val closeBtn = findViewById<ImageView?>(R.id.settings_search_close)

        // 横屏布局没有这些控件
        if (searchBar == null || searchInput == null || searchToggle == null) return

        searchToggle?.setOnClickListener { showSearchBar() }
        closeBtn?.setOnClickListener { hideSearchBar() }

        searchInput?.doAfterTextChanged { applyFilterToFragment(it?.toString().orEmpty()) }

        // IME 回车：仅收起键盘，保留过滤结果
        searchInput?.setOnEditorActionListener { v, _, _ ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
            v.clearFocus()
            true
        }
    }

    private val isSearchBarVisible: Boolean
        get() = searchBar?.visibility == View.VISIBLE

    private fun fadeIn(v: View?) {
        v ?: return
        v.alpha = 0f
        v.visibility = View.VISIBLE
        v.animate().alpha(1f).setDuration(180L).start()
    }

    private fun fadeOut(v: View?) {
        v ?: return
        v.animate().alpha(0f).setDuration(140L).withEndAction { v.visibility = View.GONE }.start()
    }

    private fun showSearchBar() {
        fadeOut(searchToggle)
        fadeOut(menuToggleView)
        fadeIn(searchBar)
        searchInput?.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearchBar() {
        searchInput?.setText("")
        applyFilterToFragment("")
        fadeOut(searchBar)
        fadeIn(searchToggle)
        fadeIn(menuToggleView)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput?.windowToken, 0)
    }

    private fun applyFilterToFragment(query: String) {
        val fragment = supportFragmentManager
                .findFragmentById(R.id.preference_container) as? SettingsFragment
        fragment?.applySearchFilter(query)
    }

    /**
     * 设置分类列表
     */
    private fun setupCategoryList() {
        val list = categoryList ?: return
        list.layoutManager = LinearLayoutManager(this)
        categoryAdapter = CategoryAdapter()
        list.adapter = categoryAdapter
        list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        list.isFocusable = true
    }

    /**
     * 设置抽屉监听器（仅竖屏有效）
     */
    private fun setupDrawerListener() {
        if (drawerLayout == null) return

        drawerLayout?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                focusSelectedCategory()
            }

            override fun onDrawerClosed(drawerView: View) {
                focusPreferenceList()
            }
        })
    }

    /**
     * 打开抽屉（仅竖屏有效）
     */
    private fun openDrawer() {
        drawerLayout?.openDrawer(findViewById(R.id.drawer_menu))
    }

    /**
     * 聚焦到选中的分类项
     */
    private fun focusSelectedCategory() {
        if (categoryList != null && categoryAdapter != null && (categoryAdapter?.itemCount ?: 0) > 0) {
            categoryList?.post {
                val vh = categoryList?.findViewHolderForAdapterPosition(selectedCategoryIndex)
                vh?.itemView?.requestFocus()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UpdateManager.INSTALL_PERMISSION_REQUEST_CODE) {
            UpdateManager.onInstallPermissionResult(this)
        }
    }

    /**
     * 聚焦到设置列表
     */
    private fun focusPreferenceList() {
        val preferenceContainer = findViewById<View>(R.id.preference_container)
        preferenceContainer?.requestFocus()
    }

    fun showScreenCombinationModePicker(preference: ListPreference) {
        val entries = preference.entries ?: return
        val values = preference.entryValues ?: return
        val overlay = findViewById<FrameLayout>(R.id.screen_combination_mode_overlay) ?: return
        val currentValue = preference.value ?: values.firstOrNull()?.toString().orEmpty()
        val checkedIndex = values.indexOfFirst { it.toString() == currentValue }.let { index ->
            if (index >= 0) index else 0
        }

        screenCombinationModeReturnFocus = currentFocus
        overlay.removeAllViews()
        overlay.addView(
            ScreenCombinationModePickerView(
                context = this,
                names = Array(entries.size) { index -> entries[index].toString() },
                descriptions = resources.getStringArray(R.array.screen_combination_mode_descriptions),
                values = Array(values.size) { index -> values[index].toString() },
                checkedIndex = checkedIndex,
                onClose = { hideScreenCombinationModePicker() },
                onModeSelected = { modeValue ->
                    val newValue = modeValue.toString()
                    if (preference.callChangeListener(newValue)) {
                        preference.value = newValue
                    }
                    hideScreenCombinationModePicker()
                }
            ),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlay.visibility = View.VISIBLE
        overlay.requestFocus()
    }

    private fun hideScreenCombinationModePicker(): Boolean {
        val overlay = findViewById<FrameLayout>(R.id.screen_combination_mode_overlay) ?: return false
        if (overlay.visibility != View.VISIBLE) {
            return false
        }

        overlay.visibility = View.GONE
        overlay.removeAllViews()
        val returnFocus = screenCombinationModeReturnFocus
        screenCombinationModeReturnFocus = null
        if (returnFocus?.isAttachedToWindow == true && returnFocus.isShown && returnFocus.isFocusable) {
            returnFocus.post { returnFocus.requestFocus() }
        } else {
            focusPreferenceList()
        }
        return true
    }

    /**
     * dp 转 px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * 分类数据项
     */
    class CategoryItem(var key: String, var title: String, var iconRes: Int)

    /**
     * 分类菜单适配器
     */
    inner class CategoryAdapter : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.category_title)
            val icon: ImageView = itemView.findViewById(R.id.category_icon)
            val indicator: View = itemView.findViewById(R.id.category_indicator)
            val root: View = itemView.findViewById(R.id.category_item_root)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category_menu, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = categories[position]
            // Phosphor 矢量图标 + 文本（图标颜色随选中态在 updateItemAppearance 中切换）
            holder.icon.setImageResource(item.iconRes)
            holder.title.text = item.title

            // 高亮选中项
            val isSelected = position == selectedCategoryIndex
            updateItemAppearance(holder, isSelected, false)

            // 点击事件
            holder.root.setOnClickListener { selectCategory(holder.bindingAdapterPosition, item) }

            // 焦点变化事件（控制器支持）
            holder.root.setOnFocusChangeListener { _, hasFocus ->
                val selected = holder.bindingAdapterPosition == selectedCategoryIndex
                updateItemAppearance(holder, selected, hasFocus)
            }
        }

        /**
         * 更新菜单项的外观（选中/焦点状态）- 精致风格
         */
        private fun updateItemAppearance(holder: ViewHolder, isSelected: Boolean, hasFocus: Boolean) {
            // 使用项目公共粉色主题
            val accentColor = androidx.core.content.ContextCompat.getColor(this@StreamSettings, R.color.ui_shell_accent)
            val primaryText = ContextCompat.getColor(this@StreamSettings, R.color.ui_shell_text_primary)
            val secondaryText = ContextCompat.getColor(this@StreamSettings, R.color.ui_shell_text_secondary)
            val subtleText = ContextCompat.getColor(this@StreamSettings, R.color.ui_shell_outline_strong)

            val isLandscapeSidebar = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            // 横屏常驻侧栏通过图标和文字颜色表示状态，释放装饰元素占用的标题空间。
            holder.indicator.visibility = when {
                isLandscapeSidebar -> View.GONE
                isSelected -> View.VISIBLE
                else -> View.INVISIBLE
            }

            // 文字 + 图标颜色三态切换
            val textColor: Int; val textAlpha: Float; val iconColor: Int; val iconAlpha: Float
            when {
                isSelected -> { textColor = primaryText; textAlpha = 1.0f; iconColor = accentColor; iconAlpha = 1.0f }
                hasFocus   -> { textColor = accentColor; textAlpha = 1.0f; iconColor = accentColor; iconAlpha = 0.95f }
                else       -> { textColor = secondaryText; textAlpha = 0.9f; iconColor = secondaryText; iconAlpha = 0.7f }
            }
            holder.title.setTextColor(textColor)
            holder.title.alpha = textAlpha
            holder.icon.setColorFilter(iconColor)
            holder.icon.alpha = iconAlpha

            // 箭头透明度和颜色
            val arrow = holder.root.findViewById<ImageView>(R.id.category_arrow)
            if (arrow != null) {
                arrow.visibility = if (isLandscapeSidebar) View.GONE else View.VISIBLE
                if (!isLandscapeSidebar && isSelected) {
                    arrow.alpha = 1.0f
                    arrow.setColorFilter(accentColor)
                } else if (!isLandscapeSidebar && hasFocus) {
                    arrow.alpha = 0.9f
                    arrow.setColorFilter(accentColor)
                } else if (!isLandscapeSidebar) {
                    arrow.alpha = 0.4f
                    arrow.setColorFilter(subtleText)
                }
            }
        }

        /**
         * 选择分类
         */
        private fun selectCategory(position: Int, item: CategoryItem) {
            if (position < 0 || position >= categories.size) return

            val oldIndex = selectedCategoryIndex
            selectedCategoryIndex = position

            // 确保 oldIndex 有效再通知更新
            if (oldIndex in 0 until categories.size) {
                notifyItemChanged(oldIndex)
            }
            notifyItemChanged(selectedCategoryIndex)

            // 滚动到对应分类
            scrollToCategory(item.key)

            // 竖屏时关闭抽屉（横屏时 drawerLayout 为 null，无需处理）
            if (drawerLayout != null) {
                drawerLayout?.closeDrawers()
            }
        }

        override fun getItemCount(): Int = categories.size
    }

    /**
     * 滚动到指定分类
     */
    fun scrollToCategory(categoryKey: String) {
        val fragment = supportFragmentManager
                .findFragmentById(R.id.preference_container) as? SettingsFragment
        fragment?.scrollToCategoryByKey(categoryKey)
    }

    /**
     * 通知 Activity 分类已加载
     */
    fun onCategoriesLoaded(loadedCategories: List<CategoryItem>) {
        categories.clear()
        categories.addAll(loadedCategories)

        // 验证并校正 selectedCategoryIndex（屏幕旋转后恢复时可能越界）
        if (selectedCategoryIndex >= categories.size) {
            selectedCategoryIndex = 0.coerceAtLeast(categories.size - 1)
        }

        categoryAdapter?.notifyDataSetChanged()
    }

    /**
     * 更新选中的分类
     */
    fun updateSelectedCategory(index: Int) {
        if (index == selectedCategoryIndex || index < 0 || index >= categories.size) return

        val oldIndex = selectedCategoryIndex
        selectedCategoryIndex = index
        // 确保 oldIndex 有效再通知更新
        if (oldIndex in 0 until categories.size) {
            categoryAdapter?.notifyItemChanged(oldIndex)
        }
        categoryAdapter?.notifyItemChanged(selectedCategoryIndex)
    }

    /**
     * 更新抽屉布局模式（仅竖屏有效）
     * 横屏使用并排的 LinearLayout，不需要 DrawerLayout 操作
     * 竖屏：默认关闭，可通过菜单按钮打开
     */
    private fun updateDrawerMode() {
        // 横屏时 drawerLayout 为 null（使用并排布局），直接返回
        if (drawerLayout == null) return

        // 以下代码仅在竖屏时执行
        val drawerMenu = findViewById<View>(R.id.drawer_menu)
        val menuToggle = findViewById<ImageView>(R.id.settings_menu_toggle)

        // 竖屏：可收起抽屉
        drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, drawerMenu)
        drawerLayout?.setScrimColor(0x99000000.toInt())

        // 关闭抽屉
        if (drawerLayout?.isDrawerOpen(drawerMenu) == true) {
            drawerLayout?.closeDrawer(drawerMenu, false)
        }

        menuToggle?.visibility = View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            val insets = window.decorView.rootWindowInsets
            if (insets != null) {
                displayCutoutP = insets.displayCutout
            }
        }

        // 设置抽屉模式
        updateDrawerMode()

        reloadSettings()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val isUsingPortraitDrawerLayout = findViewById<View>(R.id.drawer_layout) is DrawerLayout
        val shouldUsePortraitDrawerLayout = newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE
        if (isUsingPortraitDrawerLayout != shouldUsePortraitDrawerLayout) {
            recreate()
            return
        }

        // 更新抽屉模式
        updateDrawerMode()
        val nightModeChanged = lastNightMode != isNightMode()
        lastNightMode = isNightMode()
        applySettingsThemeSurfaces()
        loadBackgroundImage()
        var shouldReloadSettings = nightModeChanged
        if (nightModeChanged) {
            theme.applyStyle(R.style.PreferenceThemeWithShadow, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mode = windowManager.defaultDisplay.mode

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.physicalWidth * mode.physicalHeight != previousDisplayPixelCount) {
                shouldReloadSettings = true
            }
        }
        if (shouldReloadSettings) {
            reloadSettings()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleDrawerKeyEvent(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 处理控制器按键事件（抽屉导航）
     *
     * 手柄支持（仅竖屏有效，横屏时菜单固定显示）：
     * - L1/L2：打开抽屉菜单
     * - R1/R2：关闭抽屉菜单
     * - D-pad 左：打开抽屉
     * - D-pad 右：关闭抽屉（从抽屉内）
     * - B 键：关闭抽屉
     */
    private fun handleDrawerKeyEvent(keyCode: Int): Boolean {
        // 横屏时 drawerLayout 为 null（使用并排布局），直接返回
        if (drawerLayout == null) return false

        // 以下代码仅在竖屏时执行
        val drawerMenu = findViewById<View>(R.id.drawer_menu)
        val isDrawerOpen = drawerLayout?.isDrawerOpen(drawerMenu)

        // L1/L2：打开抽屉
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
                keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            if (isDrawerOpen != true) {
                drawerLayout?.openDrawer(drawerMenu)
                return true
            }
        }

        // R1/R2：关闭抽屉
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1 ||
                keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            if (isDrawerOpen == true) {
                drawerLayout?.closeDrawer(drawerMenu)
                return true
            }
        }

        // D-pad 左键：打开抽屉
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (isDrawerOpen != true) {
                drawerLayout?.openDrawer(drawerMenu)
                return true
            }
        }

        // D-pad 右键：关闭抽屉（从抽屉内）
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (isDrawerOpen == true) {
                val focusedView = currentFocus
                if (focusedView != null && isViewInsideDrawer(focusedView)) {
                    drawerLayout?.closeDrawer(drawerMenu)
                    return true
                }
            }
        }

        // B 键（手柄）：关闭抽屉
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (isDrawerOpen == true) {
                drawerLayout?.closeDrawer(drawerMenu)
                return true
            }
        }

        return false
    }

    /**
     * 检查视图是否在抽屉内
     */
    private fun isViewInsideDrawer(view: View): Boolean {
        val drawerMenu = findViewById<View>(R.id.drawer_menu) ?: return false

        var current: View? = view
        while (current != null) {
            if (current === drawerMenu) return true
            current = if (current.parent is View) current.parent as View else null
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存选中的分类索引，用于屏幕旋转后恢复
        outState.putInt(KEY_SELECTED_CATEGORY, selectedCategoryIndex)
    }

    override fun onDestroy() {
        AboutDialogLauncher.release(this)
        super.onDestroy()
        externalDisplayManager?.cleanup()
        externalDisplayManager = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (hideScreenCombinationModePicker()) {
            return
        }

        // 搜索栏可见时，优先关闭搜索而不是退出
        if (isSearchBarVisible) {
            hideSearchBar()
            return
        }

        super.onBackPressed()
        if (handleBackForDrawer()) {
            return
        }

        finish()
        handleLanguageChange()
    }

    /**
     * 处理返回键时的抽屉关闭逻辑（仅竖屏有效）
     */
    private fun handleBackForDrawer(): Boolean {
        // 横屏时 drawerLayout 为 null（使用并排布局），直接返回
        if (drawerLayout == null) return false

        // 以下代码仅在竖屏时执行
        val drawerMenu = findViewById<View>(R.id.drawer_menu)
        if (drawerLayout?.isDrawerOpen(drawerMenu) != true) return false

        drawerLayout?.closeDrawer(drawerMenu)
        return true
    }

    /**
     * 处理语言变更后的界面刷新（Android 13 以下）
     */
    private fun handleLanguageChange() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val newPrefs = PreferenceConfiguration.readPreferences(this)
            if (newPrefs.language != previousPrefs.language) {
                val intent = Intent(this, PcView::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent, null)
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private companion object {
            private const val SCREEN_COMBINATION_MODE_PREF_KEY = "list_screen_combination_mode"
        }

        private var nativeResolutionStartIndex = Int.MAX_VALUE
        private var nativeFramerateShown = false

        private var exportConfigString: String = ""
        private var pendingCrownShareExportString: String = ""
        private var pendingCrownShareImport: CrownProfileShareManager.ImportedProfile? = null
        private var pendingSyncExportString: String = ""
        private var pendingSyncImportString: String = ""
        private val configSyncSnapshotHandler = Handler(Looper.getMainLooper())
        private val configSyncSnapshotRunnable = Runnable {
            writeConfigSyncLocalSnapshot(showToast = false, requireAutoEnabled = true)
        }
        private val configSyncPreferenceChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                handleConfigSyncPreferenceChanged(key)
            }
        private var configSyncSnapshotDirty = false
        private var configSyncSnapshotInProgress = false
        private var configSyncPreferenceListenerRegistered = false

        // 分类列表（用于抽屉菜单同步）
        private val categoryList: MutableList<PreferenceCategory> = ArrayList()
        private var currentCategoryIndex = 0
        // 标记是否正在手动滚动（点击分类触发的滚动）
        private var isManualScrolling = false

        // 缓存 category -> adapter position 映射，避免每帧 O(N*M) 全表扫
        // 仅在数据集变化时失效；滚动期间复用
        private var categoryPositions: IntArray = IntArray(0)
        private var categoryPositionsValid = false
        private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null
        @Volatile
        private var developerUnlockVerificationRunning = false
        @Volatile
        private var developerPendingDeviceCode: GitHubStarVerifier.DeviceCode? = null
        @Volatile
        private var developerLastForegroundPollMs = 0L
        @Volatile
        private var developerForegroundPollRunning = false
        private var developerDeviceCodeDialog: AlertDialog? = null

        /**
         * 获取目标显示器（优先使用外接显示器）
         */
        private fun getTargetDisplay(): Display {
            val settingsActivity = activity as? StreamSettings
            return settingsActivity?.externalDisplayManager?.getTargetDisplay()
                ?: requireActivity().windowManager.defaultDisplay
        }

        private fun appDialogBuilder(context: Context = requireContext()): AlertDialog.Builder {
            return AlertDialog.Builder(context, R.style.AppDialogStyle)
        }

        private fun AlertDialog.Builder.showStyled(): AlertDialog {
            return showStyledDialog(create())
        }

        private fun AlertDialog.Builder.showStyledChoiceList(): AlertDialog {
            return showStyledDialog(create(), styleSystemChoiceList = true)
        }

        private fun showStyledDialog(dialog: AlertDialog, styleSystemChoiceList: Boolean = false): AlertDialog {
            hideKeyboardBeforeDialog()
            dialog.show()
            if (styleSystemChoiceList) {
                AppDialogStyler.applySystemChoiceList(dialog, requireContext())
            } else {
                AppDialogStyler.apply(dialog, requireContext())
            }
            return dialog
        }

        private fun hideKeyboardBeforeDialog() {
            val hostActivity = activity ?: return
            val focusedView = hostActivity.currentFocus ?: view ?: return
            val imm = hostActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(focusedView.windowToken, 0)
            focusedView.clearFocus()
        }

        private fun setValue(preferenceKey: String, value: String) {
            val pref = findPreference<ListPreference>(preferenceKey)!!
            pref.value = value
        }

        private fun appendPreferenceEntry(pref: ListPreference, newEntryName: String, newEntryValue: String) {
            val newEntries = pref.entries.copyOf(pref.entries.size + 1)
            val newValues = pref.entryValues.copyOf(pref.entryValues.size + 1)

            // Add the new option
            newEntries[newEntries.size - 1] = newEntryName
            newValues[newValues.size - 1] = newEntryValue

            pref.entries = newEntries
            pref.entryValues = newValues
        }

        private fun addNativeResolutionEntry(nativeWidth: Int, nativeHeight: Int, insetsRemoved: Boolean, portrait: Boolean) {
            val pref = findPreference<ListPreference>(PreferenceConfiguration.RESOLUTION_PREF_STRING)!!

            var newName: String = if (insetsRemoved) {
                resources.getString(R.string.resolution_prefix_native_fullscreen)
            } else {
                resources.getString(R.string.resolution_prefix_native)
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                newName += if (portrait) {
                    " " + resources.getString(R.string.resolution_prefix_native_portrait)
                } else {
                    " " + resources.getString(R.string.resolution_prefix_native_landscape)
                }
            }

            newName += " (${nativeWidth}x${nativeHeight})"

            val newValue = "${nativeWidth}x${nativeHeight}"

            // Check if the native resolution is already present
            if (pref.entryValues.any { it.toString() == newValue }) {
                return
            }

            if (pref.entryValues.size < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.entryValues.size
            }
            appendPreferenceEntry(pref, newName, newValue)
        }

        private fun addNativeResolutionEntries(nativeWidth: Int, nativeHeight: Int, insetsRemoved: Boolean) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true)
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false)
        }

        private fun addCustomResolutionsEntries() {
            val storage = requireActivity().getSharedPreferences(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE,
                MODE_PRIVATE
            )
            val stored = storage.getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, null)
            val pref = findPreference<ListPreference>(PreferenceConfiguration.RESOLUTION_PREF_STRING)!!

            val preferencesList = listOf(*pref.entryValues)

            if (stored.isNullOrEmpty()) {
                return
            }

            val lengthComparator = Comparator<String> { s1, s2 ->
                val s1Size = s1.split("x")
                val s2Size = s2.split("x")

                val w1 = s1Size[0].toInt()
                val w2 = s2Size[0].toInt()

                val h1 = s1Size[1].toInt()
                val h2 = s2Size[1].toInt()

                if (w1 == w2) {
                    h1.compareTo(h2)
                } else {
                    w1.compareTo(w2)
                }
            }

            val list = ArrayList(stored)
            Collections.sort(list, lengthComparator)

            for (storedResolution in list) {
                if (preferencesList.contains(storedResolution)) {
                    continue
                }
                val resolution = storedResolution.split("x")
                val width = resolution[0].toInt()
                val height = resolution[1].toInt()
                val aspectRatio = AspectRatioConverter.getAspectRatio(width, height)
                var displayText = "Custom "

                if (aspectRatio != null) {
                    displayText += "$aspectRatio "
                }

                displayText += "($storedResolution)"

                appendPreferenceEntry(pref, displayText, storedResolution)
            }
        }

        private fun addNativeFrameRateEntry(framerate: Float) {
            val frameRateRounded = framerate.roundToInt()
            if (frameRateRounded == 0) {
                return
            }

            val pref = findPreference<ListPreference>(PreferenceConfiguration.FPS_PREF_STRING)!!
            val fpsValue = frameRateRounded.toString()
            val fpsName = resources.getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + resources.getString(R.string.fps_suffix_fps) + ")"

            // Check if the native frame rate is already present
            if (pref.entryValues.any { it.toString() == fpsValue }) {
                nativeFramerateShown = false
                return
            }

            appendPreferenceEntry(pref, fpsName, fpsValue)
            nativeFramerateShown = true
        }

        private fun removeValue(preferenceKey: String, value: String, onMatched: Runnable) {
            var matchingCount = 0

            val pref = findPreference<ListPreference>(preferenceKey)!!

            // Count the number of matching entries we'll be removing
            for (seq in pref.entryValues) {
                if (seq.toString().equals(value, ignoreCase = true)) {
                    matchingCount++
                }
            }

            // Create the new arrays
            val entries = arrayOfNulls<CharSequence>(pref.entries.size - matchingCount)
            val entryValues = arrayOfNulls<CharSequence>(pref.entryValues.size - matchingCount)
            var outIndex = 0
            for (i in pref.entryValues.indices) {
                if (pref.entryValues[i].toString().equals(value, ignoreCase = true)) {
                    // Skip matching values
                    continue
                }

                entries[outIndex] = pref.entries[i]
                entryValues[outIndex] = pref.entryValues[i]
                outIndex++
            }

            if (pref.value.equals(value, ignoreCase = true)) {
                onMatched.run()
            }

            // Update the preference with the new list
            pref.entries = entries
            pref.entryValues = entryValues
        }

        private fun resetBitrateToDefault(prefs: SharedPreferences, res: String?, fps: String?) {
            var resValue = res
            var fpsValue = fps
            if (resValue == null) {
                resValue = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION)
            }
            if (fpsValue == null) {
                fpsValue = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS)
            }

            val defaultBitrate = PreferenceConfiguration.getDefaultBitrate(resValue!!, fpsValue!!)
            val bitratePreference = findPreference<SeekBarPreference>(PreferenceConfiguration.BITRATE_PREF_STRING)
            if (bitratePreference != null) {
                bitratePreference.setProgress(defaultBitrate)
            } else {
                prefs.edit {
                    putInt(PreferenceConfiguration.BITRATE_PREF_STRING, defaultBitrate)
                }
            }
        }

        private fun updateHostCadencePreciseSyncVisibility(framePacingValue: String? = null) {
            val hostCadencePref = findPreference<CheckBoxPreference>(
                PreferenceConfiguration.ENABLE_HOST_CADENCE_PRECISE_SYNC_STRING
            ) ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
            val selectedFramePacing = framePacingValue
                ?: prefs.getString(
                    PreferenceConfiguration.FRAME_PACING_PREF_STRING,
                    PreferenceConfiguration.DEFAULT_FRAME_PACING
                )

            hostCadencePref.isVisible = selectedFramePacing == "precise-sync"
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            // 确保列表背景透明
            view.setBackgroundColor(Color.TRANSPARENT)

            // 减少顶部间距，让设置内容更贴近导航栏
            val topPadding = view.paddingTop
            val reducedPadding =
                0.coerceAtLeast(topPadding - (16 * resources.displayMetrics.density).toInt())
            view.setPadding(view.paddingLeft, reducedPadding,
                    view.paddingRight, view.paddingBottom)
            UiHelper.applyStatusBarPadding(view)
            return view
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityCreated(savedInstanceState: Bundle?) {
            @Suppress("DEPRECATION")
            super.onActivityCreated(savedInstanceState)

            val activity = activity
            if (activity == null || activity !is StreamSettings) return

            val settingsActivity = activity
            val screen = preferenceScreen ?: return

            // 收集所有分类
            categoryList.clear()
            val items: MutableList<CategoryItem> = ArrayList()
            for (i in 0 until screen.preferenceCount) {
                val pref = screen.getPreference(i)
                if (pref !is PreferenceCategory) continue

                if (pref.title == null) continue

                val title = pref.title.toString()
                val key = pref.key ?: "category_$i"
                val iconRes = getIconForCategory(key)

                categoryList.add(pref)
                items.add(CategoryItem(key, title, iconRes))
            }

            // 通知 Activity 分类已加载
            settingsActivity.onCategoriesLoaded(items)

            // 添加滚动监听
            Handler(Looper.getMainLooper()).post {
                val recyclerView = listView
                if (recyclerView != null) {
                    setupScrollListener(recyclerView, settingsActivity)
                }
            }
        }

        /**
         * 根据 key 滚动到指定分类
         */
        fun scrollToCategoryByKey(categoryKey: String) {
            for (i in categoryList.indices) {
                val category = categoryList[i]
                val key = category.key ?: "category_$i"
                if (key == categoryKey) {
                    scrollToCategoryAtIndex(i)
                    return
                }
            }
        }

        /**
         * 记录每个折叠分组的原始 initialExpandedChildrenCount，
         * 搜索时全部展开，清空搜索时还原。
         */
        private val originalCollapseCounts = mutableMapOf<String, Int>()

        /**
         * 应用搜索过滤。空查询恢复全部可见性 + 原始折叠状态；
         * 非空查询仅显示匹配的项，匹配类的整组也展开。
         */
        fun applySearchFilter(query: String) {
            val screen = preferenceScreen ?: return
            val q = query.trim().lowercase(Locale.getDefault())
            val isSearching = q.isNotEmpty()

            for (i in 0 until screen.preferenceCount) {
                val category = screen.getPreference(i) as? PreferenceCategory ?: continue
                val catKey = category.key ?: "category_$i"

                // 一次性记录原始折叠数（仅首次进入搜索时）
                if (isSearching && !originalCollapseCounts.containsKey(catKey)) {
                    originalCollapseCounts[catKey] = category.initialExpandedChildrenCount
                }

                if (!isSearching) {
                    // 还原
                    category.isVisible = true
                    for (j in 0 until category.preferenceCount) {
                        category.getPreference(j).isVisible = true
                    }
                    originalCollapseCounts[catKey]?.let { category.initialExpandedChildrenCount = it }
                    continue
                }

                // 搜索中：完全展开（避免折叠掉匹配项）
                category.initialExpandedChildrenCount = Int.MAX_VALUE

                val categoryMatches = category.title?.toString()?.lowercase(Locale.getDefault())?.contains(q) == true
                var anyChildMatches = false
                for (j in 0 until category.preferenceCount) {
                    val child = category.getPreference(j)
                    val childMatches = categoryMatches || preferenceMatches(child, q)
                    child.isVisible = childMatches
                    if (childMatches) anyChildMatches = true
                }
                category.isVisible = categoryMatches || anyChildMatches
            }
        }

        private fun preferenceMatches(p: Preference, q: String): Boolean {
            val title = p.title?.toString()?.lowercase(Locale.getDefault())
            if (title != null && title.contains(q)) return true
            val summary = p.summary?.toString()?.lowercase(Locale.getDefault())
            if (summary != null && summary.contains(q)) return true
            val key = p.key?.lowercase(Locale.getDefault())
            if (key != null && key.contains(q)) return true
            return false
        }

        /**
         * 递归遍历 PreferenceGroup，给所有 ListPreference 装上一个 SummaryProvider：
         * - 第一行显示「当前: {entry}」
         * - 第二行保留 XML 中原本写好的说明 summary（如果有）
         * SummaryProvider 是按需调用的，所以即便后续动态 setEntries() 也能拿到最新值。
         */
        private fun applyListPreferenceCurrentValueSummary(group: PreferenceGroup) {
            val accent = ContextCompat.getColor(group.context, R.color.ui_shell_accent)
            val valueText = ContextCompat.getColor(group.context, R.color.ui_shell_text_primary)
            val disabledAccent = ContextCompat.getColor(group.context, R.color.ui_shell_text_disabled_primary)
            applyHighlightedSummariesRecursively(group, accent, valueText, disabledAccent)
        }

        private fun applyHighlightedSummariesRecursively(
                group: PreferenceGroup,
                accent: Int,
                valueText: Int,
                disabledAccent: Int
        ) {
            for (i in 0 until group.preferenceCount) {
                val child = group.getPreference(i)
                when {
                    child is PreferenceGroup -> applyHighlightedSummariesRecursively(child, accent, valueText, disabledAccent)
                    // IconListPreference 自己重写 setSummary 维护 "(当前：xxx)"，
                    // 装 SummaryProvider 会与其 super.setSummary 调用互斥，跳过。
                    child is IconListPreference -> Unit
                    child is ListPreference -> applyHighlightedSummary(child, accent, valueText, disabledAccent) {
                        val entry = it.entry?.toString()
                        if (entry.isNullOrBlank()) "—" else entry
                    }
                    child is SeekBarPreference -> applyHighlightedSummary(child, accent, valueText, disabledAccent) {
                        val display = it.formatDisplayValue(it.currentValue)
                        val suffix = it.suffix?.takeIf { s -> s.isNotBlank() }
                        if (suffix != null) "$display $suffix" else display
                    }
                }
            }
        }

        /**
         * 给 Preference 装上一个 SummaryProvider：
         * 第一行用主题强调色 + 粗体显示 currentValueProvider 返回的当前值，
         * 第二行恢复默认色显示 XML 中原本的说明 summary。
         */
        private inline fun <reified T : Preference> applyHighlightedSummary(
                pref: T,
                accent: Int,
                valueText: Int,
                disabledAccent: Int,
                crossinline currentValueProvider: (T) -> String
        ) {
            val originalSummary = pref.summary?.toString()?.takeIf { it.isNotBlank() }
            pref.summaryProvider = Preference.SummaryProvider<T> { p ->
                val current = currentValueProvider(p)
                val builder = SpannableStringBuilder()
                val markerStart = builder.length
                builder.append('●')
                builder.setSpan(
                        ForegroundColorSpan(if (p.isEnabled) accent else disabledAccent),
                        markerStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append(' ')
                val valueStart = builder.length
                builder.append(current)
                builder.setSpan(
                        ForegroundColorSpan(if (p.isEnabled) valueText else disabledAccent),
                        valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (originalSummary != null) {
                    builder.append('\n').append(originalSummary)
                }
                builder
            }
        }

        /**
         * 查询全部高级配置档案，返回 id->name 映射。
         * 供 export / merge / import-refresh 三处复用。
         */
        private fun loadConfigMap(helper: SuperConfigDatabaseHelper): MutableMap<String, String> {
            val map = LinkedHashMap<String, String>()
            for (id in helper.queryAllConfigIds()) {
                val name = helper.queryConfigAttribute(
                        id, PageConfigController.COLUMN_STRING_CONFIG_NAME, "default") as String
                map[id.toString()] = name
            }
            return map
        }

        private fun openConfigDocument(requestCode: Int) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            @Suppress("DEPRECATION")
            startActivityForResult(intent, requestCode)
        }

        private fun createConfigDocument(fileName: String) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_TITLE, "$fileName.mdat")
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 1)
        }

        private fun createCrownShareDocument(fileName: String) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_TITLE, CrownProfileShareManager.suggestedFileName(fileName))
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_CREATE_CROWN_SHARE)
        }

        private fun openCrownShareDocument() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_OPEN_CROWN_SHARE)
        }

        private fun createSyncDocument() {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_TITLE, ConfigurationSyncManager.DEFAULT_FILE_NAME)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_CREATE_CONFIG_SYNC)
        }

        private fun openSyncDocument() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_OPEN_CONFIG_SYNC)
        }

        private fun openSyncDirectory() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_OPEN_CONFIG_SYNC_DIRECTORY)
        }

        private fun readDocumentText(uri: android.net.Uri): String {
            val resolver = requireContext().contentResolver
            return resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IOException("Unable to open input stream")
        }

        private fun writeDocumentText(uri: android.net.Uri, text: String) {
            val resolver = requireContext().contentResolver
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                ?: throw IOException("Unable to open output stream")
        }

        private fun setupConfigSyncPreferences() {
            findPreference<Preference>("config_sync_export")?.setOnPreferenceClickListener {
                exportConfigSyncPackage()
                true
            }

            findPreference<Preference>("config_sync_import")?.setOnPreferenceClickListener {
                openSyncDocument()
                true
            }

            findPreference<Preference>(ConfigurationSyncManager.PREF_LOCAL_SNAPSHOT_NOW)
                ?.setOnPreferenceClickListener {
                    writeConfigSyncLocalSnapshot(showToast = true, requireAutoEnabled = false)
                    true
                }

            findPreference<Preference>(ConfigurationSyncManager.PREF_EXTERNAL_SYNC_DIRECTORY)
                ?.setOnPreferenceClickListener {
                    openSyncDirectory()
                    true
                }
            findPreference<Preference>(ConfigurationSyncManager.PREF_BACKUP_PASSWORD)
                ?.setOnPreferenceClickListener {
                    showExternalSyncPasswordDialog()
                    true
                }
            findPreference<Preference>(ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_IMPORT)
                ?.setOnPreferenceClickListener {
                    importExternalSyncSnapshot()
                    true
            }
            updateLocalSnapshotPreferenceSummary()
            updateExternalSyncDirectorySummary()
            updateConfigSyncStatusSummary()
        }

        private fun handleConfigSyncPreferenceChanged(key: String?) {
            if (key == ConfigurationSyncManager.PREF_AUTO_SNAPSHOT_ENABLED) {
                val ctx = context ?: return
                if (ConfigurationSyncManager.isAutoSnapshotEnabled(ctx)) {
                    requestConfigSyncAutoSnapshot(delayMs = 0L)
                } else {
                    configSyncSnapshotDirty = false
                    configSyncSnapshotHandler.removeCallbacks(configSyncSnapshotRunnable)
                }
                updateLocalSnapshotPreferenceSummary()
                updateConfigSyncStatusSummary()
                return
            }

            if (key == ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED) {
                updateExternalSyncDirectorySummary()
                updateConfigSyncStatusSummary()
                val ctx = context ?: return
                if (PreferenceManager.getDefaultSharedPreferences(ctx)
                        .getBoolean(ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED, false) &&
                    !ConfigurationSyncManager.hasExternalSyncPassword(ctx)) {
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                        putBoolean(ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED, false)
                    }
                    Toast.makeText(context, R.string.toast_config_sync_password_required, Toast.LENGTH_LONG).show()
                    showExternalSyncPasswordDialog()
                    updateExternalSyncDirectorySummary()
                    return
                }
                if (ConfigurationSyncManager.isExternalSnapshotEnabled(ctx)) {
                    requestConfigSyncAutoSnapshot(delayMs = 0L)
                    ConfigurationSyncScheduler.schedule(ctx)
                } else {
                    ConfigurationSyncScheduler.cancel(ctx)
                }
                return
            }

            if (key == ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED) {
                updateExternalSyncDirectorySummary()
                updateConfigSyncStatusSummary()
                val ctx = context ?: return
                val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                val enabled = prefs.getBoolean(ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED, false)
                if (enabled &&
                    !ConfigurationSyncManager.hasExternalSyncPassword(ctx)) {
                    prefs.edit {
                        putBoolean(ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED, false)
                    }
                    Toast.makeText(context, R.string.toast_config_sync_password_required, Toast.LENGTH_LONG).show()
                    showExternalSyncPasswordDialog()
                    updateExternalSyncDirectorySummary()
                    return
                }
                prefs.edit {
                    putBoolean(ConfigurationSyncManager.PREF_AUTO_SNAPSHOT_ENABLED, enabled)
                    putBoolean(ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED, enabled)
                }
                if (enabled) {
                    requestConfigSyncAutoSnapshot(delayMs = 0L)
                    ConfigurationSyncScheduler.runNow(ctx)
                } else {
                    ConfigurationSyncScheduler.cancel(ctx)
                }
                updateExternalSyncDirectorySummary()
                return
            }

            if (ConfigurationSyncManager.isConfigSyncMetadataPreferenceKey(key)) {
                updateLocalSnapshotPreferenceSummary()
                updateExternalSyncDirectorySummary()
                updateConfigSyncStatusSummary()
                return
            }

            requestConfigSyncAutoSnapshot()
        }

        private fun registerConfigSyncPreferenceListener() {
            if (configSyncPreferenceListenerRegistered) return
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(configSyncPreferenceChangeListener)
            configSyncPreferenceListenerRegistered = true
        }

        private fun unregisterConfigSyncPreferenceListener() {
            if (!configSyncPreferenceListenerRegistered) return
            val ctx = context ?: return
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .unregisterOnSharedPreferenceChangeListener(configSyncPreferenceChangeListener)
            configSyncPreferenceListenerRegistered = false
        }

        private fun requestConfigSyncAutoSnapshot(delayMs: Long = 1500L) {
            val ctx = context ?: return
            if (!ConfigurationSyncManager.isAutoSnapshotEnabled(ctx)) return

            configSyncSnapshotDirty = true
            configSyncSnapshotHandler.removeCallbacks(configSyncSnapshotRunnable)
            configSyncSnapshotHandler.postDelayed(configSyncSnapshotRunnable, delayMs)
        }

        private fun flushConfigSyncAutoSnapshot() {
            val ctx = context ?: return
            if (!ConfigurationSyncManager.isAutoSnapshotEnabled(ctx)) return

            configSyncSnapshotDirty = true
            configSyncSnapshotHandler.removeCallbacks(configSyncSnapshotRunnable)
            writeConfigSyncLocalSnapshot(showToast = false, requireAutoEnabled = true)
        }

        private fun writeConfigSyncLocalSnapshot(showToast: Boolean, requireAutoEnabled: Boolean) {
            val appContext = context?.applicationContext ?: return
            if (requireAutoEnabled && !ConfigurationSyncManager.isAutoSnapshotEnabled(appContext)) return
            if (configSyncSnapshotInProgress) {
                if (requireAutoEnabled) {
                    configSyncSnapshotDirty = true
                }
                return
            }

            configSyncSnapshotInProgress = true
            if (requireAutoEnabled) {
                configSyncSnapshotDirty = false
            }

            thread(name = "ConfigSyncLocalSnapshot") {
                val result = runCatching {
                    val syncManager = ConfigurationSyncManager(appContext)
                    if (ConfigurationSyncManager.isBackgroundSyncEnabled(appContext)) {
                        syncManager.synchronizeWithExternalSnapshot()
                            .also { syncManager.rememberAutoSyncResult(it) }
                    } else {
                        syncManager.writeConfiguredSnapshots()
                    }
                }.onFailure {
                    if (ConfigurationSyncManager.isBackgroundSyncEnabled(appContext)) {
                        ConfigurationSyncManager(appContext)
                            .rememberAutoSyncFailure(it.message ?: it.javaClass.simpleName)
                    }
                }

                configSyncSnapshotHandler.post {
                    configSyncSnapshotInProgress = false
                    if (isAdded) {
                        updateLocalSnapshotPreferenceSummary()
                        updateExternalSyncDirectorySummary()
                        updateConfigSyncStatusSummary()
                        if (showToast) {
                            val syncResult = result.getOrNull()
                            val syncSucceeded = result.isSuccess &&
                                    (syncResult !is ConfigurationSyncManager.AutoSyncResult ||
                                            syncResult.errorMessage == null)
                            if (syncSucceeded) {
                                Toast.makeText(
                                    context,
                                    R.string.toast_config_sync_snapshot_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.toast_config_sync_snapshot_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    result.exceptionOrNull()?.let {
                        Log.e("ConfigSync", "Failed to write local configuration sync snapshot", it)
                    }
                    (result.getOrNull() as? ConfigurationSyncManager.AutoSyncResult)
                        ?.errorMessage
                        ?.let { Log.w("ConfigSync", "Local snapshot background merge failed: $it") }
                    (result.getOrNull() as? ConfigurationSyncManager.AutoSyncResult)
                        ?.let { maybeShowPairingRestoreRestartPrompt(it.pairingItemsImported, it.pairingItemsFailed) }

                    if (configSyncSnapshotDirty &&
                        ConfigurationSyncManager.isAutoSnapshotEnabled(appContext)) {
                        requestConfigSyncAutoSnapshot()
                    } else if (ConfigurationSyncManager.isBackgroundSyncEnabled(appContext)) {
                        ConfigurationSyncScheduler.schedule(appContext)
                    }
                }
            }
        }

        private fun updateLocalSnapshotPreferenceSummary() {
            val pref = findPreference<Preference>(ConfigurationSyncManager.PREF_LOCAL_SNAPSHOT_NOW)
                ?: return
            val ctx = context ?: return
            val info = ConfigurationSyncManager(ctx).localSnapshotInfo()
            pref.summary = if (info.exists) {
                val updatedAt = DateFormat
                    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(info.updatedAt))
                val sizeKb = maxOf(1L, (info.sizeBytes + 1023L) / 1024L)
                getString(R.string.summary_config_sync_snapshot_latest, updatedAt, sizeKb)
            } else {
                getString(R.string.summary_config_sync_snapshot_never)
            }
        }

        private fun updateExternalSyncDirectorySummary() {
            val ctx = context ?: return
            val treeUri = ConfigurationSyncManager.externalSyncTreeUri(ctx)
            val directoryPref = findPreference<Preference>(
                ConfigurationSyncManager.PREF_EXTERNAL_SYNC_DIRECTORY
            )
            val externalSnapshotPref = findPreference<CheckBoxPreference>(
                ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED
            )
            val backgroundSyncPref = findPreference<CheckBoxPreference>(
                ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED
            )
            val backupPasswordPref = findPreference<Preference>(
                ConfigurationSyncManager.PREF_BACKUP_PASSWORD
            )
            val importExternalSnapshotPref = findPreference<Preference>(
                ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_IMPORT
            )
            val hasBackupPassword = ConfigurationSyncManager.hasExternalSyncPassword(ctx)

            directoryPref?.summary = if (treeUri == null) {
                getString(R.string.summary_config_sync_select_directory_none)
            } else {
                val label = treeUri.lastPathSegment?.takeIf { it.isNotBlank() }
                    ?: treeUri.toString()
                getString(R.string.summary_config_sync_select_directory_selected, label)
            }

            backupPasswordPref?.summary = if (hasBackupPassword) {
                getString(R.string.summary_config_sync_backup_password_set)
            } else {
                getString(R.string.summary_config_sync_backup_password_missing)
            }
            externalSnapshotPref?.isEnabled = treeUri != null && hasBackupPassword
            backgroundSyncPref?.isEnabled = treeUri != null && hasBackupPassword
            importExternalSnapshotPref?.isEnabled = treeUri != null
        }

        private fun updateConfigSyncStatusSummary() {
            val pref = findPreference<Preference>(ConfigurationSyncManager.PREF_SYNC_STATUS)
                ?: return
            val ctx = context ?: return
            if (!ConfigurationSyncManager.isBackgroundSyncEnabled(ctx)) {
                pref.summary = getString(R.string.summary_config_sync_status_disabled)
                return
            }

            val status = ConfigurationSyncManager(ctx).syncStatusInfo()
            if (!status.hasCompletedSync) {
                pref.summary = getString(R.string.summary_config_sync_status_never)
                return
            }

            val completedAt = DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(status.completedAt))
            pref.summary = if (status.success) {
                getString(
                    R.string.summary_config_sync_status_success,
                    completedAt,
                    formatSyncStatusFlag(status.readExternal),
                    formatSyncStatusFlag(status.appliedMergedPackage),
                    formatSyncStatusFlag(status.wroteExternal)
                )
            } else {
                getString(
                    R.string.summary_config_sync_status_failed,
                    completedAt,
                    status.errorMessage.ifBlank { getString(R.string.config_sync_unknown_version) }
                )
            }
        }

        private fun formatSyncStatusFlag(value: Boolean): String {
            return getString(
                if (value) R.string.config_sync_status_yes else R.string.config_sync_status_no
            )
        }

        private fun handleSyncExportResult(data: Intent?) {
            val uri = data?.data ?: return
            try {
                writeDocumentText(uri, pendingSyncExportString)
                Toast.makeText(context, R.string.toast_config_sync_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("ConfigSync", "Failed to write configuration sync package", e)
                Toast.makeText(context, R.string.toast_config_sync_export_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun exportConfigSyncPackage() {
            val manager = ConfigurationSyncManager(requireContext())
            try {
                val savedPasswordPackage = manager.exportEncryptedSyncPackageWithSavedPassword()
                if (savedPasswordPackage != null) {
                    pendingSyncExportString = savedPasswordPackage
                    createSyncDocument()
                    return
                }
            } catch (e: Exception) {
                Log.w("ConfigSync", "Failed to export with saved backup password", e)
            }

            showConfigSyncPasswordDialog(
                titleRes = R.string.title_config_sync_export,
                messageRes = R.string.message_config_sync_backup_password_export,
                positiveRes = R.string.config_sync_action_export
            ) { password ->
                try {
                    manager.saveExternalSyncPassword(password)
                    updateExternalSyncDirectorySummary()
                    pendingSyncExportString = manager.exportEncryptedSyncPackage(password)
                    createSyncDocument()
                } catch (e: Exception) {
                    Log.e("ConfigSync", "Failed to export configuration sync package", e)
                    Toast.makeText(context, R.string.toast_config_sync_export_failed, Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun handleSyncImportResult(data: Intent?) {
            val uri = data?.data ?: return
            try {
                val syncPackage = readDocumentText(uri)
                if (ConfigurationSyncManager.isEncryptedSyncPackage(syncPackage)) {
                    if (!previewSyncPackageForImportWithSavedPassword(syncPackage)) {
                        showConfigSyncPasswordDialog(
                            titleRes = R.string.title_config_sync_import,
                            messageRes = R.string.message_config_sync_backup_password_import,
                            positiveRes = R.string.config_sync_action_import
                        ) { password ->
                            previewSyncPackageForImport(syncPackage, password)
                        }
                    }
                } else {
                    previewSyncPackageForImport(syncPackage, null)
                }
            } catch (e: Exception) {
                Log.e("ConfigSync", "Failed to import configuration sync package", e)
                Toast.makeText(context, R.string.toast_config_sync_import_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun handleSyncDirectoryResult(data: Intent?) {
            val uri = data?.data ?: return
            try {
                val grantFlags = data.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (grantFlags != 0) {
                    requireContext().contentResolver.takePersistableUriPermission(uri, grantFlags)
                }
                showConfigSyncPasswordDialog(
                    titleRes = R.string.title_config_sync_backup_password,
                    messageRes = R.string.message_config_sync_backup_password_external,
                    positiveRes = R.string.config_sync_action_save_password
                ) { password ->
                    val manager = ConfigurationSyncManager(requireContext())
                    if (!manager.saveExternalSyncPassword(password)) {
                        Toast.makeText(context, R.string.toast_config_sync_password_unavailable, Toast.LENGTH_LONG).show()
                        return@showConfigSyncPasswordDialog
                    }
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                        putString(ConfigurationSyncManager.PREF_EXTERNAL_SYNC_TREE_URI, uri.toString())
                        putBoolean(ConfigurationSyncManager.PREF_AUTO_SNAPSHOT_ENABLED, true)
                        putBoolean(ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED, true)
                        putBoolean(ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED, true)
                    }
                    Toast.makeText(context, R.string.toast_config_sync_password_saved, Toast.LENGTH_SHORT).show()
                    updateExternalSyncDirectorySummary()
                    writeConfigSyncLocalSnapshot(showToast = true, requireAutoEnabled = false)
                }
            } catch (e: Exception) {
                Log.e("ConfigSync", "Failed to select external configuration sync directory", e)
                Toast.makeText(context, R.string.toast_config_sync_directory_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun importExternalSyncSnapshot() {
            val appContext = context?.applicationContext ?: return
            if (!ConfigurationSyncManager.hasExternalSyncPassword(appContext)) {
                showConfigSyncPasswordDialog(
                    titleRes = R.string.title_config_sync_import_external_snapshot,
                    messageRes = R.string.message_config_sync_backup_password_import,
                    positiveRes = R.string.config_sync_action_import
                ) { password ->
                    if (!ConfigurationSyncManager(requireContext()).saveExternalSyncPassword(password)) {
                        Toast.makeText(context, R.string.toast_config_sync_password_unavailable, Toast.LENGTH_LONG).show()
                        return@showConfigSyncPasswordDialog
                    }
                    updateExternalSyncDirectorySummary()
                    importExternalSyncSnapshot()
                }
                return
            }
            thread(name = "ConfigSyncReadExternalSnapshot") {
                val result = runCatching {
                    val syncManager = ConfigurationSyncManager(appContext)
                    val syncPackage = syncManager.readExternalSnapshot()
                    syncManager.previewSyncPackage(syncPackage) to syncPackage
                }

                configSyncSnapshotHandler.post {
                    if (!isAdded) return@post
                    result
                        .onSuccess { (preview, syncPackage) ->
                            pendingSyncImportString = syncPackage
                            showSyncImportPreview(preview)
                        }
                        .onFailure {
                            Log.e("ConfigSync", "Failed to read external configuration sync snapshot", it)
                            Toast.makeText(
                                context,
                                R.string.toast_config_sync_import_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        }

        private fun previewSyncPackageForImport(syncPackage: String, password: String?) {
            try {
                val manager = ConfigurationSyncManager(requireContext())
                val plainPackage = if (ConfigurationSyncManager.isEncryptedSyncPackage(syncPackage)) {
                    manager.decryptEncryptedSyncPackage(syncPackage, password.orEmpty())
                } else {
                    syncPackage
                }
                val preview = manager.previewSyncPackage(plainPackage)
                pendingSyncImportString = plainPackage
                showSyncImportPreview(preview)
            } catch (e: Exception) {
                Log.e("ConfigSync", "Failed to preview configuration sync package", e)
                Toast.makeText(context, R.string.toast_config_sync_import_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun previewSyncPackageForImportWithSavedPassword(syncPackage: String): Boolean {
            val manager = ConfigurationSyncManager(requireContext())
            val plainPackage = runCatching {
                manager.decryptEncryptedSyncPackageWithSavedPassword(syncPackage)
            }.getOrNull() ?: return false

            previewSyncPackageForImport(plainPackage, null)
            return true
        }

        private fun showExternalSyncPasswordDialog() {
            showConfigSyncPasswordDialog(
                titleRes = R.string.title_config_sync_backup_password,
                messageRes = R.string.message_config_sync_backup_password_external,
                positiveRes = R.string.config_sync_action_save_password
            ) { password ->
                if (ConfigurationSyncManager(requireContext()).saveExternalSyncPassword(password)) {
                    Toast.makeText(context, R.string.toast_config_sync_password_saved, Toast.LENGTH_SHORT).show()
                    updateExternalSyncDirectorySummary()
                    updateConfigSyncStatusSummary()
                } else {
                    Toast.makeText(context, R.string.toast_config_sync_password_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun showConfigSyncPasswordDialog(
            titleRes: Int,
            messageRes: Int,
            positiveRes: Int,
            onPassword: (String) -> Unit
        ) {
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                hint = getString(R.string.hint_config_sync_backup_password)
            }
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val inset = (24 * resources.displayMetrics.density).toInt()
                setPadding(inset, 0, inset, 0)
                addView(input)
            }

            val dialog = AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setView(container)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password = input.text?.toString().orEmpty()
                    if (password.isBlank()) {
                        input.error = getString(R.string.toast_config_sync_password_required)
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    onPassword(password)
                }
                input.requestFocus()
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
            showStyledDialog(dialog)
        }

        private fun showSyncImportPreview(preview: ConfigurationSyncManager.PackagePreview) {
            val sourceVersion = preview.appVersionName.takeIf { it.isNotBlank() }
                ?: if (preview.appVersionCode > 0) preview.appVersionCode.toString()
                else getString(R.string.config_sync_unknown_version)

            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                .setTitle(R.string.title_config_sync_import)
                .setMessage(
                    getString(
                        R.string.message_config_sync_import_preview,
                        sourceVersion,
                        preview.defaultPreferenceCount,
                        preview.appLastSettingsCount,
                        preview.customResolutionsCount,
                        preview.sceneConfigsCount,
                        preview.appViewPreferenceCount,
                        preview.hiddenAppsCount,
                        preview.crownProfilesCount,
                        preview.pairedComputersCount,
                        formatSyncStatusFlag(preview.hasPairingIdentity)
                    )
                )
                .setPositiveButton(R.string.config_sync_action_import) { _, _ -> importPendingSyncPackage() }
                .setNegativeButton(android.R.string.cancel, null)
                .showStyled()
        }

        private fun importPendingSyncPackage() {
            val syncPackage = pendingSyncImportString
            if (syncPackage.isBlank()) return

            try {
                val importResult = ConfigurationSyncManager(requireContext()).importSyncPackage(syncPackage)
                val failedItems = importResult.crownProfilesFailed + importResult.pairingItemsFailed
                val toastRes = if (failedItems > 0) {
                    R.string.toast_config_sync_import_success_with_failures
                } else {
                    R.string.toast_config_sync_import_success
                }
                val toastText = if (failedItems > 0) {
                    getString(toastRes, importResult.totalImported, failedItems)
                } else {
                    getString(toastRes, importResult.totalImported)
                }
                pendingSyncImportString = ""
                Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                maybeShowPairingRestoreRestartPrompt(
                    importResult.pairingItemsImported,
                    importResult.pairingItemsFailed
                )
                requestConfigSyncAutoSnapshot(delayMs = 0L)
                Handler(Looper.getMainLooper()).post {
                    (activity as? StreamSettings)?.reloadSettings()
                }
            } catch (e: Exception) {
                Log.e("ConfigSync", "Failed to apply configuration sync package", e)
                Toast.makeText(context, R.string.toast_config_sync_import_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun maybeShowPairingRestoreRestartPrompt(imported: Int, failed: Int) {
            if (!isAdded || imported <= 0 && failed <= 0) return
            val message = if (failed > 0) {
                getString(R.string.message_config_sync_restart_required_with_failures, failed)
            } else {
                getString(R.string.message_config_sync_restart_required)
            }
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                .setTitle(R.string.title_config_sync_restart_required)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .showStyled()
        }

        private fun showCrownConfigManagementDialog() {
            val options = arrayOf(
                    getString(R.string.crown_store_action_browse),
                    getString(R.string.crown_store_action_publish),
                    getString(R.string.crown_share_action_import),
                    getString(R.string.crown_share_action_import_url),
                    getString(R.string.crown_share_action_export),
                    getString(R.string.crown_config_action_import_legacy),
                    getString(R.string.crown_config_action_export_legacy),
                    getString(R.string.crown_config_action_merge)
            )

            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_crown_config_management)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> openCrownStore()
                            1 -> showCrownStorePublishProfileDialog()
                            2 -> openCrownShareDocument()
                            3 -> showCrownShareUrlImportDialog()
                            4 -> showCrownShareExportConfigDialog()
                            5 -> openConfigDocument(2)
                            6 -> showCrownExportConfigDialog()
                            7 -> showCrownMergeConfigDialog()
                        }
                    }
                    .showStyledChoiceList()
        }

        private fun showCrownShareExportConfigDialog() {
            val helper = SuperConfigDatabaseHelper(context)
            val configMap = loadConfigMap(helper)
            if (configMap.isEmpty()) {
                Toast.makeText(context, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
                return
            }

            val ids = configMap.keys.toTypedArray()
            val names = configMap.values.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_share_action_export)
                    .setItems(names) { _, which ->
                        val id = ids[which]
                        val profileName = configMap[id] ?: "Crown Profile"
                        val payload = helper.exportConfig(id.toLong())
                        try {
                            pendingCrownShareExportString = CrownProfileShareManager.createBundle(
                                profileName = profileName,
                                payload = payload,
                                metadata = currentCrownShareExportMetadata()
                            )
                            createCrownShareDocument(profileName)
                        } catch (e: Exception) {
                            Log.e("CrownShare", "Failed to export Crown share package", e)
                            Toast.makeText(context, R.string.toast_crown_share_export_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    .showStyledChoiceList()
        }

        private fun currentCrownShareExportMetadata(): CrownProfileShareManager.ExportMetadata {
            val ctx = requireContext()
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            return CrownProfileShareManager.ExportMetadata(
                packageName = ctx.packageName,
                appVersionCode = versionCode,
                appVersionName = packageInfo.versionName ?: "",
                layoutBasis = currentLayoutBasis()
            )
        }

        private fun currentLayoutBasis(): CrownProfileShareManager.LayoutBasis {
            val metrics = resources.displayMetrics
            val orientation = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> "landscape"
                Configuration.ORIENTATION_PORTRAIT -> "portrait"
                else -> "unknown"
            }
            return CrownProfileShareManager.LayoutBasis(
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                density = metrics.density,
                orientation = orientation
            )
        }

        private fun handleCrownShareExportResult(data: Intent?) {
            val uri = data?.data ?: return
            try {
                writeDocumentText(uri, pendingCrownShareExportString)
                Toast.makeText(context, R.string.toast_crown_share_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("CrownShare", "Failed to write Crown share package", e)
                Toast.makeText(context, R.string.toast_crown_share_export_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun handleCrownShareImportResult(data: Intent?) {
            val uri = data?.data ?: return
            try {
                val importText = readDocumentText(uri)
                val importedProfile = CrownProfileShareManager.parseImportText(importText)
                pendingCrownShareImport = importedProfile
                showCrownShareImportPreview(importedProfile)
            } catch (e: Exception) {
                Log.e("CrownShare", "Failed to read Crown share package", e)
                Toast.makeText(context, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun openCrownStore() {
            val appContext = requireContext().applicationContext
            Toast.makeText(context, R.string.toast_crown_store_loading, Toast.LENGTH_SHORT).show()
            thread(name = "CrownStoreIndex") {
                val result = runCatching {
                    val indexText = downloadRemoteText(CROWN_STORE_INDEX_URL, CROWN_STORE_MAX_INDEX_BYTES)
                    CrownProfileShareManager.parseStoreIndex(indexText)
                }

                Handler(Looper.getMainLooper()).post {
                    if (!isAdded) return@post
                    result
                            .onSuccess { profiles ->
                                if (profiles.isEmpty()) {
                                    Toast.makeText(
                                            appContext,
                                            R.string.toast_crown_store_empty,
                                            Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    showCrownStoreDialog(profiles)
                                }
                            }
                            .onFailure {
                                Log.e("CrownStore", "Failed to load Crown profile store", it)
                                Toast.makeText(
                                        appContext,
                                        R.string.toast_crown_store_failed,
                                        Toast.LENGTH_LONG
                                ).show()
                            }
                }
            }
        }

        private fun showCrownStoreDialog(profiles: List<CrownProfileShareManager.StoreProfile>) {
            val labels = profiles.map { crownStoreProfileLabel(it) }.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_store_action_browse)
                    .setItems(labels) { _, which ->
                        val profile = profiles[which]
                        val profileUrl = try {
                            CrownProfileShareManager.resolveStoreProfileUrl(CROWN_STORE_INDEX_URL, profile.url)
                        } catch (e: Exception) {
                            Log.e("CrownStore", "Invalid Crown store profile URL", e)
                            Toast.makeText(context, R.string.toast_crown_store_profile_failed, Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        importCrownShareFromUrl(
                                profileUrl,
                                sourceLabelOverride = getString(R.string.crown_store_source_label, profile.name),
                                failureToastRes = R.string.toast_crown_store_profile_failed
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showStyledChoiceList()
        }

        private fun crownStoreProfileLabel(profile: CrownProfileShareManager.StoreProfile): String {
            val details = arrayOf(profile.game, profile.author)
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
            return buildString {
                append(profile.name)
                if (details.isNotBlank()) {
                    append('\n')
                    append(details)
                }
                if (profile.summary.isNotBlank()) {
                    append('\n')
                    append(profile.summary)
                }
            }
        }

        private fun showCrownStorePublishProfileDialog() {
            val ctx = requireContext()
            if (!GitHubStarVerifier.isConfigured()) {
                Toast.makeText(ctx, R.string.toast_developer_oauth_unconfigured, Toast.LENGTH_LONG).show()
                return
            }

            val accessToken = PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(DeveloperUnlockSettings.PREF_ACCESS_TOKEN, null)
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            if (accessToken.isNullOrBlank() ||
                    !DeveloperUnlockSettings.hasAccessTokenScope(
                            prefs,
                            GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH
                    )) {
                showCrownStoreGitHubAuthorizationRequiredDialog(clearSavedToken = false)
                return
            }

            val helper = SuperConfigDatabaseHelper(context)
            val configMap = loadConfigMap(helper)
            if (configMap.isEmpty()) {
                Toast.makeText(context, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
                return
            }

            val ids = configMap.keys.toTypedArray()
            val names = configMap.values.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_store_action_publish)
                    .setItems(names) { _, which ->
                        showCrownStorePublishMetadataDialog(ids[which], configMap[ids[which]] ?: "Crown Profile")
                    }
                    .showStyledChoiceList()
        }

        private fun showCrownStorePublishMetadataDialog(configId: String, defaultName: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val defaultAuthor = prefs.getString(DeveloperUnlockSettings.PREF_USER_LOGIN, null).orEmpty()
            val nameInput = crownStorePublishInput(defaultName, R.string.hint_crown_store_profile_name)
            val gameInput = crownStorePublishInput("", R.string.hint_crown_store_game)
            val authorInput = crownStorePublishInput(defaultAuthor, R.string.hint_crown_store_author)
            val tagsInput = crownStorePublishInput("", R.string.hint_crown_store_tags)
            val summaryInput = crownStorePublishInput("", R.string.hint_crown_store_summary).apply {
                setSingleLine(false)
                minLines = 2
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val inset = (24 * resources.displayMetrics.density).toInt()
                setPadding(inset, 0, inset, 0)
                addCrownStorePublishField(R.string.label_crown_store_profile_name, nameInput)
                addCrownStorePublishField(R.string.label_crown_store_game, gameInput)
                addCrownStorePublishField(R.string.label_crown_store_author, authorInput)
                addCrownStorePublishField(R.string.label_crown_store_tags, tagsInput)
                addCrownStorePublishField(R.string.label_crown_store_summary, summaryInput)
            }

            val dialog = AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_crown_store_publish_metadata)
                    .setMessage(R.string.message_crown_store_publish_metadata)
                    .setView(container)
                    .setPositiveButton(R.string.crown_store_action_publish, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val profileName = nameInput.text?.toString().orEmpty().trim()
                    if (profileName.isBlank()) {
                        nameInput.error = getString(R.string.hint_crown_store_profile_name)
                        return@setOnClickListener
                    }

                    val game = gameInput.text?.toString().orEmpty().trim()
                    val author = authorInput.text?.toString().orEmpty().trim()
                    val summary = summaryInput.text?.toString().orEmpty().trim()
                    val tags = tagsInput.text?.toString().orEmpty()
                            .split(Regex("[,\\s\\uFF0C]+"))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()

                    try {
                        val payload = SuperConfigDatabaseHelper(context).exportConfig(configId.toLong())
                        val bundle = CrownProfileShareManager.createBundle(
                                profileName = profileName,
                                payload = payload,
                                metadata = currentCrownShareExportMetadata(),
                                displayMetadata = CrownProfileShareManager.BundleDisplayMetadata(
                                        summary = summary,
                                        authorName = author,
                                        gameName = game,
                                        tags = tags
                                )
                        )
                        dialog.dismiss()
                        publishCrownStoreProfile(
                                GitHubCrownProfileStorePublisher.PublishRequest(
                                        profileName = profileName,
                                        summary = summary,
                                        author = author,
                                        game = game,
                                        tags = tags,
                                        bundleJson = bundle
                                )
                        )
                    } catch (e: Exception) {
                        Log.e("CrownStore", "Failed to prepare Crown Store profile", e)
                        Toast.makeText(context, R.string.toast_crown_store_publish_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            showStyledDialog(dialog)
        }

        private fun crownStorePublishInput(value: String, hintRes: Int): EditText {
            return EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(true)
                setText(value)
                hint = getString(hintRes)
            }
        }

        private fun LinearLayout.addCrownStorePublishField(labelRes: Int, input: EditText) {
            addView(TextView(context).apply {
                text = getString(labelRes)
            })
            addView(input)
        }

        private fun publishCrownStoreProfile(request: GitHubCrownProfileStorePublisher.PublishRequest) {
            val appContext = requireContext().applicationContext
            val accessToken = PreferenceManager.getDefaultSharedPreferences(appContext)
                    .getString(DeveloperUnlockSettings.PREF_ACCESS_TOKEN, null)
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            if (accessToken.isNullOrBlank() ||
                    !DeveloperUnlockSettings.hasAccessTokenScope(
                            prefs,
                            GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH
                    )) {
                showCrownStoreGitHubAuthorizationRequiredDialog(clearSavedToken = false)
                return
            }

            Toast.makeText(context, R.string.toast_crown_store_publish_started, Toast.LENGTH_LONG).show()
            thread(name = "CrownStorePublish") {
                val result = runCatching {
                    GitHubCrownProfileStorePublisher.publish(accessToken, request)
                }

                Handler(Looper.getMainLooper()).post {
                    if (!isAdded) return@post
                    result
                            .onSuccess { publishResult ->
                                showCrownStorePublishSuccessDialog(publishResult)
                            }
                            .onFailure { error ->
                                Log.e("CrownStore", "Failed to publish Crown Store profile", error)
                                if (error is GitHubCrownProfileStorePublisher.GitHubCrownStoreException &&
                                        error.authorizationFailure) {
                                    showCrownStoreGitHubAuthorizationRequiredDialog(clearSavedToken = true)
                                } else {
                                    Toast.makeText(
                                            appContext,
                                            getString(
                                                    R.string.toast_crown_store_publish_failed_with_error,
                                                    error.message ?: error.javaClass.simpleName
                                            ),
                                            Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                }
            }
        }

        private fun showCrownStoreGitHubAuthorizationRequiredDialog(clearSavedToken: Boolean) {
            val ctx = requireContext()
            if (clearSavedToken) {
                PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                    remove(DeveloperUnlockSettings.PREF_ACCESS_TOKEN)
                    remove(DeveloperUnlockSettings.PREF_ACCESS_TOKEN_SCOPE)
                    remove(DeveloperUnlockSettings.PREF_UNLOCKED)
                    remove(DeveloperUnlockSettings.PREF_VERIFIED_AT_MS)
                }
                refreshDeveloperFeatureGateState()
            }

            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_crown_store_github_authorization)
                    .setMessage(
                            if (clearSavedToken) {
                                R.string.message_crown_store_github_reauthorization_required
                            } else {
                                R.string.message_crown_store_github_authorization_required
                            }
                    )
                    .setPositiveButton(R.string.action_crown_store_authorize_github) { _, _ ->
                        startDeveloperUnlockVerification(GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showStyled()
        }

        private fun showCrownStorePublishSuccessDialog(result: GitHubCrownProfileStorePublisher.PublishResult) {
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_crown_store_publish_success)
                    .setMessage(
                            getString(
                                    R.string.message_crown_store_publish_success,
                                    result.profilePath,
                                    result.pullRequestUrl
                            )
                    )
                    .setPositiveButton(R.string.action_open_pull_request) { _, _ ->
                        openDeveloperUrl(result.pullRequestUrl)
                    }
                    .setNegativeButton(android.R.string.ok, null)
                    .showStyled()
        }

        private fun showCrownShareUrlImportDialog() {
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                hint = getString(R.string.hint_crown_share_import_url)
            }
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val inset = (24 * resources.displayMetrics.density).toInt()
                setPadding(inset, 0, inset, 0)
                addView(input)
            }

            val dialog = AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_share_action_import_url)
                    .setMessage(R.string.message_crown_share_import_url)
                    .setView(container)
                    .setPositiveButton(R.string.crown_share_action_import, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val url = input.text?.toString().orEmpty().trim()
                    if (url.isBlank()) {
                        input.error = getString(R.string.hint_crown_share_import_url)
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    importCrownShareFromUrl(url)
                }
                input.requestFocus()
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
            showStyledDialog(dialog)
        }

        private fun importCrownShareFromUrl(
            url: String,
            sourceLabelOverride: String? = null,
            failureToastRes: Int = R.string.toast_crown_share_url_failed
        ) {
            val normalizedUrl = url.trim()
            if (!normalizedUrl.startsWith("https://", ignoreCase = true) &&
                    !normalizedUrl.startsWith("http://", ignoreCase = true)) {
                Toast.makeText(context, R.string.toast_crown_share_url_invalid, Toast.LENGTH_LONG).show()
                return
            }

            val appContext = requireContext().applicationContext
            Toast.makeText(context, R.string.toast_crown_share_url_loading, Toast.LENGTH_SHORT).show()
            thread(name = "CrownShareUrlImport") {
                val result = runCatching {
                    val importText = downloadRemoteText(normalizedUrl, CROWN_SHARE_MAX_DOWNLOAD_BYTES)
                    CrownProfileShareManager.parseImportText(importText)
                        .copy(sourceLabel = sourceLabelOverride ?: crownShareSourceLabel(normalizedUrl))
                }

                Handler(Looper.getMainLooper()).post {
                    if (!isAdded) return@post
                    result
                            .onSuccess { importedProfile ->
                                pendingCrownShareImport = importedProfile
                                showCrownShareImportPreview(importedProfile)
                            }
                            .onFailure {
                                Log.e("CrownShare", "Failed to import Crown share package from URL", it)
                                Toast.makeText(
                                        appContext,
                                        failureToastRes,
                                        Toast.LENGTH_LONG
                                ).show()
                            }
                }
            }
        }

        private fun crownShareSourceLabel(url: String): String {
            return runCatching {
                URL(url).host
                    .takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.crown_share_source_link_host, it) }
            }.getOrNull() ?: getString(R.string.crown_share_source_link)
        }

        private fun downloadRemoteText(url: String, maxBytes: Int): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json,text/plain,*/*")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("HTTP $responseCode")
                }
                val contentLength = connection.contentLengthLong
                if (contentLength > maxBytes) {
                    throw IOException("Remote Crown profile response is too large")
                }
                return connection.inputStream.use { input ->
                    readLimitedText(input, maxBytes)
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun readLimitedText(input: InputStream, maxBytes: Int): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Remote Crown profile response is too large")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }

        private fun showCrownShareImportPreview(profile: CrownProfileShareManager.ImportedProfile) {
            val details = getString(
                R.string.message_crown_share_import_preview,
                profile.name,
                profile.author.ifBlank { getString(R.string.crown_share_unknown_value) },
                profile.game.ifBlank { getString(R.string.crown_share_unknown_value) },
                profile.sourceLabel,
                profile.payloadInfo.version,
                profile.payloadInfo.elementCount,
                profile.payloadInfo.settingsCount
            )

            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_share_action_import)
                    .setMessage(details)
                    .setPositiveButton(R.string.crown_share_install_as_new) { _, _ ->
                        importPendingCrownShareAsNew()
                    }
                    .setNeutralButton(R.string.crown_share_merge_into_existing) { _, _ ->
                        showCrownShareMergeTargetDialog()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showStyled()
        }

        private fun importPendingCrownShareAsNew() {
            val profile = pendingCrownShareImport ?: return
            val helper = SuperConfigDatabaseHelper(context)
            val errorCode = helper.importConfig(CrownProfileShareManager.payloadForInstallAsNew(profile))
            if (errorCode == 0) {
                pendingCrownShareImport = null
                requestConfigSyncAutoSnapshot(delayMs = 0L)
                Toast.makeText(context, R.string.toast_crown_share_import_success, Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).post {
                    (activity as? StreamSettings)?.reloadSettings()
                }
            } else {
                Toast.makeText(context, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun showCrownShareMergeTargetDialog() {
            val profile = pendingCrownShareImport ?: return
            val configMap = loadConfigMap(SuperConfigDatabaseHelper(context))
            if (configMap.isEmpty()) {
                Toast.makeText(context, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
                return
            }

            val ids = configMap.keys.toTypedArray()
            val names = configMap.values.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.crown_share_merge_into_existing)
                    .setItems(names) { _, which ->
                        val helper = SuperConfigDatabaseHelper(context)
                        val errorCode = helper.mergeConfig(profile.payload, ids[which].toLong())
                        if (errorCode == 0) {
                            pendingCrownShareImport = null
                            requestConfigSyncAutoSnapshot(delayMs = 0L)
                            Toast.makeText(context, R.string.toast_crown_share_merge_success, Toast.LENGTH_SHORT).show()
                            Handler(Looper.getMainLooper()).post {
                                (activity as? StreamSettings)?.reloadSettings()
                            }
                        } else {
                            Toast.makeText(context, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    .showStyledChoiceList()
        }

        private fun showCrownExportConfigDialog() {
            val helper = SuperConfigDatabaseHelper(context)
            val configMap = loadConfigMap(helper)
            if (configMap.isEmpty()) {
                Toast.makeText(context, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
                return
            }

            val ids = configMap.keys.toTypedArray()
            val names = configMap.values.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_export_super_config)
                    .setItems(names) { _, which ->
                        val id = ids[which]
                        exportConfigString = helper.exportConfig(id.toLong())
                        createConfigDocument(configMap[id] ?: "crown_config")
                    }
                    .showStyledChoiceList()
        }

        private fun showCrownMergeConfigDialog() {
            val configMap = loadConfigMap(SuperConfigDatabaseHelper(context))
            if (configMap.isEmpty()) {
                Toast.makeText(context, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
                return
            }

            val ids = configMap.keys.toTypedArray()
            val names = configMap.values.toTypedArray<CharSequence>()
            AlertDialog.Builder(requireActivity(), R.style.AppDialogStyle)
                    .setTitle(R.string.title_merge_super_config)
                    .setItems(names) { _, which ->
                        exportConfigString = ids[which]
                        openConfigDocument(3)
                    }
                    .showStyledChoiceList()
        }

        /**
         * 滚动到指定索引的分类
         */
        private fun scrollToCategoryAtIndex(index: Int) {
            if (index < 0 || index >= categoryList.size) return

            val category = categoryList[index]
            val position = findAdapterPositionForPreference(category)
            if (position >= 0) {
                isManualScrolling = true
                currentCategoryIndex = index

                val recyclerView = listView
                if (recyclerView != null) {
                    val lm = recyclerView.layoutManager
                    if (lm is LinearLayoutManager) {
                        lm.scrollToPositionWithOffset(position, dpToPx(2))
                    }
                }
            }
        }

        /**
         * 设置滚动监听
         */
        private fun setupScrollListener(recyclerView: RecyclerView, settingsActivity: StreamSettings) {
            // 注册数据集变化监听，仅在数据集变化时失效缓存的 category positions
            val adapter = recyclerView.adapter
            if (adapter != null && adapterDataObserver == null) {
                val observer = object : RecyclerView.AdapterDataObserver() {
                    override fun onChanged() { categoryPositionsValid = false }
                    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { categoryPositionsValid = false }
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { categoryPositionsValid = false }
                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { categoryPositionsValid = false }
                    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { categoryPositionsValid = false }
                }
                adapter.registerAdapterDataObserver(observer)
                adapterDataObserver = observer
            }

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        isManualScrolling = false
                        updateVisibleCategory(rv, settingsActivity)
                    }
                }

                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (!isManualScrolling) {
                        updateVisibleCategory(rv, settingsActivity)
                    }
                }
            })
            updateVisibleCategory(recyclerView, settingsActivity)
        }

        override fun onDestroyView() {
            unregisterConfigSyncPreferenceListener()
            configSyncSnapshotHandler.removeCallbacks(configSyncSnapshotRunnable)
            // 注销 adapter observer，避免泄漏
            val obs = adapterDataObserver
            if (obs != null) {
                try {
                    listView?.adapter?.unregisterAdapterDataObserver(obs)
                } catch (_: Exception) {
                }
                adapterDataObserver = null
            }
            categoryPositionsValid = false
            super.onDestroyView()
        }

        override fun onResume() {
            super.onResume()
            registerConfigSyncPreferenceListener()
            updateLocalSnapshotPreferenceSummary()
            updateExternalSyncDirectorySummary()
            updateConfigSyncStatusSummary()
            resumeDeveloperUnlockVerificationIfPending()
        }

        override fun onPause() {
            flushConfigSyncAutoSnapshot()
            unregisterConfigSyncPreferenceListener()
            super.onPause()
        }

        /**
         * 一次性扫描 adapter，建立 category preference -> position 的映射缓存。
         * 避免每个滚动帧都做 O(categories * itemCount) 全表扫描。
         */
        @SuppressLint("RestrictedApi")
        private fun ensureCategoryPositions(recyclerView: RecyclerView): Boolean {
            if (categoryPositionsValid && categoryPositions.size == categoryList.size) {
                return true
            }
            val adapter = recyclerView.adapter as? PreferenceGroupAdapter ?: return false
            val n = categoryList.size
            if (categoryPositions.size != n) {
                categoryPositions = IntArray(n) { -1 }
            } else {
                for (i in 0 until n) categoryPositions[i] = -1
            }
            // 用引用比较的 IdentityHashMap 反查表，单次 O(itemCount)
            val targets = java.util.IdentityHashMap<Preference, Int>(n * 2)
            for (i in 0 until n) targets[categoryList[i]] = i
            val total = adapter.itemCount
            var found = 0
            for (i in 0 until total) {
                val pref = adapter.getItem(i) ?: continue
                val idx = targets[pref] ?: continue
                if (categoryPositions[idx] == -1) {
                    categoryPositions[idx] = i
                    found++
                    if (found >= n) break
                }
            }
            categoryPositionsValid = true
            return true
        }

        /**
         * 更新当前可见分类
         * 使用缓存的 categoryPositions 配合 firstVisiblePosition 做 O(N) 单次扫描，
         * 替代原先每帧 O(categories * adapter.itemCount) 的全表扫。
         */
        private fun updateVisibleCategory(recyclerView: RecyclerView?, settingsActivity: StreamSettings) {
            if (recyclerView == null || categoryList.isEmpty()) return

            val lm = recyclerView.layoutManager
            if (lm !is LinearLayoutManager) return

            if (!ensureCategoryPositions(recyclerView)) return

            val firstVisiblePosition = lm.findFirstVisibleItemPosition()
            if (firstVisiblePosition < 0) return

            // 找到最大的 position <= firstVisiblePosition 的 category
            // categoryPositions 通常按 adapter 顺序递增，但保险起见线性扫描（N ~ 15）
            var newCategoryIndex = -1
            var bestPosition = -1
            for (i in 0 until categoryPositions.size) {
                val p = categoryPositions[i]
                if (p in 0..firstVisiblePosition && p > bestPosition) {
                    bestPosition = p
                    newCategoryIndex = i
                }
            }
            // 若所有 category 都在 first 之后，则取第一个可见的
            if (newCategoryIndex < 0) {
                val lastVisible = lm.findLastVisibleItemPosition()
                for (i in 0 until categoryPositions.size) {
                    val p = categoryPositions[i]
                    if (p in 0..lastVisible) {
                        newCategoryIndex = i
                        break
                    }
                }
            }

            if (newCategoryIndex >= 0 && newCategoryIndex != currentCategoryIndex) {
                currentCategoryIndex = newCategoryIndex
                settingsActivity.updateSelectedCategory(currentCategoryIndex)
            }
        }

        private fun dpToPx(dp: Int): Int {
            val density = resources.displayMetrics.density
            return (dp * density).roundToInt()
        }

        @SuppressLint("RestrictedApi")
        private fun findAdapterPositionForPreference(target: Preference?): Int {
            val recyclerView = listView ?: return -1
            if (target == null) return -1
            // 优先走缓存
            if (categoryPositionsValid && categoryPositions.size == categoryList.size) {
                val idx = categoryList.indexOfFirst { it === target }
                if (idx >= 0) return categoryPositions[idx]
            }
            val adapter = recyclerView.adapter
            if (adapter is PreferenceGroupAdapter) {
                for (i in 0 until adapter.itemCount) {
                    if (adapter.getItem(i) === target) return i
                }
            }
            return -1
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // 添加阴影主题
            requireActivity().theme.applyStyle(R.style.PreferenceThemeWithShadow, true)

            setPreferencesFromResource(R.xml.preferences, rootKey)
            val screen = preferenceScreen

            setupFramegenPreferences()
            setupConfigSyncPreferences()
            setupMicVolumeProcessingPreferences()

            // 让所有 ListPreference 在 summary 顶部显示当前选中值，
            // 避免用户必须点开才知道现值。原 summary 作为说明保留在第二行。
            applyListPreferenceCurrentValueSummary(screen)
            updateHostCadencePreciseSyncVisibility()

            // 为 LocalImagePickerPreference 设置 Fragment 实例，确保 onActivityResult 回调正确
            val localImagePicker = findPreference<LocalImagePickerPreference>("local_image_picker")
            localImagePicker?.setFragment(this)

            // Route API URL changes through BackgroundSource so source state,
            // cache invalidation, and the live refresh stay atomic.
            val backgroundImageUrlPref = findPreference<EditTextPreference>("background_image_url")
            backgroundImageUrlPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val context = requireActivity()
                val url = (newValue as? String).orEmpty().trim()

                if (url.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                        putString(BackgroundSource.KEY_API_URL, url)
                    }
                    BackgroundSource.setActivePreservingExtras(context, BackgroundSource.Api)
                } else {
                    BackgroundSource.setActive(context, BackgroundSource.Auto)
                }

                true
            }

            // hide on-screen controls category on non touch screen devices
            if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                val category = findPreference<PreferenceCategory>("category_onscreen_controls")!!
                screen.removePreference(category)
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    requireActivity().packageManager.hasSystemFeature("com.nvidia.feature.shield")) {
                val category = findPreference<PreferenceCategory>("category_input_settings")!!
                category.removePreference(findPreference("checkbox_absolute_mouse_mode")!!)
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val category = findPreference<PreferenceCategory>("category_gamepad_settings")!!
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors")!!)
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                val category = findPreference<PreferenceCategory>("category_gamepad_settings")!!
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback")!!)
            }

            // Hide USB driver options on devices without USB host support
            if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                val category = findPreference<PreferenceCategory>("category_gamepad_settings")!!
                category.removePreference(findPreference("checkbox_usb_bind_all")!!)
                category.removePreference(findPreference("checkbox_usb_driver")!!)
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !requireActivity().packageManager.hasSystemFeature("android.software.picture_in_picture") ||
                    requireActivity().packageManager.hasSystemFeature("com.amazon.software.fireos")) {
                val category = findPreference<PreferenceCategory>("category_screen_position")!!
                category.removePreference(findPreference("checkbox_enable_pip")!!)
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            // (currently disabled — keep Help category visible on all builds)
            val categoryGamepadSettings = findPreference<PreferenceCategory>("category_gamepad_settings")!!
            // Remove the vibration options if the device can't vibrate
            if (!(requireActivity().getSystemService(VIBRATOR_SERVICE) as Vibrator).hasVibrator()) {
                categoryGamepadSettings.removePreference(findPreference("checkbox_vibrate_fallback")!!)
                categoryGamepadSettings.removePreference(findPreference("seekbar_vibrate_fallback_strength")!!)
                // The entire OSC category may have already been removed by the touchscreen check above
                val category = findPreference<PreferenceCategory>("category_onscreen_controls")
                category?.removePreference(findPreference("checkbox_vibrate_osc")!!)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !(requireActivity().getSystemService(VIBRATOR_SERVICE) as Vibrator).hasAmplitudeControl()) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                categoryGamepadSettings.removePreference(findPreference("seekbar_vibrate_fallback_strength")!!)
            }

            // 获取目标显示器（优先使用外接显示器）
            val display = getTargetDisplay()
            var maxSupportedFps = display.refreshRate

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var maxSupportedResW = 0

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                var hasInsets = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val cutout: DisplayCutout? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        display.cutout
                    } else {
                        // Android 9 only
                        displayCutoutP
                    }

                    if (cutout != null) {
                        val widthInsets = cutout.safeInsetLeft + cutout.safeInsetRight
                        val heightInsets = cutout.safeInsetBottom + cutout.safeInsetTop

                        if (widthInsets != 0 || heightInsets != 0) {
                            val metrics = DisplayMetrics()
                            display.getRealMetrics(metrics)

                            val width =
                                (metrics.widthPixels - widthInsets).coerceAtLeast(metrics.heightPixels - heightInsets)
                            val height =
                                (metrics.widthPixels - widthInsets).coerceAtMost(metrics.heightPixels - heightInsets)

                            addNativeResolutionEntries(width, height, false)
                            hasInsets = true
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                for (candidate in display.supportedModes) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    val width = candidate.physicalWidth.coerceAtLeast(candidate.physicalHeight)
                    val height = candidate.physicalWidth.coerceAtMost(candidate.physicalHeight)

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets)
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840
                    } else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560
                    } else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920
                    }

                    if (candidate.refreshRate > maxSupportedFps) {
                        maxSupportedFps = candidate.refreshRate
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(requireContext(), GlPreferences.readPreferences(requireContext()).glRenderer)

                val avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1)
                val hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1)

                if (avcDecoder != null) {
                    val avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").videoCapabilities?.supportedWidths

                    if (avcWidthRange != null) {
                        LimeLog.info("AVC supported width range: ${avcWidthRange.lower} - ${avcWidthRange.upper}")

                        // If 720p is not reported as supported, ignore all results from this API
                        if (avcWidthRange.contains(1280)) {
                            if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                                maxSupportedResW = 3840
                            } else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                                maxSupportedResW = 1920
                            } else if (maxSupportedResW < 1280) {
                                maxSupportedResW = 1280
                            }
                        }
                    }
                }

                if (hevcDecoder != null) {
                    val hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").videoCapabilities?.supportedWidths

                    if (hevcWidthRange != null) {
                        LimeLog.info("HEVC supported width range: ${hevcWidthRange.lower} - ${hevcWidthRange.upper}")

                        // If 720p is not reported as supported, ignore all results from this API
                        if (hevcWidthRange.contains(1280)) {
                            if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                                maxSupportedResW = 3840
                            } else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                                maxSupportedResW = 1920
                            } else if (maxSupportedResW < 1280) {
                                maxSupportedResW = 1280
                            }
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: $maxSupportedResW")

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K) {
                            val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                            setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P)
                            resetBitrateToDefault(prefs, null, null)
                        }
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P) {
                            val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                            setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P)
                            resetBitrateToDefault(prefs, null, null)
                        }
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P) {
                            val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                            setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P)
                            resetBitrateToDefault(prefs, null, null)
                        }
                    }
                    // Never remove 720p
                }
            } else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                val metrics = DisplayMetrics()
                display.getRealMetrics(metrics)
                val width = metrics.widthPixels.coerceAtLeast(metrics.heightPixels)
                val height = metrics.widthPixels.coerceAtMost(metrics.heightPixels)
                addNativeResolutionEntries(width, height, false)
            }

            if (!PreferenceConfiguration.readPreferences(requireActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 162) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "165") {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        setValue(PreferenceConfiguration.FPS_PREF_STRING, "144")
                        resetBitrateToDefault(prefs, null, null)
                    }
                }
                if (maxSupportedFps < 141) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "144") {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        setValue(PreferenceConfiguration.FPS_PREF_STRING, "120")
                        resetBitrateToDefault(prefs, null, null)
                    }
                }
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120") {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        setValue(PreferenceConfiguration.FPS_PREF_STRING, "90")
                        resetBitrateToDefault(prefs, null, null)
                    }
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90") {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        setValue(PreferenceConfiguration.FPS_PREF_STRING, "60")
                        resetBitrateToDefault(prefs, null, null)
                    }
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps)

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference<Preference>(PreferenceConfiguration.UNLOCK_FPS_STRING)!!.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, _ ->
                        // HACK: We need to let the preference change succeed before reinitializing to ensure
                        // it's reflected in the new layout.
                        val h = Handler(Looper.getMainLooper())
                        h.postDelayed({
                            // Ensure the activity is still open when this timeout expires
                            val settingsActivity = this@SettingsFragment.activity as? StreamSettings
                            settingsActivity?.reloadSettings()
                        }, 500)

                        // Allow the original preference change to take place
                        true
                    }

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS")
                val category = findPreference<PreferenceCategory>("category_screen_position")!!
                // 必须先移除依赖项，再移除被依赖的项，否则会崩溃
                val hdrModePref = findPreference<Preference>("list_hdr_mode")
                if (hdrModePref != null) {
                    category.removePreference(hdrModePref)
                }
                val hdrPeakBrightnessPref = findPreference<Preference>("seekbar_hdr_peak_brightness_nits")
                if (hdrPeakBrightnessPref != null) {
                    category.removePreference(hdrPeakBrightnessPref)
                }
                val hdrBrightnessOverridePref = findPreference<Preference>("checkbox_hdr_brightness_override")
                if (hdrBrightnessOverridePref != null) {
                    category.removePreference(hdrBrightnessOverridePref)
                }
                val hdrHighBrightnessPref = findPreference<Preference>("checkbox_enable_hdr_high_brightness")
                if (hdrHighBrightnessPref != null) {
                    category.removePreference(hdrHighBrightnessPref)
                }
                val hdrPref = findPreference<Preference>("checkbox_enable_hdr")
                if (hdrPref != null) {
                    category.removePreference(hdrPref)
                }
            } else {
                // 获取目标显示器的 HDR 能力（优先使用外接显示器）
                val targetDisplay = getTargetDisplay()

                // Settings are shown before a concrete display mode is selected, so use the
                // display-wide capabilities rather than restricting the menu to the current mode.
                val hdrTypeSupport = HdrCapabilityHelper.getDisplayWideHdrTypeSupport(targetDisplay)
                val foundHdr10 = hdrTypeSupport.hasHdr10
                val foundHdr10Plus = hdrTypeSupport.hasHdr10Plus
                val foundHlg = hdrTypeSupport.hasHlg

                val category = findPreference<PreferenceCategory>("category_screen_position")!!
                val hdrPref = findPreference<CheckBoxPreference>("checkbox_enable_hdr")
                val hdrHighBrightnessPref = findPreference<CheckBoxPreference>("checkbox_enable_hdr_high_brightness")
                val hdrBrightnessOverridePref = findPreference<CheckBoxPreference>("checkbox_hdr_brightness_override")
                val hdrPeakBrightnessPref = findPreference<Preference>("seekbar_hdr_peak_brightness_nits")
                val hdrModePref = findPreference<ListPreference>("list_hdr_mode")

                if (!foundHdr10 && !foundHdr10Plus && !foundHlg) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities")
                    // 必须先移除依赖项，再移除被依赖的项，否则会崩溃
                    if (hdrModePref != null) {
                        category.removePreference(hdrModePref)
                    }
                    if (hdrPeakBrightnessPref != null) {
                        category.removePreference(hdrPeakBrightnessPref)
                    }
                    if (hdrBrightnessOverridePref != null) {
                        category.removePreference(hdrBrightnessOverridePref)
                    }
                    if (hdrHighBrightnessPref != null) {
                        category.removePreference(hdrHighBrightnessPref)
                    }
                    if (hdrPref != null) {
                        category.removePreference(hdrPref)
                    }
                } else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware")
                    if (hdrPref != null) {
                        hdrPref.isEnabled = false
                        hdrPref.isChecked = false
                        hdrPref.summary = "Update the firmware on your NVIDIA SHIELD Android TV to enable HDR"
                    }
                    // 同时禁用 HDR 高亮度选项
                    if (hdrHighBrightnessPref != null) {
                        hdrHighBrightnessPref.isEnabled = false
                        hdrHighBrightnessPref.isChecked = false
                    }
                    if (hdrBrightnessOverridePref != null) {
                        hdrBrightnessOverridePref.isEnabled = false
                        hdrBrightnessOverridePref.isChecked = false
                    }
                    if (hdrPeakBrightnessPref != null) {
                        hdrPeakBrightnessPref.isEnabled = false
                    }
                    // 同时禁用 HDR 模式选项
                    if (hdrModePref != null) {
                        hdrModePref.isEnabled = false
                    }
                } else {
                    // HDR is supported, configure the HDR mode preference
                    if (hdrModePref != null) {
                        val entries = mutableListOf<CharSequence>()
                        val entryValues = mutableListOf<CharSequence>()

                        if (foundHdr10 || foundHdr10Plus) {
                            entries += getString(R.string.hdr_mode_hdr10)
                            entryValues += "1"
                        }
                        if (foundHdr10Plus) {
                            entries += getString(R.string.hdr_mode_hdr10_plus)
                            entryValues += "3"
                        }
                        if (foundHlg) {
                            entries += getString(R.string.hdr_mode_hlg)
                            entryValues += "2"
                        }

                        hdrModePref.entries = entries.toTypedArray()
                        hdrModePref.entryValues = entryValues.toTypedArray()

                        // An HDR10+ selection may have been restored from another display/profile.
                        // Prefer static HDR10 when this display cannot present HDR10+.
                        if (hdrModePref.value == "3" && !foundHdr10Plus && foundHdr10) {
                            hdrModePref.value = "1"
                        }
                        if (hdrModePref.value !in entryValues) {
                            hdrModePref.value = entryValues.first().toString()
                        }

                        // 当前选中值由通用的 SummaryProvider 自动显示（applyListPreferenceCurrentValueSummary），
                        // 这里不再单独设置 summary，避免与 SummaryProvider 互斥而抛 IllegalStateException
                    }
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference<Preference>(PreferenceConfiguration.RESOLUTION_PREF_STRING)!!.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { preference, newValue ->
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        val valueStr = newValue as String

                        // Detect if this value is the native resolution option
                        val values = (preference as ListPreference).entryValues
                        var isNativeRes = true
                        for (i in values.indices) {
                            // Look for a match prior to the start of the native resolution entries
                            if (valueStr == values[i].toString() && i < nativeResolutionStartIndex) {
                                isNativeRes = false
                                break
                            }
                        }

                        // If this is native resolution, show the warning dialog
                        if (isNativeRes) {
                            Dialog.displayDialog(requireActivity(),
                                    resources.getString(R.string.title_native_res_dialog),
                                    resources.getString(R.string.text_native_res_dialog),
                                    false)
                        }

                        // Write the new bitrate value
                        resetBitrateToDefault(prefs, valueStr, null)

                        // Allow the original preference change to take place
                        true
                    }
            findPreference<Preference>(PreferenceConfiguration.FPS_PREF_STRING)!!.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { preference, newValue ->
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                        val valueStr = newValue as String

                        // If this is native frame rate, show the warning dialog
                        val values = (preference as ListPreference).entryValues
                        if (nativeFramerateShown && values[values.size - 1].toString() == newValue) {
                            Dialog.displayDialog(requireActivity(),
                                    resources.getString(R.string.title_native_fps_dialog),
                                    resources.getString(R.string.text_native_res_dialog),
                                    false)
                        }

                        // Write the new bitrate value
                        resetBitrateToDefault(prefs, null, valueStr)

                        // Allow the original preference change to take place
                        true
                    }
            findPreference<Preference>(PreferenceConfiguration.FRAME_PACING_PREF_STRING)!!.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, newValue ->
                        updateHostCadencePreciseSyncVisibility(newValue as String)
                        true
                    }
            findPreference<Preference>(PreferenceConfiguration.CROWN_CONFIG_MANAGEMENT_STRING)!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        startActivity(Intent(requireActivity(), CrownStoreActivity::class.java))
                        true
                    }

            addCustomResolutionsEntries()

            findPreference<Preference>("documentation_handbook")!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        HandbookLauncher.openIndex(requireActivity())
                        true
                    }

            findPreference<Preference>("about_us")!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        AboutDialogLauncher.show(requireActivity())
                        true
                    }

            // 添加检查更新选项的点击事件
            findPreference<Preference>("check_for_updates")!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        UpdateManager.checkForUpdates(requireActivity(), true)
                        true
                    }

            // 编解码与屏幕能力检测
            findPreference<Preference>("capability_diagnostic")!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val capIntent = Intent(requireActivity(), CapabilityDiagnosticActivity::class.java)
                        startActivity(capIntent)
                        true
                    }

            findPreference<Preference>("controller_diagnostic")!!.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        startActivity(Intent(requireActivity(), ControllerDiagnosticActivity::class.java))
                        true
                    }

            // 对于没有触摸屏的设备，只提供本地鼠标指针选项
            val mouseModePresetPref = findPreference<ListPreference>(PreferenceConfiguration.NATIVE_MOUSE_MODE_PRESET_PREF_STRING)!!
            if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                // 只显示本地鼠标指针选项
                mouseModePresetPref.entries = arrayOf<CharSequence>(getString(R.string.native_mouse_mode_preset_native))
                mouseModePresetPref.entryValues = arrayOf<CharSequence>("native")
                mouseModePresetPref.value = "native"

                // 强制设置为本地鼠标指针模式
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                prefs.edit {
                    putBoolean(PreferenceConfiguration.ENABLE_ENHANCED_TOUCH_PREF_STRING, false)
                    putBoolean(PreferenceConfiguration.TOUCHSCREEN_TRACKPAD_PREF_STRING, false)
                    putBoolean(
                        PreferenceConfiguration.ENABLE_NATIVE_MOUSE_POINTER_PREF_STRING,
                        true
                    )
                }
            }

            // 添加本地鼠标模式预设选择监听器
            // 每种预设对应 (enhancedTouch, trackpad, nativePointer) 三元组
            val touchPresetMap = mapOf(
                    "enhanced" to Triple(true,  false, false),
                    "classic"  to Triple(false, false, false),
                    "trackpad" to Triple(false, true,  false),
                    "native"   to Triple(false, false, true)
            )
            mouseModePresetPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val preset = newValue as String
                val flags = touchPresetMap[preset] ?: return@OnPreferenceChangeListener true
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsFragment.requireActivity())
                prefs.edit {
                    putBoolean(PreferenceConfiguration.ENABLE_ENHANCED_TOUCH_PREF_STRING, flags.first)
                    putBoolean(PreferenceConfiguration.TOUCHSCREEN_TRACKPAD_PREF_STRING, flags.second)
                    putBoolean(PreferenceConfiguration.ENABLE_NATIVE_MOUSE_POINTER_PREF_STRING, flags.third)
                }

                // 显示提示信息
                val presetName = when (preset) {
                    "enhanced" -> getString(R.string.native_mouse_mode_preset_enhanced)
                    "classic" -> getString(R.string.native_mouse_mode_preset_classic)
                    "trackpad" -> getString(R.string.native_mouse_mode_preset_trackpad)
                    "native" -> getString(R.string.native_mouse_mode_preset_native)
                    else -> ""
                }
                Toast.makeText(activity,
                        getString(R.string.toast_preset_applied, presetName),
                        Toast.LENGTH_SHORT).show()

                true
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            when (preference) {
                is SeekBarPreference -> {
                    val f = SeekBarPreferenceDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "SeekBarPreference")
                }
                is CustomResolutionsPreference -> {
                    val f = CustomResolutionsPreferenceDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "CustomResolutionsPreference")
                }
                is ConfirmDeleteOscPreference -> {
                    val f = ConfirmDeleteOscDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "ConfirmDeleteOscPreference")
                }
                is IconListPreference -> {
                    val f = IconListPreferenceDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "IconListPreference")
                }
                is EditTextPreference -> {
                    val f = StyledEditTextPreferenceDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "StyledEditTextPreference")
                }
                is MultiSelectListPreference -> {
                    val f = StyledMultiSelectListPreferenceDialogFragment.newInstance(preference.key)
                    @Suppress("DEPRECATION")
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, "StyledMultiSelectListPreference")
                }
                is ListPreference -> {
                    if (preference.key == SCREEN_COMBINATION_MODE_PREF_KEY) {
                        (requireActivity() as? StreamSettings)?.showScreenCombinationModePicker(preference)
                    } else {
                        val f = StyledListPreferenceDialogFragment.newInstance(preference.key)
                        @Suppress("DEPRECATION")
                        f.setTargetFragment(this, 0)
                        f.show(parentFragmentManager, "StyledListPreference")
                    }
                }
                else -> super.onDisplayPreferenceDialog(preference)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_CODE_OPEN_CONFIG_SYNC_DIRECTORY && resultCode == RESULT_OK) {
                handleSyncDirectoryResult(data)
                return
            }
            if (requestCode == REQUEST_CODE_CREATE_CONFIG_SYNC && resultCode == RESULT_OK) {
                handleSyncExportResult(data)
                return
            }
            if (requestCode == REQUEST_CODE_OPEN_CONFIG_SYNC && resultCode == RESULT_OK) {
                handleSyncImportResult(data)
                return
            }
            if (requestCode == REQUEST_CODE_CREATE_CROWN_SHARE && resultCode == RESULT_OK) {
                handleCrownShareExportResult(data)
                return
            }
            if (requestCode == REQUEST_CODE_OPEN_CROWN_SHARE && resultCode == RESULT_OK) {
                handleCrownShareImportResult(data)
                return
            }
            //导出配置文件
            if (requestCode == 1 && resultCode == RESULT_OK) {
                val uri = data?.data

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        // 将字符串写入文件
                        val outputStream = requireContext().contentResolver.openOutputStream(uri!!)
                        if (outputStream != null) {
                            outputStream.write(exportConfigString.toByteArray())
                            outputStream.close()
                            Toast.makeText(context, R.string.toast_legacy_config_export_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        Toast.makeText(context, R.string.toast_legacy_config_export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            //导入配置文件
            if (requestCode == 2 && resultCode == RESULT_OK) {
                val importUri = data?.data

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        requireContext().contentResolver.openInputStream(importUri!!).use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                val stringBuilder = StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    stringBuilder.append(line).append("\n")
                                }
                                val fileContent = stringBuilder.toString()
                                val superConfigDatabaseHelper = SuperConfigDatabaseHelper(context)
                                val errorCode = superConfigDatabaseHelper.importConfig(fileContent)
                                if (errorCode == 0) {
                                    requestConfigSyncAutoSnapshot(delayMs = 0L)
                                }
                                when (errorCode) {
                                    0 -> {
                                        Toast.makeText(context, R.string.toast_legacy_config_import_success, Toast.LENGTH_SHORT).show()
                                        //更新导出配置文件列表
                                    }
                                    -1, -2 -> Toast.makeText(context, R.string.toast_legacy_config_read_failed, Toast.LENGTH_SHORT).show()
                                    -3 -> Toast.makeText(context, R.string.toast_legacy_config_version_mismatch, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Toast.makeText(context, R.string.toast_legacy_config_read_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (requestCode == 3 && resultCode == RESULT_OK) {
                val importUri = data?.data

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        requireContext().contentResolver.openInputStream(importUri!!).use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                val stringBuilder = StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    stringBuilder.append(line).append("\n")
                                }
                                val fileContent = stringBuilder.toString()
                                val superConfigDatabaseHelper = SuperConfigDatabaseHelper(context)
                                val errorCode = superConfigDatabaseHelper.mergeConfig(fileContent, exportConfigString.toLong())
                                if (errorCode == 0) {
                                    requestConfigSyncAutoSnapshot(delayMs = 0L)
                                }
                                when (errorCode) {
                                    0 -> Toast.makeText(context, R.string.toast_legacy_config_merge_success, Toast.LENGTH_SHORT).show()
                                    -1, -2 -> Toast.makeText(context, R.string.toast_legacy_config_read_failed, Toast.LENGTH_SHORT).show()
                                    -3 -> Toast.makeText(context, R.string.toast_legacy_config_version_mismatch, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Toast.makeText(context, R.string.toast_legacy_config_read_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (requestCode == REQUEST_CODE_PICK_FRAMEGEN_DLL && resultCode == RESULT_OK) {
                val dllUri = data?.data
                if (dllUri != null) {
                    val ctx = requireContext()
                    Log.i("Framegen", "picked Lossless.dll uri=$dllUri flags=${data.flags}")
                    Toast.makeText(ctx, R.string.toast_framegen_lossless_dll_copying, Toast.LENGTH_SHORT).show()

                    thread(name = "FramegenLosslessDllProbe") {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                        val resultText = try {
                            // takePersistableUriPermission 只接受 READ/WRITE 两个权限位（0x1 / 0x2），
                            // 不能把 FLAG_GRANT_PERSISTABLE_URI_PERMISSION 本身传进去——否则抛 IllegalArgumentException。
                            try {
                                val readWriteFlags = data.flags and
                                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                val grantFlags = if (readWriteFlags != 0) readWriteFlags
                                                 else Intent.FLAG_GRANT_READ_URI_PERMISSION
                                ctx.contentResolver.takePersistableUriPermission(dllUri, grantFlags)
                            } catch (e: Throwable) {
                                // 某些文档提供器不支持持久授权 / Intent 里没带 read flag；
                                // 这一步只是为了下次冷启动还能读，失败不该阻塞本次复制。
                                Log.w("Framegen", "takePersistableUriPermission skipped: ${e.message}")
                            }

                            val stagedFile = stageFramegenLosslessDll(dllUri)
                            Log.i(
                                "Framegen",
                                "staged Lossless.dll path=${stagedFile.absolutePath} size=${stagedFile.length()}"
                            )
                            prefs.edit {
                                putString(FramegenSettings.PREF_LOSSLESS_DLL_STAGED_PATH, stagedFile.absolutePath)
                            }

                            com.limelight.framegen.FramegenInterceptor().probeLosslessDll(stagedFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e("Framegen", "pick/probe Lossless.dll failed", e)
                            getString(
                                R.string.toast_framegen_lossless_dll_pick_failed,
                                e.message ?: e.javaClass.simpleName
                            )
                        }

                        activity?.runOnUiThread {
                            if (isAdded) {
                                updateFramegenDllPreferenceSummary()
                                refreshDeveloperFeatureGateState()
                            }
                            Toast.makeText(
                                ctx,
                                if (resultText.startsWith("Lossless.dll 自检结果:")) {
                                    resultText
                                } else {
                                    getString(R.string.toast_framegen_lossless_dll_probe_result, resultText)
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            // 处理本地图片选择
            if (requestCode == LocalImagePickerPreference.PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
                val pickerPreference = LocalImagePickerPreference.instance
                pickerPreference?.handleImagePickerResult(data)
            }
        }

        private fun setupFramegenPreferences() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            DeveloperUnlockSettings.migrateLegacyPrefs(prefs)
            FramegenSettings.migrateLegacyCustomScale(prefs, getSelectedStreamWidth(prefs))
            setupDeveloperUnlockPreference()
            setupFramegenSelfTestPreference()
            setupFramegenLosslessDllPreference()
            setupFramegenEnabledPreference()
            setupFramegenAdaptivePreference()
            setupFramegenQualityPreference()
            refreshDeveloperFeatureGateState()
            updateFramegenDllPreferenceSummary()
        }

        private fun getSelectedStreamWidth(prefs: SharedPreferences): Int {
            val resolution = prefs.getString(
                PreferenceConfiguration.RESOLUTION_PREF_STRING,
                PreferenceConfiguration.DEFAULT_RESOLUTION
            ) ?: PreferenceConfiguration.DEFAULT_RESOLUTION
            return resolution.substringBefore('x').toIntOrNull()
                ?: PreferenceConfiguration.DEFAULT_RESOLUTION.substringBefore('x').toInt()
        }

        private fun setupDeveloperUnlockPreference() {
            findPreference<Preference>(DeveloperUnlockSettings.PREF_ENTRY)?.setOnPreferenceClickListener {
                showDeveloperUnlockDialog()
                true
            }
        }

        private fun setupFramegenSelfTestPreference() {
            findPreference<Preference>("pref_framegen_selftest")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                val result = if (com.limelight.framegen.FramegenInterceptor.isAvailable()) {
                    com.limelight.framegen.FramegenInterceptor().selfTest()
                } else {
                    ctx.getString(R.string.toast_framegen_unavailable)
                }
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.toast_framegen_selftest_result, result),
                    Toast.LENGTH_LONG
                ).show()
                true
            }
        }

        private fun setupFramegenLosslessDllPreference() {
            findPreference<Preference>("pref_framegen_pick_lossless_dll")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                if (!com.limelight.framegen.FramegenInterceptor.isAvailable()) {
                    Toast.makeText(ctx, R.string.toast_framegen_unavailable, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceClickListener true
                }

                appDialogBuilder(ctx)
                    .setTitle(R.string.title_framegen_pick_lossless_dll)
                    .setMessage(R.string.message_framegen_lossless_dll_source)
                    .setPositiveButton(R.string.action_framegen_select_lossless_dll) { _, _ ->
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_CODE_PICK_FRAMEGEN_DLL)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showStyled()
                true
            }
        }

        private fun setupFramegenEnabledPreference() {
            findPreference<CheckBoxPreference>(FramegenSettings.PREF_ENABLED)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue == true) {
                        val ctx = requireContext()
                        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                        if (!DeveloperUnlockSettings.isUnlocked(prefs)) {
                            showDeveloperUnlockDialog()
                            return@OnPreferenceChangeListener false
                        }
                        if (!FramegenSettings.isLosslessDllReady(prefs)) {
                            Toast.makeText(ctx, R.string.toast_framegen_need_lossless_dll, Toast.LENGTH_LONG).show()
                            updateFramegenDllPreferenceSummary()
                            return@OnPreferenceChangeListener false
                        }
                        if (!com.limelight.framegen.FramegenInterceptor.isAvailable()) {
                            Toast.makeText(ctx, R.string.toast_framegen_unavailable, Toast.LENGTH_LONG).show()
                            return@OnPreferenceChangeListener false
                        }
                        Toast.makeText(ctx, R.string.toast_framegen_enabled_warning, Toast.LENGTH_LONG).show()
                    }
                    true
                }
        }

        private fun setupFramegenAdaptivePreference() {
            findPreference<CheckBoxPreference>(FramegenSettings.PREF_ADAPTIVE_ENABLED)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue == true) {
                        val ctx = requireContext()
                        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                        if (!DeveloperUnlockSettings.isUnlocked(prefs)) {
                            showDeveloperUnlockDialog()
                            return@OnPreferenceChangeListener false
                        }
                        if (!FramegenSettings.isLosslessDllReady(prefs)) {
                            Toast.makeText(ctx, R.string.toast_framegen_need_lossless_dll, Toast.LENGTH_LONG).show()
                            updateFramegenDllPreferenceSummary()
                            return@OnPreferenceChangeListener false
                        }
                        if (!com.limelight.framegen.FramegenInterceptor.isAvailable()) {
                            Toast.makeText(ctx, R.string.toast_framegen_unavailable, Toast.LENGTH_LONG).show()
                            return@OnPreferenceChangeListener false
                        }
                    }
                    true
                }
        }

        private fun setupFramegenQualityPreference() {
            findPreference<ListPreference>(FramegenSettings.PREF_QUALITY_PRESET)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateFramegenQualityVisibility(newValue as? String)
                    true
                }
            updateFramegenQualityVisibility(
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(FramegenSettings.PREF_QUALITY_PRESET, null)
            )
        }

        /**
         * 麦克风"音量增益及其平衡"：
         * "音量增益"与"音量平衡"两个子功能互斥，开启一个时自动关闭另一个
         */
        private fun setupMicVolumeProcessingPreferences() {
            val gainPref = findPreference<CheckBoxPreference>("checkbox_mic_gain")
            val balancePref = findPreference<CheckBoxPreference>("checkbox_mic_balance")

            // Imported or individually synced settings can briefly contain both flags.
            // Gain has the same precedence as the runtime processor.
            if (gainPref?.isChecked == true && balancePref?.isChecked == true) {
                balancePref.isChecked = false
            }

            gainPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    balancePref?.setChecked(false)
                }
                true
            }

            balancePref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    gainPref?.setChecked(false)
                }
                true
            }
        }

        private fun refreshDeveloperFeatureGateState() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val unlocked = DeveloperUnlockSettings.isUnlocked(prefs)
            val dllReady = FramegenSettings.isLosslessDllReady(prefs)

            findPreference<Preference>(DeveloperUnlockSettings.PREF_ENTRY)?.summary =
                getString(
                    if (unlocked) {
                        R.string.summary_developer_unlock_unlocked
                    } else {
                        R.string.summary_developer_unlock_locked
                    }
                )

            findPreference<CheckBoxPreference>(FramegenSettings.PREF_ENABLED)?.let { pref ->
                pref.isEnabled = unlocked && dllReady
                if ((!unlocked || !dllReady) && pref.isChecked) {
                    pref.isChecked = false
                    prefs.edit { putBoolean(FramegenSettings.PREF_ENABLED, false) }
                }
                pref.summary = getString(
                    when {
                        !unlocked -> R.string.summary_framegen_enabled_locked
                        !dllReady -> R.string.summary_framegen_enabled_needs_dll
                        else -> R.string.summary_framegen_enabled
                    }
                )
            }

            updateFramegenAdaptivePreferenceState(prefs, unlocked, dllReady)
        }

        private fun updateFramegenAdaptivePreferenceState(
            prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext()),
            unlocked: Boolean = DeveloperUnlockSettings.isUnlocked(prefs),
            dllReady: Boolean = FramegenSettings.isLosslessDllReady(prefs)
        ) {
            val showAdaptive = unlocked && dllReady
            findPreference<CheckBoxPreference>(FramegenSettings.PREF_ADAPTIVE_ENABLED)?.let { pref ->
                pref.isVisible = showAdaptive
                pref.isEnabled = showAdaptive
            }
        }

        private fun updateFramegenQualityVisibility(selectedPreset: String?) {
            val showCustomWidth = selectedPreset == FramegenSettings.QUALITY_CUSTOM
            findPreference<Preference>(FramegenSettings.PREF_INTERNAL_WIDTH)?.isVisible = showCustomWidth
            findPreference<Preference>(FramegenSettings.PREF_SLOW_THRESHOLD_MS)?.isVisible = showCustomWidth
            findPreference<Preference>(FramegenSettings.PREF_PRESENT_REAL_FIRST)?.isVisible = showCustomWidth
        }

        private fun showDeveloperUnlockDialog() {
            val ctx = requireContext()
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val alreadyUnlocked = DeveloperUnlockSettings.isUnlocked(prefs)
            if (!GitHubStarVerifier.isConfigured()) {
                appDialogBuilder(ctx)
                    .setTitle(R.string.title_developer_unlock)
                    .setMessage(R.string.message_developer_oauth_unconfigured)
                    .setPositiveButton(R.string.action_developer_open_project) { _, _ ->
                        openDeveloperProjectPage()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showStyled()
                return
            }

            if (!alreadyUnlocked) {
                val pendingDeviceCode = GitHubDeviceAuthorization.loadPendingDeviceCode(ctx.applicationContext)
                if (pendingDeviceCode?.scope == GitHubStarVerifier.OAuthScope.STAR_VERIFICATION) {
                    developerPendingDeviceCode = pendingDeviceCode
                    showDeveloperDeviceCodeDialog(pendingDeviceCode)
                    pollDeveloperPendingDeviceCode(showPendingToast = false, enforceThrottle = true)
                    return
                }
            }

            appDialogBuilder(ctx)
                .setTitle(R.string.title_developer_unlock)
                .setMessage(
                    if (alreadyUnlocked) {
                        R.string.message_developer_unlock_done
                    } else {
                        R.string.message_developer_unlock_required
                    }
                )
                .setPositiveButton(R.string.action_developer_verify_star) { _, _ ->
                    startDeveloperUnlockVerification()
                }
                .setNeutralButton(R.string.action_developer_open_project) { _, _ ->
                    openDeveloperProjectPage()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showStyled()
        }

        private fun startDeveloperUnlockVerification(
            scope: GitHubStarVerifier.OAuthScope = GitHubStarVerifier.OAuthScope.STAR_VERIFICATION
        ) {
            val ctx = requireContext().applicationContext
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            if (developerUnlockVerificationRunning) {
                Toast.makeText(ctx, R.string.toast_developer_verification_running, Toast.LENGTH_LONG).show()
                return
            }
            if (!GitHubStarVerifier.isConfigured()) {
                Toast.makeText(ctx, R.string.toast_developer_oauth_unconfigured, Toast.LENGTH_LONG).show()
                return
            }

            val savedToken = prefs.getString(DeveloperUnlockSettings.PREF_ACCESS_TOKEN, null)
            val savedTokenCanBeUsed = !savedToken.isNullOrBlank() &&
                    DeveloperUnlockSettings.hasAccessTokenScope(prefs, scope)
            if (!savedTokenCanBeUsed) {
                val pendingDeviceCode = GitHubDeviceAuthorization.loadPendingDeviceCode(ctx)
                if (pendingDeviceCode?.scope == scope) {
                    developerPendingDeviceCode = pendingDeviceCode
                    showDeveloperDeviceCodeDialog(pendingDeviceCode)
                    pollDeveloperPendingDeviceCode(showPendingToast = false, enforceThrottle = true)
                    return
                }
            }

            developerUnlockVerificationRunning = true
            Toast.makeText(ctx, R.string.toast_developer_verification_started, Toast.LENGTH_LONG).show()
            thread(name = "DeveloperGitHubStarVerify") {
                try {
                    if (savedTokenCanBeUsed) {
                        completeDeveloperUnlockVerification(
                            ctx = ctx,
                            accessToken = savedToken,
                            starCheck = GitHubStarVerifier.checkStar(savedToken),
                            scope = scope
                        )
                        return@thread
                    }

                    val deviceCode = GitHubStarVerifier.requestDeviceCode(scope)
                    developerPendingDeviceCode = deviceCode
                    GitHubDeviceAuthorization.savePendingDeviceCode(ctx, deviceCode)
                    Log.i(
                        "DeveloperUnlock",
                        "GitHub star device code requested: userCode=${deviceCode.userCode}, " +
                            "interval=${deviceCode.intervalSeconds}s, expires=${deviceCode.expiresInSeconds}s"
                    )
                    activity?.runOnUiThread {
                        if (isAdded) {
                            showDeveloperDeviceCodeDialog(deviceCode)
                        }
                    }
                    developerUnlockVerificationRunning = false
                } catch (e: Exception) {
                    Log.e("DeveloperUnlock", "GitHub star verification failed", e)
                    failDeveloperUnlockVerification(ctx, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        private fun resumeDeveloperUnlockVerificationIfPending() {
            pollDeveloperPendingDeviceCode(showPendingToast = false, enforceThrottle = true)
        }

        private fun pollDeveloperPendingDeviceCode(showPendingToast: Boolean, enforceThrottle: Boolean) {
            val ctx = requireContext().applicationContext
            val deviceCode = developerPendingDeviceCode ?: GitHubDeviceAuthorization.loadPendingDeviceCode(ctx)
            if (deviceCode == null) {
                if (showPendingToast) {
                    Toast.makeText(ctx, R.string.toast_developer_verification_expired, Toast.LENGTH_LONG).show()
                }
                return
            }
            developerPendingDeviceCode = deviceCode
            developerUnlockVerificationRunning = true
            if (developerForegroundPollRunning) {
                if (showPendingToast) {
                    Toast.makeText(ctx, R.string.toast_developer_verification_running, Toast.LENGTH_LONG).show()
                }
                return
            }

            val nowMs = System.currentTimeMillis()
            if (enforceThrottle && nowMs - developerLastForegroundPollMs < 1500L) {
                return
            }
            developerLastForegroundPollMs = nowMs
            developerForegroundPollRunning = true

            thread(name = "DeveloperGitHubStarVerifyResume") {
                try {
                    Log.i("DeveloperUnlock", "GitHub star verification foreground poll, manual=$showPendingToast")
                    when (val poll = GitHubStarVerifier.pollAccessToken(deviceCode)) {
                        is GitHubStarVerifier.TokenPollResult.Authorized -> {
                            Log.i("DeveloperUnlock", "GitHub star device flow authorized from foreground poll")
                            val starCheck = GitHubStarVerifier.checkStar(poll.accessToken)
                            runIfDeveloperAttemptActive(deviceCode) {
                                completeDeveloperUnlockVerification(
                                    ctx = ctx,
                                    accessToken = poll.accessToken,
                                    starCheck = starCheck,
                                    scope = deviceCode.scope
                                )
                            }
                        }
                        GitHubStarVerifier.TokenPollResult.Pending -> {
                            Log.i("DeveloperUnlock", "GitHub star verification still pending")
                            if (showPendingToast) {
                                showDeveloperUnlockToastIfActive(deviceCode)
                            }
                        }
                        is GitHubStarVerifier.TokenPollResult.SlowDown -> {
                            Log.i("DeveloperUnlock", "GitHub star foreground poll slowed down to ${poll.intervalSeconds}s")
                            if (showPendingToast) {
                                showDeveloperUnlockToastIfActive(deviceCode)
                            }
                        }
                        is GitHubStarVerifier.TokenPollResult.Failed -> {
                            runIfDeveloperAttemptActive(deviceCode) {
                                failDeveloperUnlockVerification(ctx, poll.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DeveloperUnlock", "GitHub star foreground verification failed", e)
                    runIfDeveloperAttemptActive(deviceCode) {
                        failDeveloperUnlockVerification(ctx, e.message ?: e.javaClass.simpleName)
                    }
                } finally {
                    activity?.runOnUiThread {
                        developerForegroundPollRunning = false
                        if (developerPendingDeviceCode == deviceCode) {
                            developerUnlockVerificationRunning = false
                        }
                    }
                }
            }
        }

        private fun runIfDeveloperAttemptActive(
            deviceCode: GitHubStarVerifier.DeviceCode,
            action: () -> Unit
        ) {
            activity?.runOnUiThread {
                if (isAdded && developerPendingDeviceCode == deviceCode) {
                    action()
                }
            }
        }

        private fun showDeveloperUnlockToastIfActive(deviceCode: GitHubStarVerifier.DeviceCode) {
            runIfDeveloperAttemptActive(deviceCode) {
                Toast.makeText(
                    requireContext(),
                    R.string.toast_developer_authorization_pending,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun clearDeveloperPendingDeviceCode(ctx: Context) {
            developerPendingDeviceCode = null
            GitHubDeviceAuthorization.clearPendingDeviceCode(ctx)
        }

        private fun showDeveloperDeviceCodeDialog(deviceCode: GitHubStarVerifier.DeviceCode) {
            developerDeviceCodeDialog?.dismiss()
            GitHubDeviceAuthorization.copyDeviceCodeToClipboard(requireContext(), deviceCode)
            val dialog = appDialogBuilder()
                .setTitle(
                    if (deviceCode.scope == GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH) {
                        R.string.title_crown_store_github_authorization
                    } else {
                        R.string.title_developer_unlock
                    }
                )
                .setMessage(
                    getString(
                        if (deviceCode.scope == GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH) {
                            R.string.message_crown_store_device_code
                        } else {
                            R.string.message_developer_device_code
                        },
                        deviceCode.userCode,
                        deviceCode.verificationUri
                    )
                )
                .setPositiveButton(R.string.action_developer_open_authorization, null)
                .setNeutralButton(R.string.action_developer_check_authorization, null)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    developerUnlockVerificationRunning = false
                    clearDeveloperPendingDeviceCode(requireContext().applicationContext)
                }
                .setOnCancelListener {
                    developerUnlockVerificationRunning = false
                    clearDeveloperPendingDeviceCode(requireContext().applicationContext)
                }
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    GitHubDeviceAuthorization.copyDeviceCodeToClipboard(
                        requireContext(),
                        deviceCode,
                        showToast = false
                    )
                    openDeveloperUrl(GitHubDeviceAuthorization.authorizationUrl(deviceCode))
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    pollDeveloperPendingDeviceCode(showPendingToast = true, enforceThrottle = false)
                }
            }
            dialog.setOnDismissListener {
                if (developerDeviceCodeDialog === dialog) {
                    developerDeviceCodeDialog = null
                }
            }
            developerDeviceCodeDialog = dialog
            showStyledDialog(dialog)
        }

        private fun completeDeveloperUnlockVerification(
            ctx: Context,
            accessToken: String,
            starCheck: GitHubStarVerifier.StarCheck,
            scope: GitHubStarVerifier.OAuthScope
        ) {
            val dialogToDismiss = developerDeviceCodeDialog
            developerUnlockVerificationRunning = false
            clearDeveloperPendingDeviceCode(ctx)
            GitHubDeviceAuthorization.saveAuthorizedAccount(ctx, accessToken, starCheck, scope)
            Log.i(
                "DeveloperUnlock",
                "GitHub star verification completed: starred=${starCheck.starred}, login=${starCheck.login ?: "unknown"}"
            )
            activity?.runOnUiThread {
                if (!isAdded) {
                    return@runOnUiThread
                }
                if (developerDeviceCodeDialog === dialogToDismiss) {
                    dialogToDismiss?.dismiss()
                }
                refreshDeveloperFeatureGateState()

                if (scope == GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH) {
                    Toast.makeText(requireContext(), R.string.toast_crown_store_github_connected, Toast.LENGTH_LONG).show()
                } else if (starCheck.starred) {
                    Toast.makeText(requireContext(), R.string.toast_developer_unlocked, Toast.LENGTH_LONG).show()
                } else {
                    appDialogBuilder()
                        .setTitle(R.string.title_developer_unlock)
                        .setMessage(R.string.message_developer_star_not_found)
                        .setPositiveButton(R.string.action_developer_open_project) { _, _ ->
                            openDeveloperProjectPage()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showStyled()
                }
            }
        }

        private fun failDeveloperUnlockVerification(ctx: Context, message: String) {
            developerUnlockVerificationRunning = false
            clearDeveloperPendingDeviceCode(ctx)
            Log.w("DeveloperUnlock", "GitHub star verification failed: $message")
            activity?.runOnUiThread {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_developer_verification_failed, message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        private fun openDeveloperProjectPage() {
            openDeveloperUrl(DeveloperUnlockSettings.GITHUB_REPO_URL)
        }

        private fun openDeveloperUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_developer_open_project_failed, e.message ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun updateFramegenDllPreferenceSummary() {
            val pref = findPreference<Preference>("pref_framegen_pick_lossless_dll") ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val stagedPath = prefs.getString(FramegenSettings.PREF_LOSSLESS_DLL_STAGED_PATH, null)
            pref.summary = if (!stagedPath.isNullOrBlank() && File(stagedPath).exists()) {
                getString(R.string.summary_framegen_pick_lossless_dll_selected, stagedPath)
            } else {
                getString(R.string.summary_framegen_pick_lossless_dll)
            }
        }

        @Throws(IOException::class)
        private fun stageFramegenLosslessDll(sourceUri: android.net.Uri): File {
            val ctx = requireContext()
            val targetDir = File(ctx.filesDir, "framegen").apply { mkdirs() }
            if (!targetDir.exists()) {
                throw IOException("unable to create framegen dir")
            }

            val targetFile = File(targetDir, "Lossless.dll")
            var copied = false
            var lastError: Exception? = null
            repeat(5) { attempt ->
                try {
                    ctx.contentResolver.openInputStream(sourceUri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("unable to open selected dll")

                    if (targetFile.exists() && targetFile.length() > 0L) {
                        copied = true
                        return@repeat
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w("Framegen", "stage Lossless.dll attempt ${attempt + 1}/5 failed", e)
                }

                if (!copied && attempt < 4) {
                    Thread.sleep(120)
                }
            }

            if (!copied) {
                throw IOException(lastError?.message ?: "unable to open selected dll")
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                throw IOException("staged dll is empty")
            }

            return targetFile
        }
    }

    /**
     * 计算设置页背景图允许的解码尺寸（RGB_565，每像素 2 字节）：
     * - 预算取 min(maxMemory/12, freeMemory*0.4)，更贴近运行期实际可用堆
     * - 仍超出预算则按平方根缩小到目标像素数
     * - 返回 null 表示当前堆压力过大、放弃加载背景图（避免 OOM）
     */
    private fun computeBackgroundDecodeSize(): Pair<Int, Int>? {
        val rt = Runtime.getRuntime()
        val freeBytes = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        // 极端低内存：直接放弃，避免 Glide native decode 第一步就 OOM
        if (freeBytes < 8L * 1024 * 1024) return null
        val dispW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val dispH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val needPixels = dispW.toLong() * dispH
        val budgetBytes = minOf(rt.maxMemory() / 12L, (freeBytes * 4L) / 10L)
        val maxPixels = budgetBytes / 2L  // RGB_565 每像素 2 字节
        if (needPixels <= maxPixels) {
            return dispW to dispH
        }
        val scale = sqrt(maxPixels.toDouble() / needPixels.toDouble())
        val w = (dispW * scale).toInt().coerceAtLeast(1)
        val h = (dispH * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    private fun loadBackgroundImage() {
        val imageView = findViewById<ImageView>(R.id.settingsBackgroundImage)
        val resolved = BackgroundSource.resolveCurrentTarget(
                this,
                resources.configuration.orientation
        )
        val target = resolved.target

        if (target == null) {
            Glide.with(this).clear(imageView)
            imageView.setImageDrawable(null)
            return
        }

        // 解码尺寸根据当前可用堆按比例约束（详见 computeBackgroundDecodeSize）：
        // - 4K 电视 + 大堆设备保持原分辨率
        // - 低堆设备（典型场景：xiaomi MiTV4 SDK 23 maxMemory ≈ 100 MB）
        //   自动缩到安全尺寸
        // - 极端低剩余堆直接放弃背景图（保守不渲染，避免 OOM）
        val size = computeBackgroundDecodeSize() ?: return
        val (width, height) = size

        // 模糊 + theme-aware 蒙版，单次解码完成（合并到一个 Glide pipeline）
        // 强制 RGB_565：每像素 2 字节，整张图内存减半，模糊背景视觉无损
        val filterColor = if (isNightMode()) {
            Color.argb(120, 0, 0, 0)
        } else {
            Color.argb(96, 255, 255, 255)
        }
        val transformations = MultiTransformation<Bitmap>(
                BlurTransformation(2, 3),
                ColorFilterTransformation(filterColor)
        )
        val options = RequestOptions()
                .override(width, height)
                .format(DecodeFormat.PREFER_RGB_565)
                .transform(transformations)
                .signature(ObjectKey(resolved.cacheKey))
                .diskCacheStrategy(DiskCacheStrategy.ALL)

        // 候选 URL（含原始与所有代理变体）。Glide 缓存键以 URL 为基础，
        // 因此可能上次走代理 A 命中、原始 URL 在缓存中并不存在；这里逐个尝试，
        // 任一变体在缓存中就立即贴图，体验等同本地资源。
        if (!target.startsWith("http")) {
            val localFile = File(target)
            if (!localFile.exists()) {
                imageView.setImageDrawable(null)
                return
            }
            Glide.with(this)
                    .load(localFile)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade(400))
                    .into(imageView)
            return
        }

        // Reuse the active home-screen source. Proxy variants remain fallbacks
        // for remote sources so this page keeps its existing network resilience.
        val candidates = mutableListOf<String>().apply {
            add(target)
            try { addAll(UpdateManager.buildProxiedUrls(target)) } catch (_: Exception) {}
        }.distinct()

        tryCachedThenNetwork(imageView, options, candidates, 0)
    }

    /**
     * 依次对候选 URL 做 onlyRetrieveFromCache 同步缓存查询：
     *   - 命中：直接贴图，无线程切换、无渐入，体验秒开
     *   - 全部未命中：转入网络下载路径，下载完成后带 crossFade 渐入
     */
    private fun tryCachedThenNetwork(
            imageView: ImageView,
            options: RequestOptions,
            candidates: List<String>,
            index: Int
    ) {
        if (isDestroyed || isFinishing) return
        if (index >= candidates.size) {
            loadBackgroundImageFromNetwork(imageView, options, candidates)
            return
        }
        Glide.with(this)
                .load(candidates[index])
                .apply(options.clone().onlyRetrieveFromCache(true))
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        imageView.setImageDrawable(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        tryCachedThenNetwork(imageView, options, candidates, index + 1)
                    }
                })
    }

    private fun loadBackgroundImageFromNetwork(
            imageView: ImageView,
            options: RequestOptions,
            preBuiltCandidates: List<String>?
    ) {
        Thread {
            // 代理列表可能在调用前还未就绪，需要刷新一次
            UpdateManager.ensureProxyListUpdated(this)
            val candidates = preBuiltCandidates.orEmpty()
            for (url in candidates) {
                try {
                    if (isDestroyed || isFinishing) return@Thread
                    // 后台同步预热：把图解码到 Glide 缓存中（包含 blur+mask 变换）；
                    // 失败则尝试下一条代理，不会污染 UI
                    val ready = Glide.with(applicationContext)
                            .asDrawable()
                            .load(url)
                            .apply(options)
                            .submit()
                            .get()
                    if (ready != null) {
                        runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            // 用同一份缓存渲染，加 400ms 渐入避免突兀 pop-in
                            Glide.with(this@StreamSettings)
                                    .load(url)
                                    .apply(options)
                                    .transition(DrawableTransitionOptions.withCrossFade(400))
                                    .into(imageView)
                        }
                        return@Thread
                    }
                } catch (e: Exception) {
                    // Try next proxy
                }
            }
        }.start()
    }
}
