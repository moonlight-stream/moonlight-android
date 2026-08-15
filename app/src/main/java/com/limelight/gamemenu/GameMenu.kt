package com.limelight.gamemenu

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.limelight.CustomKeyData
import com.limelight.CustomKeyRepository
import com.limelight.Game
import com.limelight.LimeLog
import com.limelight.QuickActionRegistry
import com.limelight.R
import com.limelight.StreamActionExecutor
import com.limelight.binding.input.GameInputDevice
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.element.ElementController
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.http.NvApp
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.UiDismissKeyHandler
import com.limelight.utils.AppActionSheet
import com.limelight.utils.KeyCodeMapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayDeque

/** Int → Short 快捷转换 */
private fun Int.s(): Short = this.toShort()

internal fun mapGameMenuConfirmKeyCode(keyCode: Int): Int {
    return if (keyCode == KeyEvent.KEYCODE_BUTTON_A) KeyEvent.KEYCODE_DPAD_CENTER else keyCode
}

private fun mapGameMenuConfirmKeyEvent(event: KeyEvent): KeyEvent {
    val mappedKeyCode = mapGameMenuConfirmKeyCode(event.keyCode)
    return if (mappedKeyCode != event.keyCode) {
        KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            mappedKeyCode,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
    } else {
        event
    }
}

/**
 * 提供游戏流媒体进行中的选项菜单
 * 在游戏活动中按返回键时显示
 */
class GameMenu(
    private val game: Game,
    private val app: NvApp,
    private val conn: NvConnection,
    private val device: GameInputDevice?,
    private val onDismiss: (GameMenu) -> Unit = {}
) {
    // 当前激活的对话框（如果有）
    private var activeDialog: ComponentDialog? = null
    private var composeUiState: MutableState<GameMenuComposeUiState>? = null
    // 标志：上一次运行的选项是否打开了子菜单（由 showSubMenu 设置）
    private var lastActionOpenedSubmenu = false
    // 菜单历史栈，用于二级/多级菜单的回退
    private val menuStack: ArrayDeque<MenuPage> = ArrayDeque()
    private val handler = Handler(Looper.getMainLooper())
    private val actionExecutor = StreamActionExecutor(game, { conn }, handler)
    private val bitrateCardController = BitrateCardController(game, conn)
    private val audioHapticsCardController = AudioHapticsCardController(game)
    private val gyroCardController = GyroCardController(game)
    private val renderingProfile = GameMenuRenderingProfile.from(game)
    init {
        showMenu()
    }

    fun dismiss() {
        activeDialog?.dismiss()
    }

    fun isShowing(): Boolean {
        return activeDialog?.isShowing == true
    }

    fun dispatchControllerKeyEvent(event: KeyEvent): Boolean {
        val dialog = activeDialog ?: return false
        if (!dialog.isShowing) return false
        if (UiDismissKeyHandler.handle(event.action, event.keyCode) {
                if (!navigateBack()) dialog.cancel()
            }
        ) {
            return true
        }
        dialog.dispatchKeyEvent(event)
        return true
    }

    /**
     * 菜单选项类
     */
    class MenuOption(
        val label: String,
        val isWithGameFocus: Boolean,
        val runnable: Runnable?,
        val iconKey: String?,
        val isShowIcon: Boolean,
        val isKeepDialog: Boolean,
        val subtitle: String? = null,
        val isCrownControl: Boolean = false,
        val showChevron: Boolean = false,
        val inlineControl: InlineControl? = null
    ) {
        constructor(label: String, runnable: Runnable?) :
                this(label, false, runnable, null, true, false)

        constructor(label: String, withGameFocus: Boolean, runnable: Runnable?) :
                this(label, withGameFocus, runnable, null, true, false)

        constructor(label: String, withGameFocus: Boolean, runnable: Runnable?, iconKey: String?) :
                this(label, withGameFocus, runnable, iconKey, true, false)

        constructor(label: String, withGameFocus: Boolean, runnable: Runnable?, iconKey: String?, showIcon: Boolean) :
                this(label, withGameFocus, runnable, iconKey, showIcon, false)
    }

    sealed interface InlineControl {
        data class Toggle(
            val checked: Boolean,
            val toggleAction: Runnable? = null
        ) : InlineControl
        data class Segmented(val segments: List<SegmentOption>) : InlineControl
    }

    data class SegmentOption(
        val label: String,
        val selected: Boolean,
        val runnable: Runnable,
        val subtitle: String? = null
    )

    /**
     * 菜单状态，用于回退
     */
    private data class MenuPage(val title: String, val options: List<MenuOption>)

    /**
     * 获取字符串资源
     */
    private fun getString(id: Int): String = game.resources.getString(id)

    /**
     * 断开连接并退出
     */
    private fun disconnectAndQuit() {
        actionExecutor.disconnectAndQuit()
    }

    /**
     * 发送键盘按键序列
     */
    private fun sendKeys(keys: ShortArray) {
        actionExecutor.sendKeys(keys)
    }

    /**
     * 在游戏获得焦点时运行任务
     */
    private fun runWithGameFocus(runnable: Runnable) {
        if (game.isFinishing) return

        if (!game.hasWindowFocus()) {
            handler.postDelayed({ runWithGameFocus(runnable) }, TEST_GAME_FOCUS_DELAY)
            return
        }

        runnable.run()
    }

    /**
     * 执行菜单选项
     */
    private fun run(option: MenuOption?) {
        if (option?.runnable == null) return

        if (option.isWithGameFocus) {
            runWithGameFocus(option.runnable)
        } else {
            option.runnable.run()
        }
    }

    /**
     * 显示触控模式菜单
     */
    private fun showTouchModeMenu() {
        val isTouchscreenTrackpad = game.prefConfig.touchscreenTrackpad
        val touchModeOptionsList = buildTouchModeSegments().mapTo(mutableListOf()) { segment ->
            MenuOption(
                label = if (segment.selected) {
                    game.getString(R.string.game_menu_current_selection, segment.label)
                } else {
                    segment.label
                },
                isWithGameFocus = false,
                runnable = segment.runnable,
                iconKey = null,
                isShowIcon = false,
                isKeepDialog = false,
                subtitle = segment.subtitle
            )
        }

        //触控板双击功能
        if (isTouchscreenTrackpad) {
            touchModeOptionsList.add(
                MenuOption(
                    getString(R.string.game_menu_trackpad_double_click_drag),
                    false,
                    {
                        game.prefConfig.enableDoubleClickDrag =
                            !game.prefConfig.enableDoubleClickDrag
                        Toast.makeText(
                            game,
                            if (game.prefConfig.enableDoubleClickDrag) getString(R.string.toast_double_click_drag_enabled) else getString(
                                R.string.toast_double_click_drag_disabled
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    null,
                    false,
                    false,
                    if (game.prefConfig.enableDoubleClickDrag) {
                        getString(R.string.game_menu_option_enabled)
                    } else {
                        getString(R.string.game_menu_option_disabled)
                    }
                )
            )
        }

        //触控板仅移动
        if (isTouchscreenTrackpad) {
            touchModeOptionsList.add(
                MenuOption(
                    getString(R.string.game_menu_trackpad_tap_behavior),
                    false,
                    {
                        game.toggleMouseMoveOnly()
                        Toast.makeText(
                            game,
                            if (game.isMouseMoveOnlyEnabled) getString(R.string.layout_page_device_text_mmo_true_text) else getString(
                                R.string.layout_page_device_text_mmo_false_text
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    null,
                    false,
                    false,
                    if (game.isMouseMoveOnlyEnabled) {
                        getString(R.string.layout_page_device_text_mmo_true_text)
                    } else {
                        getString(R.string.layout_page_device_text_mmo_false_text)
                    }
                )
            )
        }

        showSubMenu(getString(R.string.game_menu_switch_touch_mode), touchModeOptionsList.toTypedArray())
    }

    private fun buildTouchModeSegments(compactLabels: Boolean = false): List<SegmentOption> {
        val isEnhancedTouch = game.prefConfig.enableEnhancedTouch
        val isTrackpad = game.prefConfig.touchscreenTrackpad
        val isNativePointer = game.prefConfig.enableNativeMousePointer

        return listOf(
            SegmentOption(
                label = getString(if (compactLabels) R.string.game_menu_touch_mode_enhanced_short else R.string.game_menu_touch_mode_enhanced),
                selected = isEnhancedTouch && !isTrackpad && !isNativePointer,
                runnable = Runnable {
                    game.prefConfig.enableEnhancedTouch = true
                    game.prefConfig.enableNativeMousePointer = false
                    game.enableNativeMousePointer(false)
                    game.setTouchMode(false)
                    updateEnhancedTouchSetting(true)
                    updateTouchModeSetting(false)
                },
                subtitle = getString(R.string.game_menu_touch_mode_enhanced_summary)
            ),
            SegmentOption(
                label = getString(if (compactLabels) R.string.game_menu_touch_mode_classic_short else R.string.game_menu_touch_mode_classic),
                selected = !isEnhancedTouch && !isTrackpad && !isNativePointer,
                runnable = Runnable {
                    game.prefConfig.enableEnhancedTouch = false
                    game.prefConfig.enableNativeMousePointer = false
                    game.enableNativeMousePointer(false)
                    game.setTouchMode(false)
                    updateEnhancedTouchSetting(false)
                    updateTouchModeSetting(false)
                },
                subtitle = getString(R.string.game_menu_touch_mode_classic_summary)
            ),
            SegmentOption(
                label = getString(if (compactLabels) R.string.game_menu_touch_mode_trackpad_short else R.string.game_menu_touch_mode_trackpad),
                selected = isTrackpad && !isNativePointer,
                runnable = Runnable {
                    game.prefConfig.enableNativeMousePointer = false
                    game.enableNativeMousePointer(false)
                    game.setTouchMode(true)
                    updateTouchModeSetting(true)
                },
                subtitle = getString(R.string.game_menu_touch_mode_trackpad_summary)
            ),
            SegmentOption(
                label = getString(if (compactLabels) R.string.game_menu_touch_mode_native_mouse_short else R.string.game_menu_touch_mode_native_mouse),
                selected = isNativePointer,
                runnable = Runnable {
                    game.prefConfig.enableNativeMousePointer = true
                    game.prefConfig.enableEnhancedTouch = false
                    game.setTouchMode(false)
                    game.enableNativeMousePointer(true)
                    updateEnhancedTouchSetting(false)
                    updateTouchModeSetting(false)
                },
                subtitle = getString(R.string.game_menu_touch_mode_native_mouse_summary)
            )
        )
    }

    private fun toggleRemoteMouse() {
        sendKeys(shortArrayOf(
            KeyboardTranslator.VK_LCONTROL.s(),
            KeyboardTranslator.VK_MENU.s(),
            KeyboardTranslator.VK_LSHIFT.s(),
            KeyboardTranslator.VK_N.s()
        ))
        Toast.makeText(game, getString(R.string.toast_remote_mouse_toast), Toast.LENGTH_SHORT).show()
    }

    private fun updateTouchModeSetting(isTrackpadMode: Boolean) {
        val controllerManager = game.controllerManager ?: run {
            LimeLog.warning("ControllerManager is null, cannot update touch mode setting")
            return
        }

        val contentValues = ContentValues()
        val currentConfigId = controllerManager.pageConfigController?.currentConfigId ?: return

        contentValues.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, isTrackpadMode.toString())
        controllerManager.superConfigDatabaseHelper?.updateConfig(currentConfigId, contentValues)
    }

    private fun updateEnhancedTouchSetting(isEnabled: Boolean) {
        val controllerManager = game.controllerManager ?: run {
            LimeLog.warning("ControllerManager is null, cannot update touch mode setting")
            return
        }

        val contentValues = ContentValues()
        val currentConfigId = controllerManager.pageConfigController?.currentConfigId ?: return

        contentValues.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, isEnabled.toString())
        controllerManager.superConfigDatabaseHelper?.updateConfig(currentConfigId, contentValues)
    }

    /**
     * 切换王冠功能并即时刷新菜单内容
     */
    private fun toggleCrownFeature() {
        setCrownFeatureEnabled(!game.isCrownFeatureEnabled)
        rebuildAndReplaceMenu()
    }

    private fun getCrownToggleText(): String {
        return if (game.isCrownFeatureEnabled)
            getString(R.string.crown_switch_to_normal)
        else
            getString(R.string.crown_switch_to_crown)
    }

    private fun rebuildAndReplaceMenu() {
        activeDialog ?: return

        menuStack.clear()

        val normalOptions = mutableListOf<MenuOption>()
        buildNormalMenuOptions(normalOptions)
        composeUiState?.let { state ->
            state.value = state.value.copy(
                title = getString(R.string.game_menu_title),
                options = normalOptions,
                deviceQuickOptions = device?.getGameMenuQuickOptions().orEmpty(),
                crownToggleText = getCrownToggleText(),
                isSubmenu = false
            )
        }
    }

    /**
     * 显示"王冠功能"的二级菜单
     */
    private fun showCrownFunctionMenu() {
        if (!game.isCrownFeatureEnabled) return
        showSubMenu(
            getString(R.string.game_menu_crown_function_title),
            buildEnabledCrownFunctionOptions(game.controllerManager)
        )
    }

    private fun createCrownOption(
        label: String,
        iconKey: String,
        subtitle: String,
        action: () -> Unit
    ): MenuOption {
        return MenuOption(
            label = label,
            isWithGameFocus = false,
            runnable = Runnable { action() },
            iconKey = iconKey,
            isShowIcon = true,
            isKeepDialog = false,
            subtitle = subtitle,
            isCrownControl = true
        )
    }

    private fun setCrownFeatureEnabled(enabled: Boolean) {
        game.isCrownFeatureEnabled = enabled
        val message = if (game.isCrownFeatureEnabled) {
            getString(R.string.crown_mode_crown)
        } else {
            getString(R.string.crown_mode_normal)
        }
        Toast.makeText(game, message, Toast.LENGTH_SHORT).show()
    }

    private fun buildEnabledCrownFunctionOptions(controllerManager: com.limelight.binding.input.advance_setting.ControllerManager?): Array<MenuOption> {
        return arrayOf(
            createCrownOption(
                getString(R.string.game_menu_toggle_elements_visibility),
                "crown_visibility",
                getString(R.string.crown_control_visibility_subtitle)
            ) {
                game.toggleVirtualControllerVisibility()
            },
            createCrownOption(
                getString(R.string.game_menu_toggle_touch),
                "crown_touch",
                getString(R.string.crown_control_touch_subtitle)
            ) {
                controllerManager?.touchController?.enableTouch(mouse_enable_switch)
                Toast.makeText(game,
                    if (mouse_enable_switch) getString(R.string.toast_touch_enabled) else getString(R.string.toast_touch_disabled),
                    Toast.LENGTH_SHORT).show()
                mouse_enable_switch = !mouse_enable_switch
            },
            createCrownOption(
                getString(R.string.game_menu_configure_settings),
                "crown_profiles",
                getString(R.string.crown_control_profiles_subtitle)
            ) {
                controllerManager?.let { cm ->
                    game.setcurrentBackKeyMenu(Game.BackKeyMenuMode.NO_MENU_LOCKED)
                    cm.pageConfigController?.open()
                }
            },
            createCrownOption(
                getString(R.string.game_menu_edit_mode),
                "crown_layout",
                getString(R.string.crown_control_layout_subtitle)
            ) {
                controllerManager?.let { cm ->
                    game.toggleBackKeyMenuType()
                    game.setcurrentBackKeyMenu(Game.BackKeyMenuMode.NO_MENU)
                    cm.elementController?.changeMode(ElementController.Mode.Edit)
                    cm.elementController?.open()
                    cm.superPagesController?.let { spc ->
                        if (spc.pageNow === spc.pageNull) {
                            spc.returnOperation()
                        }
                    }
                }
            },
            createCrownOption(
                getString(R.string.game_menu_configure_crown_function),
                "crown_back_key",
                getString(R.string.crown_control_back_key_subtitle)
            ) {
                game.toggleBackKeyMenuType()
            }
        )
    }

    /**
     * 本地测试震动
     */
    private fun testLocalRumbleAll() {
        try {
            val ch = game.controllerHandler

            val on: Short = 0xFFFF.toShort()
            val off: Short = 0
            for (n in 0.toShort()..3.toShort()) {
                ch.handleTestRumble(n.toShort(), on, on)
            }

            handler.postDelayed({
                try {
                    for (n in 0.toShort()..3.toShort()) {
                        ch.handleTestRumble(n.toShort(), off, off)
                    }
                } catch (_: Exception) {}
            }, 1000)

            Toast.makeText(game, getString(R.string.toast_vibration_test_sent), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(game, game.getString(R.string.toast_vibration_test_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示分辨率选择菜单
     */
    private fun showResolutionMenu() {
        val options = mutableListOf<MenuOption>()
        val currentResStr = "${game.prefConfig.width}x${game.prefConfig.height}"

        // 预设分辨率
        for (res in PreferenceConfiguration.RESOLUTIONS) {
            val label = if (res == currentResStr) {
                game.getString(R.string.game_menu_resolution_current, res)
            } else {
                res
            }
            options.add(MenuOption(label, false, { changeResolution(res) }, null, false))
        }

        // 自定义分辨率
        val customPrefs = game.getSharedPreferences("custom_resolutions", Context.MODE_PRIVATE)
        val customResolutions = customPrefs.getStringSet("custom_resolutions", null)

        if (!customResolutions.isNullOrEmpty()) {
            val sortedCustom = customResolutions.sortedWith(Comparator { s1, s2 ->
                val parts1 = s1.split("x")
                val parts2 = s2.split("x")
                if (parts1.size != 2 || parts2.size != 2) return@Comparator s1.compareTo(s2)
                try {
                    val w1 = parts1[0].toInt(); val h1 = parts1[1].toInt()
                    val w2 = parts2[0].toInt(); val h2 = parts2[1].toInt()
                    if (w1 != w2) w1.compareTo(w2) else h1.compareTo(h2)
                } catch (_: NumberFormatException) {
                    s1.compareTo(s2)
                }
            })

            for (res in sortedCustom) {
                if (PreferenceConfiguration.RESOLUTIONS.contains(res)) continue

                val label = if (res == currentResStr) {
                    game.getString(R.string.game_menu_resolution_custom_current, res)
                } else {
                    game.getString(R.string.game_menu_resolution_custom, res)
                }

                options.add(MenuOption(label, false, { changeResolution(res) }, null, false))
            }
        }

        showSubMenu(getString(R.string.game_menu_change_resolution), options.toTypedArray())
    }

    private fun changeResolution(resString: String) {
        @Suppress("DEPRECATION")
        android.preference.PreferenceManager.getDefaultSharedPreferences(game)
            .edit {
                putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, resString)
            }

        Toast.makeText(
            game,
            game.getString(R.string.game_menu_resolution_restarting, resString),
            Toast.LENGTH_SHORT
        ).show()

        game.changeResolution()
        activeDialog?.dismiss()
    }

    /**
     * 显示菜单对话框
     */
    private fun showMenuDialog(title: String, normalOptions: Array<MenuOption>, superOptions: Array<MenuOption>) {
        lateinit var dialog: ComponentDialog

        val state = mutableStateOf(
            GameMenuComposeUiState(
                title = title,
                options = normalOptions.toList(),
                superOptions = superOptions.toList(),
                appName = getAppNameDisplay(),
                crownToggleText = getCrownToggleText(),
                deviceQuickOptions = device?.getGameMenuQuickOptions().orEmpty(),
                quickActions = buildComposeQuickActions(),
                visibleCards = readVisibleCards(),
                bitrate = bitrateCardController.snapshot(),
                audioHaptics = audioHapticsCardController.snapshot(),
                gyro = gyroCardController.snapshot(),
                customKeys = getSavedCustomKeys()
            )
        )
        composeUiState = state
        bitrateCardController.start { bitrate ->
            composeUiState?.let { it.value = it.value.copy(bitrate = bitrate) }
        }
        audioHapticsCardController.start { audioHaptics ->
            composeUiState?.let { it.value = it.value.copy(audioHaptics = audioHaptics) }
        }
        gyroCardController.start { gyro ->
            composeUiState?.let { it.value = it.value.copy(gyro = gyro) }
        }

        val callbacks = GameMenuCallbacks(
            iconForOption = ::getIconForMenuOption,
            onBack = { navigateBack() },
            onCrownToggle = ::toggleCrownFeature,
            onOptionClick = { handleComposeOptionClick(it, dialog) },
            onInlineToggle = ::handleInlineToggle,
            onSegmentClick = ::handleInlineSegmentClick,
            onEmptySuperCommandClick = ::showSuperCommandHint,
            onQuickAction = ::runComposeQuickAction,
            onToggleQuickEdit = ::toggleComposeQuickEdit,
            onAddQuickAction = ::showQuickButtonEditor,
            onRemoveQuickAction = ::removeComposeQuickAction,
            onMoveQuickAction = ::moveComposeQuickAction,
            onEditCards = ::showCardEditorDialog,
            onBitrateProgress = bitrateCardController::previewProgress,
            onBitrateApply = bitrateCardController::applySelectedBitrate,
            onBitrateHapticMode = bitrateCardController::cycleHapticMode,
            onAudioHapticsEnabled = audioHapticsCardController::setEnabled,
            onAudioHapticsStrength = audioHapticsCardController::previewStrength,
            onAudioHapticsStrengthFinished = audioHapticsCardController::persistStrength,
            onAudioHapticsMode = audioHapticsCardController::setMode,
            onAudioHapticsScene = audioHapticsCardController::setScene,
            onAudioHapticsReset = audioHapticsCardController::resetTuning,
            onGyroEnabled = gyroCardController::setEnabled,
            onGyroMouseMode = gyroCardController::setMouseMode,
            onGyroActivationKey = gyroCardController::showActivationKeyDialog,
            onGyroSensitivity = gyroCardController::previewSensitivity,
            onGyroSensitivityFinished = gyroCardController::persistSensitivity,
            onGyroInvertX = gyroCardController::setInvertX,
            onGyroInvertY = gyroCardController::setInvertY,
            onCustomKey = { sendKeys(it.keys) }
        )

        val composeView = ComposeView(game).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                GameMenuScreen(
                    state = state.value,
                    callbacks = callbacks,
                    useFabricTexture = renderingProfile.useFabricTexture,
                    requestControllerFocus = device != null
                )
            }
        }
        dialog = ComponentDialog(game, R.style.GameMenuDialogStyle).apply {
            setContentView(composeView)
            setCanceledOnTouchOutside(true)
        }
        this.activeDialog = dialog

        setupDialogProperties(dialog)

        // 返回键监听器
        dialog.setOnKeyListener { _, keyCode, event ->
            if (UiDismissKeyHandler.handle(event.action, keyCode) {
                if (!navigateBack()) dialog.cancel()
            }) {
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                dialog.dispatchKeyEvent(mapGameMenuConfirmKeyEvent(event))
                return@setOnKeyListener true
            }
            false
        }

        // 关闭时清理状态
        dialog.setOnDismissListener {
            if (this.activeDialog == dialog) this.activeDialog = null
            this.composeUiState = null
            bitrateCardController.dispose()
            audioHapticsCardController.dispose()
            gyroCardController.dispose()
            menuStack.clear()
            onDismiss(this)
        }

        dialog.show()
        applyDialogSize(dialog)
    }

    private fun handleComposeOptionClick(option: MenuOption, dialog: ComponentDialog) {
        lastActionOpenedSubmenu = false

        // Focus-dependent actions must wait until the dialog has released the game window.
        // Dismissing first also preserves the interaction order of the legacy menu.
        if (option.isWithGameFocus && !option.isKeepDialog) {
            dialog.dismiss()
            option.runnable?.let(::runWithGameFocus)
            return
        }

        run(option)
        val shouldKeep = option.isKeepDialog || lastActionOpenedSubmenu
        if (!shouldKeep) dialog.dismiss()
        if (option.inlineControl is InlineControl.Toggle &&
            option.inlineControl.toggleAction == null &&
            dialog.isShowing
        ) {
            rebuildAndReplaceMenu()
        }
        lastActionOpenedSubmenu = false
    }

    private fun handleInlineSegmentClick(segment: SegmentOption) {
        if (segment.selected) return
        segment.runnable.run()
        rebuildAndReplaceMenu()
    }

    private fun handleInlineToggle(toggle: InlineControl.Toggle) {
        val action = toggle.toggleAction ?: return
        action.run()
        rebuildAndReplaceMenu()
    }

    private fun showSuperCommandHint() {
        Toast.makeText(
            game,
            getString(R.string.layout_game_menu_super_empty_text_fac9d),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getAppNameDisplay(): String {
        return try {
            val version = conn.serverVersion?.takeIf { it.isNotBlank() }
            if (version != null) {
                game.getString(R.string.game_menu_server_version, app.appName, version)
            } else {
                app.appName
            }
        } catch (_: Exception) {
            "Moonlight V+"
        }
    }

    private fun readVisibleCards(): GameMenuVisibleCards {
        return GameMenuVisibleCards(
            bitrate = game.prefConfig.showBitrateCard,
            audioHaptics = game.prefConfig.showAudioHapticsCard,
            gyro = game.prefConfig.showGyroCard,
            shortcuts = game.prefConfig.showQuickKeyCard
        )
    }

    private fun showCardEditorDialog() {
        GameMenuCardVisibilityEditor.show(game, game.prefConfig) {
            composeUiState?.let { state ->
                state.value = state.value.copy(
                    visibleCards = readVisibleCards(),
                    customKeys = getSavedCustomKeys()
                )
            }
        }
    }

    // --- 简单的按键数据模型 ---
    /**
     * 从存储或默认资源中获取解析好的按键数据列表
     */
    private fun getSavedCustomKeys(): List<CustomKeyData> {
        return CustomKeyRepository.load(game, showErrorToast = true)
    }

    private fun refreshComposeCustomKeys() {
        composeUiState?.let { state ->
            state.value = state.value.copy(customKeys = getSavedCustomKeys())
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * game.resources.displayMetrics.density).toInt()

    /** 解析 action id → Triple(label, iconRes, iconText)，无效返回 null。 */
    private fun resolveAction(id: String): Triple<String, Int, String?>? {
        val action = QuickActionRegistry.getBuiltin(id)
        return when {
            action != null -> {
                val label = if (action.labelRes != 0) getString(action.labelRes) else action.label
                Triple(label, action.iconRes, action.iconText)
            }
            id.startsWith("custom_") -> Triple(id.substring("custom_".length), 0, null)
            else -> null
        }
    }

    private fun buildComposeQuickActions(): List<GameMenuQuickAction> {
        return QuickActionRegistry.loadConfig(game).mapNotNull { id ->
            val (label, iconRes, iconText) = resolveAction(id) ?: return@mapNotNull null
            GameMenuQuickAction(
                id = id,
                label = label,
                iconRes = if (id == "toggle_mic" && !game.prefConfig.enableMic) {
                    QuickActionRegistry.getBuiltin(id)?.iconDisabledRes ?: iconRes
                } else {
                    iconRes
                },
                iconText = iconText,
                enabled = id != "toggle_mic" || game.prefConfig.enableMic
            )
        }
    }

    private fun refreshComposeQuickActions(editMode: Boolean? = null) {
        composeUiState?.let { state ->
            state.value = state.value.copy(
                quickActions = buildComposeQuickActions(),
                quickEditMode = editMode ?: state.value.quickEditMode
            )
        }
    }

    private fun runComposeQuickAction(id: String) {
        if (id == "toggle_mic" && !game.prefConfig.enableMic) {
            Toast.makeText(game, getString(R.string.toast_enable_mic_redirect), Toast.LENGTH_SHORT).show()
            return
        }

        if (QuickActionRegistry.getBuiltin(id)?.requiresGameFocus == true) {
            activeDialog?.dismiss()
            runWithGameFocus(Runnable { actionExecutor.execute(id) })
            return
        }

        actionExecutor.execute(id)
    }

    private fun toggleComposeQuickEdit() {
        val state = composeUiState ?: return
        state.value = state.value.copy(quickEditMode = !state.value.quickEditMode)
    }

    private fun removeComposeQuickAction(id: String) {
        val ids = QuickActionRegistry.loadConfig(game).toMutableList()
        if (ids.size <= 1 || !ids.remove(id)) return
        QuickActionRegistry.saveConfig(game, ids)
        refreshComposeQuickActions()
    }

    private fun moveComposeQuickAction(id: String, targetId: String) {
        val ids = QuickActionRegistry.loadConfig(game).toMutableList()
        val from = ids.indexOf(id)
        val target = ids.indexOf(targetId)
        if (from < 0 || target < 0 || from == target) return
        ids.add(target.coerceAtMost(ids.size), ids.removeAt(from))
        composeUiState?.let { state ->
            val actionsById = state.value.quickActions.associateBy(GameMenuQuickAction::id)
            state.value = state.value.copy(
                quickActions = ids.mapNotNull(actionsById::get)
            )
        }
        QuickActionRegistry.saveConfig(game, ids)
    }

    /**
     * 快捷按钮配置编辑器
     */
    private fun showQuickButtonEditor() {
        val currentIds = QuickActionRegistry.loadConfig(game)
        val customKeys = getSavedCustomKeys()
        val customKeyPairs = customKeys.map { arrayOf(it.name, "") }

        val allActions = QuickActionRegistry.getAllActions(customKeyPairs)

        val allIds = allActions.keys.toTypedArray()
        val allLabels = allIds.map { id ->
            val a = allActions[id]!!
            if (a.labelRes != 0) getString(a.labelRes) else a.label
        }.toTypedArray()
        AppActionSheet.showMultiSelect(
            context = game,
            title = getString(R.string.quick_button_editor_title),
            actions = allLabels.mapIndexed { index, label ->
                AppActionSheet.Action(index, label, checked = allIds[index] in currentIds)
            },
            confirmLabel = getString(R.string.game_menu_ok).trim(),
            cancelLabel = getString(R.string.game_menu_cancel).trim(),
            resetLabel = getString(R.string.quick_button_reset_default).trim(),
            minimumSelectionCount = 1,
            onConfirm = { selectedPositions ->
                val selectedIds = selectedPositions.mapTo(linkedSetOf()) { allIds[it] }
                val newIds = currentIds.filterTo(mutableListOf()) { it in selectedIds }
                allIds.filterTo(newIds) { it in selectedIds && it !in newIds }
                if (newIds.isEmpty()) newIds.add("quit")
                QuickActionRegistry.saveConfig(game, newIds)
                refreshComposeQuickActions()
            },
            onReset = {
                QuickActionRegistry.saveConfig(game, QuickActionRegistry.defaultIds(game))
                refreshComposeQuickActions()
            }
        )
    }

    private fun currentMenuPage(): MenuPage? {
        return composeUiState?.value?.let { MenuPage(it.title, it.options) }
    }

    private fun showMenuPage(page: MenuPage, pushCurrent: Boolean = false) {
        val state = composeUiState ?: return
        if (pushCurrent) currentMenuPage()?.let(menuStack::push)
        state.value = state.value.copy(
            title = page.title,
            options = page.options,
            isSubmenu = menuStack.isNotEmpty()
        )
    }

    private fun navigateBack(): Boolean {
        if (menuStack.isEmpty()) return false
        showMenuPage(menuStack.pop())
        return true
    }

    /**
     * 在当前打开的 dialog 中显示一个子菜单
     */
    private fun showSubMenu(title: String, subOptions: Array<MenuOption>) {
        val dialog = activeDialog
        if (dialog != null && dialog.isShowing) {
            lastActionOpenedSubmenu = true
            showMenuPage(MenuPage(title, subOptions.toList()), pushCurrent = true)
        } else {
            showMenuDialog(title, subOptions, emptyArray())
        }
    }

    private fun setupDialogProperties(dialog: ComponentDialog) {
        dialog.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.alpha = renderingProfile.windowAlpha
            layoutParams.dimAmount = DIALOG_DIM_AMOUNT
            layoutParams.width = resolveDialogWidth()
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.gravity = android.view.Gravity.BOTTOM
            window.attributes = layoutParams
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            window.setWindowAnimations(renderingProfile.dialogAnimationStyle)
        }
    }

    private fun applyDialogSize(dialog: ComponentDialog) {
        dialog.window?.setLayout(
            resolveDialogWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun resolveDialogWidth(): Int {
        val widthFraction = if (
            game.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            DIALOG_LANDSCAPE_WIDTH_FRACTION
        } else {
            DIALOG_PORTRAIT_WIDTH_FRACTION
        }
        val windowWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            game.windowManager.currentWindowMetrics.bounds.width()
        } else {
            game.window.decorView.width
        }.takeIf { it > 0 } ?: game.resources.displayMetrics.widthPixels

        return (windowWidth * widthFraction)
            .toInt()
            .coerceAtLeast(1)
    }

    /**
     * 显示特殊按键菜单
     */
    private fun showSpecialKeysMenu() {
        val options = mutableListOf<MenuOption>()

        val hasKeys = loadAndAddAllKeys(options)

        options.add(MenuOption(getString(R.string.game_menu_add_custom_key), false,
            { showAddCustomKeyDialog() }, null, false))

        if (hasKeys) {
            options.add(MenuOption(getString(R.string.game_menu_delete_custom_key), false,
                { showDeleteKeysDialog() }, null, false))
        }

        options.add(MenuOption(getString(R.string.game_menu_cancel), false, null, null, false))

        showSubMenu(getString(R.string.game_menu_send_keys), options.toTypedArray())
    }

    private fun loadAndAddAllKeys(options: MutableList<MenuOption>): Boolean {
        val loadedKeys = getSavedCustomKeys()
        if (loadedKeys.isEmpty()) return false

        for (keyData in loadedKeys) {
            options.add(MenuOption(keyData.name, false, { sendKeys(keyData.keys) }, null, false))
        }
        return true
    }

    private fun readRawResourceAsString(resourceId: Int): String {
        try {
            game.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val builder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        builder.append(line)
                    }
                    return builder.toString()
                }
            }
        } catch (e: IOException) {
            LimeLog.warning("Failed to read raw resource file: $resourceId: $e")
            return ""
        }
    }

    private fun saveCustomKey(name: String, keysString: String) {
        val preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
        val value = preferences.getString(KEY_NAME, "{\"data\":[]}") ?: "{\"data\":[]}"

        try {
            val keyParts = keysString.split(",")
            val keyCodesArray = JSONArray()
            for (part in keyParts) {
                val trimmedPart = part.trim()
                if (!trimmedPart.startsWith("0x")) {
                    Toast.makeText(game, R.string.toast_key_code_format_error, Toast.LENGTH_LONG).show()
                    return
                }
                keyCodesArray.put(trimmedPart)
            }

            val root = JSONObject(value)
            val dataArray = root.getJSONArray("data")

            val newKeyEntry = JSONObject()
            newKeyEntry.put("name", name)
            newKeyEntry.put("data", keyCodesArray)
            dataArray.put(newKeyEntry)

            preferences.edit { putString(KEY_NAME, root.toString()) }

            Toast.makeText(game, game.getString(R.string.toast_custom_key_saved, name), Toast.LENGTH_SHORT).show()
            refreshComposeCustomKeys()
        } catch (e: Exception) {
            LimeLog.warning("Exception while saving custom key${e.message}")
            Toast.makeText(game, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddCustomKeyDialog() {
        val builder = AlertDialog.Builder(game, R.style.VirtualKeyboardDialogStyle)

        val dialogView = LayoutInflater.from(game).inflate(R.layout.dialog_add_custom_key, null)
        builder.setView(dialogView)

        val dialogContent = dialogView.findViewById<LinearLayout>(R.id.dialog_content)
        val nameInput = dialogView.findViewById<EditText>(R.id.edit_text_key_name)
        val keysDisplay = dialogView.findViewById<TextView>(R.id.text_view_key_codes)
        val clearButton = dialogView.findViewById<Button>(R.id.button_clear_keys)
        val closeButton = dialogView.findViewById<Button>(R.id.button_close_dialog)
        val saveButton = dialogView.findViewById<Button>(R.id.button_save_key)

        val dialog = builder.create()
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            UiDismissKeyHandler.handle(event.action, keyCode, dialog::cancel)
        }

        closeButton?.setOnClickListener { dialog.dismiss() }

        // 点击背景关闭对话框
        if (dialogView is FrameLayout) {
            dialogView.setOnClickListener { dialog.dismiss() }

            // 防止内容区域的点击事件传播到背景
            val contentArea = dialogView.getChildAt(0) // ScrollView
            contentArea?.setOnClickListener { /* block propagation */ }
        }

        // 初始化 TextView 的数据存储 (tag) 和显示 (text)
        keysDisplay.tag = ""
        keysDisplay.text = ""
        keysDisplay.setHint(R.string.dialog_hint_key_codes)

        // 清空按钮
        clearButton?.setOnClickListener {
            keysDisplay.tag = ""
            keysDisplay.text = ""
        }

        // 递归设置键盘监听器
        setupCompactKeyboardListeners(dialogView.findViewById(R.id.keyboard_drawing), keysDisplay)

        // 保存按钮事件
        saveButton?.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val androidKeyCodesStr = keysDisplay.tag.toString()

            if (name.isEmpty() || androidKeyCodesStr.isEmpty()) {
                Toast.makeText(game, R.string.toast_name_and_codes_cannot_be_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val androidCodes = androidKeyCodesStr.split(",")
            val windowsCodesBuilder = StringBuilder()
            for (i in androidCodes.indices) {
                try {
                    val code = androidCodes[i].toInt()
                    val windowsCode = KeyCodeMapper.getWindowsKeyCode(code)
                        ?: throw NullPointerException()
                    windowsCodesBuilder.append(windowsCode)
                    if (i < androidCodes.size - 1) windowsCodesBuilder.append(",")
                } catch (_: Exception) {
                    Toast.makeText(game, "error: invalid key code", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }
            saveCustomKey(name, windowsCodesBuilder.toString())
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogContent?.minimumHeight = game.resources.displayMetrics.heightPixels
    }

    private fun setupCompactKeyboardListeners(parent: ViewGroup?, keysDisplay: TextView) {
        if (parent == null) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) {
                setupCompactKeyboardListeners(child, keysDisplay)
            } else if (child is TextView && child.tag != null) {
                child.setOnClickListener { v ->
                    val androidKeyCode = v.tag.toString()
                    val currentTag = keysDisplay.tag.toString()

                    val newTag = if (currentTag.isEmpty()) androidKeyCode else "$currentTag,$androidKeyCode"
                    keysDisplay.tag = newTag

                    val currentText = keysDisplay.text.toString()
                    val displayName = KeyCodeMapper.getDisplayName(androidKeyCode.toInt())
                    val newText = if (currentText.isEmpty()) displayName else "$currentText + $displayName"
                    keysDisplay.text = newText
                }
            }
        }
    }

    private fun showDeleteKeysDialog() {
        val preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
        val value = preferences.getString(KEY_NAME, "")

        if (value.isNullOrEmpty()) {
            Toast.makeText(game, R.string.toast_no_custom_keys_to_delete, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val root = JSONObject(value)
            val dataArray = root.optJSONArray("data")

            if (dataArray == null || dataArray.length() == 0) {
                Toast.makeText(game, R.string.toast_no_custom_keys_to_delete, Toast.LENGTH_SHORT).show()
                return
            }

            val keyNames = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                keyNames.add(dataArray.getJSONObject(i).optString("name"))
            }
            AppActionSheet.showMultiSelect(
                context = game,
                title = getString(R.string.dialog_title_select_keys_to_delete),
                actions = keyNames.mapIndexed { index, name ->
                    AppActionSheet.Action(id = index, title = name, checked = false)
                },
                confirmLabel = getString(R.string.dialog_button_delete),
                cancelLabel = getString(R.string.dialog_button_cancel),
                minimumSelectionCount = 1,
                onConfirm = { selectedIds ->
                    try {
                        selectedIds.sortedDescending().forEach(dataArray::remove)
                        root.put("data", dataArray)
                        preferences.edit { putString(KEY_NAME, root.toString()) }
                        Toast.makeText(game, R.string.toast_selected_keys_deleted, Toast.LENGTH_SHORT).show()
                        refreshComposeCustomKeys()
                    } catch (e: Exception) {
                        LimeLog.warning("Exception while deleting keys${e.message}")
                        Toast.makeText(game, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            LimeLog.warning("Exception while loading key list${e.message}")
            Toast.makeText(game, R.string.toast_load_key_list_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示主菜单
     */
    private fun showMenu() {
        val normalOptions = mutableListOf<MenuOption>()
        val superOptions = mutableListOf<MenuOption>()

        buildNormalMenuOptions(normalOptions)
        buildSuperMenuOptions(superOptions)

        showMenuDialog(getString(R.string.game_menu_title), normalOptions.toTypedArray(), superOptions.toTypedArray())
    }

    /**
     * 构建普通菜单选项
     */
    private fun buildNormalMenuOptions(normalOptions: MutableList<MenuOption>) {
        normalOptions.add(MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
            { game.toggleKeyboard() }, "game_menu_toggle_keyboard", true))

        normalOptions.add(MenuOption(getString(R.string.game_menu_toggle_host_keyboard), true,
            { sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.s(), KeyboardTranslator.VK_LCONTROL.s(), KeyboardTranslator.VK_O.s())) },
            "game_menu_toggle_host_keyboard", true))

        normalOptions.add(MenuOption(
            label = getString(R.string.game_menu_control).trim(),
            isWithGameFocus = false,
            runnable = Runnable { showTouchModeMenu() },
            iconKey = "mouse_mode",
            isShowIcon = true,
            isKeepDialog = true,
            showChevron = true,
            inlineControl = InlineControl.Segmented(buildTouchModeSegments(compactLabels = true))
        ))

        normalOptions.add(MenuOption(
            label = getString(R.string.game_menu_toggle_remote_mouse),
            isWithGameFocus = false,
            runnable = Runnable { toggleRemoteMouse() },
            iconKey = "game_menu_mouse_emulation",
            isShowIcon = true,
            isKeepDialog = false,
            subtitle = getString(R.string.game_menu_toggle_remote_mouse_summary)
        ))

        normalOptions.add(MenuOption(
            label = getString(R.string.game_menu_enable_pan_zoom).trim(),
            isWithGameFocus = false,
            runnable = Runnable {
                Toast.makeText(game,
                    if (game.getisTouchOverrideEnabled()) getString(R.string.toast_pan_zoom_disabled) else getString(R.string.toast_pan_zoom_enabled),
                    Toast.LENGTH_SHORT).show()
                game.setisTouchOverrideEnabled(!game.getisTouchOverrideEnabled())
            },
            iconKey = "game_menu_mouse_emulation",
            isShowIcon = true,
            isKeepDialog = true,
            inlineControl = InlineControl.Toggle(game.getisTouchOverrideEnabled())
        ))

        // 王冠功能
        val crownEnabled = game.isCrownFeatureEnabled
        normalOptions.add(MenuOption(
            label = getString(R.string.game_menu_crown_function),
            isWithGameFocus = false,
            runnable = if (crownEnabled) Runnable { showCrownFunctionMenu() } else null,
            iconKey = "crown_function_menu",
            isShowIcon = true,
            isKeepDialog = true,
            showChevron = crownEnabled,
            inlineControl = InlineControl.Toggle(
                checked = crownEnabled,
                toggleAction = Runnable { setCrownFeatureEnabled(!game.isCrownFeatureEnabled) }
            )
        ))

        // 性能显示
        normalOptions.add(MenuOption(
            label = getString(R.string.game_menu_toggle_performance_overlay).trim(),
            isWithGameFocus = false,
            runnable = null,
            iconKey = "game_menu_toggle_performance_overlay",
            isShowIcon = true,
            isKeepDialog = true,
            inlineControl = InlineControl.Segmented(buildPerformanceOverlaySegments())
        ))

        normalOptions.add(MenuOption(
            getString(R.string.game_menu_change_resolution), false,
            { showResolutionMenu() }, "game_menu_change_resolution", isShowIcon = true, isKeepDialog = true,
            showChevron = true
        ))

        if (game.prefConfig.onscreenController) {
            normalOptions.add(MenuOption(
                label = getString(R.string.game_menu_toggle_virtual_controller).trim(),
                isWithGameFocus = false,
                runnable = Runnable { game.toggleVirtualController() },
                iconKey = "game_menu_toggle_virtual_controller",
                isShowIcon = true,
                isKeepDialog = true,
                inlineControl = InlineControl.Toggle(game.isVirtualControllerVisible())
            ))
        }

        normalOptions.add(MenuOption(getString(R.string.game_menu_send_keys),
            false, { showSpecialKeysMenu() }, "game_menu_send_keys", isShowIcon = true, isKeepDialog = true,
            showChevron = true
        ))

        normalOptions.add(MenuOption(getString(R.string.game_menu_disconnect), true,
            { game.disconnect() }, "game_menu_disconnect", true))

        normalOptions.add(MenuOption(getString(R.string.game_menu_disconnect_and_quit), true,
            { disconnectAndQuit() }, "game_menu_disconnect_and_quit", true))
    }

    private fun buildPerformanceOverlaySegments(): List<SegmentOption> {
        val currentMode = game.performanceOverlayMode
        return listOf(
            performanceOverlaySegment(
                label = getString(R.string.perf_overlay_hidden),
                mode = Game.PerformanceOverlayMode.HIDDEN,
                currentMode = currentMode
            ),
            performanceOverlaySegment(
                label = getString(R.string.perf_overlay_floating),
                mode = Game.PerformanceOverlayMode.FLOATING,
                currentMode = currentMode
            ),
            performanceOverlaySegment(
                label = getString(R.string.perf_overlay_locked),
                mode = Game.PerformanceOverlayMode.LOCKED,
                currentMode = currentMode
            )
        )
    }

    private fun performanceOverlaySegment(
        label: String,
        mode: Game.PerformanceOverlayMode,
        currentMode: Game.PerformanceOverlayMode
    ) = SegmentOption(
        label = label,
        selected = mode == currentMode,
        runnable = Runnable { game.setPerformanceOverlayMode(mode) }
    )

    /**
     * 构建超级菜单选项
     */
    private fun buildSuperMenuOptions(superOptions: MutableList<MenuOption>) {
        val cmdList: JsonArray? = app.cmdList
        if (cmdList != null) {
            for (i in 0 until cmdList.size()) {
                val cmd = cmdList[i].asJsonObject
                superOptions.add(MenuOption(cmd["name"].asString, true, {
                    try {
                        conn.sendSuperCmd(cmd["id"].asString)
                    } catch (e: Exception) {
                        Toast.makeText(game, game.getString(R.string.toast_super_command_error, e.message), Toast.LENGTH_SHORT).show()
                    }
                }, null, false))
            }
        }
    }

    companion object {
        private const val TEST_GAME_FOCUS_DELAY = 10L
        private const val DIALOG_DIM_AMOUNT = 0.0f
        private const val DIALOG_LANDSCAPE_WIDTH_FRACTION = 0.88f
        private const val DIALOG_PORTRAIT_WIDTH_FRACTION = 0.95f
        private const val PREF_NAME = "custom_special_keys"
        private const val KEY_NAME = "data"

        private var mouse_enable_switch = false

        private val ICON_MAP = mapOf(
            "game_menu_change_resolution" to R.drawable.ic_resolution_cute,
            "game_menu_toggle_keyboard" to R.drawable.ic_keyboard_cute,
            "game_menu_toggle_performance_overlay" to R.drawable.ic_performance_cute,
            "game_menu_toggle_virtual_controller" to R.drawable.ic_controller_cute,
            "game_menu_disconnect" to R.drawable.ic_disconnect_cute,
            "game_menu_send_keys" to R.drawable.ic_send_keys_cute,
            "game_menu_toggle_host_keyboard" to R.drawable.ic_host_keyboard,
            "game_menu_disconnect_and_quit" to R.drawable.ic_btn_quit,
            "game_menu_cancel" to R.drawable.ic_cancel_cute,
            "mouse_mode" to R.drawable.ic_mouse_cute,
            "game_menu_mouse_emulation" to R.drawable.ic_mouse_emulation_cute,
            "crown_function_menu" to R.drawable.ic_super_crown,
            "crown_visibility" to R.drawable.ic_ui_settings,
            "crown_touch" to R.drawable.ic_touch_settings,
            "crown_profiles" to R.drawable.ic_input_settings,
            "crown_layout" to R.drawable.ic_gamepad_settings,
            "crown_back_key" to R.drawable.ic_keyboard_cute,
            "game_menu_test_local_rumble" to R.drawable.ic_rumble_cute
        )

        fun getIconForMenuOption(iconKey: String?): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return ICON_MAP.getOrDefault(iconKey, R.drawable.ic_menu_item_default)
            }
            // Compose's painterResource() rejects negative resource IDs. Older
            // Android versions intentionally hide these vector menu icons.
            return 0
        }
    }
}
