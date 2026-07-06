package com.limelight.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch as AndroidSwitch
import androidx.activity.ComponentDialog
import androidx.core.content.ContextCompat
import com.easytier.jni.EasyTierManager
import com.limelight.LimeLog
import com.limelight.R
import android.graphics.Color as AndroidColor

import org.json.JSONArray
import org.json.JSONObject

/**
 * EasyTier功能控制器
 * 集中管理EasyTier的所有功能：配置、状态、UI对话框、服务控制
 */
class EasyTierController(
        private val activity: Activity,
        private val vpnCallback: VpnPermissionCallback
) {
    private var easyTierManager: EasyTierManager? = null
    private var currentDialog: Dialog? = null
    private val instanceName = "Default"

    private enum class EasyTierTab {
        STATUS,
        CONFIG
    }

    private data class EasyTierConfigUiState(
            val networkName: String = "",
            val networkSecret: String = "",
            val ipv4: String = "",
            val listeners: String = "",
            val peers: String = "",
            val useSmoltcp: Boolean = false,
            val latencyFirst: Boolean = false,
            val disableP2p: Boolean = false,
            val privateMode: Boolean = false,
            val disableIpv6: Boolean = false,
            val enableKcpProxy: Boolean = false,
            val disableKcpInput: Boolean = false,
            val enableQuicProxy: Boolean = false,
            val disableQuicInput: Boolean = false,
            val proxyForwardBySystem: Boolean = false,
            val disableEncryption: Boolean = false,
            val disableUdpHolePunching: Boolean = false,
            val disableSymHolePunching: Boolean = false
    )

    private data class EasyTierDialogUiState(
            val selectedTab: EasyTierTab = EasyTierTab.STATUS,
            val config: EasyTierConfigUiState = EasyTierConfigUiState(),
            val statusJson: String? = null,
            val advancedExpanded: Boolean = false
    ) {
        val isRunning: Boolean
            get() = !statusJson.isNullOrEmpty()
    }

    interface VpnPermissionCallback {
        fun requestVpnPermission()
    }

    init {
        initEasyTierManager()
    }

    // ==================== 初始化和生命周期 ====================

    private fun initEasyTierManager() {
        val config = getEasyTierConfig()

        if (easyTierManager != null && easyTierManager?.latestNetworkInfoJson != null) {
            easyTierManager?.stop()
        }
        LimeLog.info("使用的easytier配置为：\n$config")
        easyTierManager = EasyTierManager(activity, instanceName, config)
        LimeLog.info("$TAG: EasyTierManager initialized with instance: $instanceName")
    }

    fun onDestroy() {
        easyTierManager?.stop()
        if (currentDialog != null && currentDialog?.isShowing == true) {
            currentDialog?.dismiss()
        }
    }

    // ==================== 主要公共方法 ====================

    fun showControlDialog() {
        if (easyTierManager == null) {
            Toast.makeText(activity, R.string.easytier_manager_uninitialized, Toast.LENGTH_SHORT).show()
            return
        }

        createAndShowDialog()
    }

    fun handleVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            LimeLog.info("$TAG: VPN权限已获取，启动EasyTier Manager。")
            easyTierManager?.start()
            Toast.makeText(activity, R.string.easytier_starting, Toast.LENGTH_SHORT).show()
        } else {
            LimeLog.warning("$TAG: VPN权限被拒绝。")
            Toast.makeText(activity, R.string.easytier_vpn_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 对话框管理 ====================

    private fun createAndShowDialog() {
        val panelView = EasyTierPanelView(
                activity,
                EasyTierDialogUiState(
                        config = loadConfigurationState(),
                        statusJson = easyTierManager?.latestNetworkInfoJson
                )
        )

        currentDialog = ComponentDialog(activity, R.style.AppDialogStyle).apply {
            setContentView(panelView)
        }
        currentDialog?.show()
        currentDialog?.let { AppDialogStyler.applyCustomContent(it, activity) }
    }

    private inner class EasyTierPanelView(
            context: Context,
            initialState: EasyTierDialogUiState
    ) : LinearLayout(context) {
        private var state = initialState
        private val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        private val primaryTextColor = ContextCompat.getColor(context, R.color.app_dialog_text_primary)
        private val secondaryTextColor = ContextCompat.getColor(context, R.color.app_dialog_text_secondary)
        private val accentColor = ContextCompat.getColor(context, R.color.app_dialog_accent_color)
        private val accentSoft = ContextCompat.getColor(context, R.color.app_dialog_accent_soft)
        private val accentFocus = ContextCompat.getColor(context, R.color.app_dialog_accent_focus)
        private val panelFill = ContextCompat.getColor(context, R.color.app_dialog_surface)
        private val cardFill = ContextCompat.getColor(context, R.color.app_dialog_surface_elevated)
        private val softFill = ContextCompat.getColor(context, R.color.app_dialog_surface_pressed)
        private val focusedFill = ContextCompat.getColor(context, R.color.app_dialog_surface_focused)
        private val cardStroke = ContextCompat.getColor(context, R.color.app_dialog_outline)
        private val focusedStroke = ContextCompat.getColor(context, R.color.app_dialog_outline_strong)
        private val accentTextColor = AndroidColor.rgb(28, 29, 34)

        private val statusText = TextView(context)
        private val statusPill = TextView(context)
        private val statusTab = TextView(context)
        private val configTab = TextView(context)
        private val contentScroll = ScrollView(context)
        private val content = LinearLayout(context)
        private val toggleButton = TextView(context)

        init {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(panelFill, focusedStroke, dp(22))
            setPadding(
                    dp(if (isLandscape) 24 else 18),
                    dp(if (isLandscape) 18 else 20),
                    dp(if (isLandscape) 24 else 18),
                    dp(if (isLandscape) 14 else 16)
            )

            content.orientation = LinearLayout.VERTICAL
            contentScroll.apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isScrollbarFadingEnabled = false
                clipToPadding = false
                setPadding(0, 0, dp(4), dp(2))
                addView(content, LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }

            addView(createHeader(), sectionParams())
            addView(createTabs(), sectionParams(top = 14))
            addView(contentScroll, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ).apply {
                topMargin = dp(12)
            })
            addView(createFooter(), sectionParams(top = 12))
            refreshChrome()
            renderContent()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val displayMetrics = resources.displayMetrics
            val maxWidth = if (isLandscape) {
                minOf((displayMetrics.widthPixels * 0.68f).toInt(), dp(720))
            } else {
                minOf(displayMetrics.widthPixels - dp(64), dp(560))
            }.coerceAtLeast(dp(320))
            val maxHeight = if (isLandscape) {
                minOf(displayMetrics.heightPixels - dp(48), dp(520))
            } else {
                minOf((displayMetrics.heightPixels * 0.78f).toInt(), displayMetrics.heightPixels - dp(148))
            }.coerceAtLeast(dp(420))
            val parentWidth = View.MeasureSpec.getSize(widthMeasureSpec)
            val parentHeight = View.MeasureSpec.getSize(heightMeasureSpec)
            val measuredWidth = if (parentWidth > 0) minOf(parentWidth, maxWidth) else maxWidth
            val measuredHeight = if (parentHeight > 0) minOf(parentHeight, maxHeight) else maxHeight
            val constrainedWidth = View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY)
            val constrainedHeight = View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            super.onMeasure(constrainedWidth, constrainedHeight)
        }

        private fun createHeader(): View {
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            header.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.easytier_panel_title)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLandscape) 21f else 22f)
                    typeface = Typeface.DEFAULT_BOLD
                })
                statusText.setTextColor(secondaryTextColor)
                statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLandscape) 12f else 13f)
                statusText.setPadding(0, dp(4), 0, 0)
                addView(statusText)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            statusPill.gravity = Gravity.CENTER
            statusPill.includeFontPadding = false
            statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            statusPill.typeface = Typeface.DEFAULT_BOLD
            statusPill.minHeight = dp(28)
            statusPill.minWidth = dp(52)
            statusPill.setPadding(dp(10), 0, dp(10), 0)
            header.addView(statusPill, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(28)
            ).apply {
                marginStart = dp(12)
                topMargin = dp(4)
            })
            return header
        }

        private fun createTabs(): View {
            val tabRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBackground(softFill, cardStroke, dp(18))
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }

            configureTab(statusTab, EasyTierTab.STATUS, context.getString(R.string.easytier_tab_status))
            configureTab(configTab, EasyTierTab.CONFIG, context.getString(R.string.easytier_tab_config))
            tabRow.addView(statusTab, LinearLayout.LayoutParams(0, dp(38), 1f))
            tabRow.addView(configTab, LinearLayout.LayoutParams(0, dp(38), 1f))
            return tabRow
        }

        private fun configureTab(tab: TextView, target: EasyTierTab, title: String) {
            tab.text = title
            tab.gravity = Gravity.CENTER
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tab.typeface = Typeface.DEFAULT_BOLD
            tab.isClickable = true
            tab.isFocusable = true
            tab.setOnClickListener {
                state = state.copy(selectedTab = target)
                refreshChrome()
                renderContent()
                contentScroll.smoothScrollTo(0, 0)
            }
        }

        private fun createFooter(): View {
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            footer.addView(createButton(context.getString(R.string.dialog_button_close), false) {
                currentDialog?.dismiss()
            }, LinearLayout.LayoutParams(0, dp(44), 1f))

            footer.addView(createButton(context.getString(R.string.config_sync_action_export), false) {
                saveConfiguration(state.config, showToast = true)
                state = state.copy(statusJson = easyTierManager?.latestNetworkInfoJson)
                refreshChrome()
                renderContent()
            }, LinearLayout.LayoutParams(0, dp(44), 1.15f).apply {
                marginStart = dp(8)
            })

            footer.addView(toggleButton, LinearLayout.LayoutParams(0, dp(44), 1.15f).apply {
                marginStart = dp(8)
            })
            toggleButton.gravity = Gravity.CENTER
            toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            toggleButton.typeface = Typeface.DEFAULT_BOLD
            toggleButton.isClickable = true
            toggleButton.isFocusable = true
            toggleButton.setTextColor(accentTextColor)
            toggleButton.setOnClickListener {
                if (state.isRunning) {
                    Toast.makeText(activity, R.string.easytier_stopped, Toast.LENGTH_SHORT).show()
                    easyTierManager?.stop()
                    state = state.copy(statusJson = null)
                    currentDialog?.dismiss()
                } else {
                    saveConfiguration(state.config, showToast = false)
                    vpnCallback.requestVpnPermission()
                    currentDialog?.dismiss()
                }
            }
            return footer
        }

        private fun refreshChrome() {
            statusText.text = context.getString(
                    if (state.isRunning) R.string.easytier_status_running
                    else R.string.easytier_status_stopped
            )
            statusPill.text = context.getString(if (state.isRunning) R.string.easytier_pill_running else R.string.easytier_pill_idle)
            statusPill.setTextColor(primaryTextColor)
            statusPill.background = roundedBackground(
                    if (state.isRunning) accentSoft else softFill,
                    if (state.isRunning) accentFocus else cardStroke,
                    dp(14)
            )
            updateTab(statusTab, state.selectedTab == EasyTierTab.STATUS)
            updateTab(configTab, state.selectedTab == EasyTierTab.CONFIG)
            toggleButton.text = context.getString(if (state.isRunning) R.string.dialog_button_stop else R.string.dialog_button_start)
            toggleButton.background = roundedBackground(accentColor, accentFocus, dp(18))
        }

        private fun updateTab(tab: TextView, selected: Boolean) {
            tab.setTextColor(if (selected) accentTextColor else primaryTextColor)
            tab.background = if (selected) {
                roundedBackground(accentColor, accentFocus, dp(16))
            } else {
                roundedBackground(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT, dp(16))
            }
        }

        private fun renderContent() {
            content.removeAllViews()
            content.orientation = LinearLayout.VERTICAL
            if (state.selectedTab == EasyTierTab.STATUS) {
                renderStatus()
            } else {
                renderConfig()
            }
            contentScroll.post { contentScroll.scrollTo(0, 0) }
        }

        private fun renderStatus() {
            content.addView(createStatusToolbar {
                state = state.copy(statusJson = easyTierManager?.latestNetworkInfoJson)
                refreshChrome()
                renderContent()
                Toast.makeText(activity, R.string.easytier_status_refreshed, Toast.LENGTH_SHORT).show()
            }, sectionParams())

            val statusJson = state.statusJson
            if (statusJson.isNullOrEmpty()) {
                content.addView(createCard(LinearLayout.VERTICAL).apply {
                    addText(context.getString(R.string.easytier_service_not_running), primaryTextColor, 16f, true)
                    addText(context.getString(R.string.easytier_refresh_hint), secondaryTextColor, 13f, false, topPadding = dp(6))
                }, sectionParams(top = 10))
                return
            }

            val displayInfo = parseNetworkInfoForDialog(statusJson, instanceName)
            addSectionTitle(context.getString(R.string.easytier_local_info))
            content.addView(createCard(LinearLayout.VERTICAL).apply {
                addInfoRow(context.getString(R.string.easytier_hostname), displayInfo.hostname)
                addInfoRow(context.getString(R.string.easytier_virtual_ip), displayInfo.virtualIp)
                addInfoRow(context.getString(R.string.easytier_public_ip), displayInfo.publicIp)
                addInfoRow(context.getString(R.string.easytier_nat_type), displayInfo.natType)
            }, sectionParams(top = 6))

            addSectionTitle(context.getString(R.string.easytier_peers_count, displayInfo.finalPeerList.size))
            if (displayInfo.finalPeerList.isEmpty()) {
                content.addView(createCard(LinearLayout.VERTICAL).apply {
                    addText(context.getString(R.string.easytier_no_peers), secondaryTextColor, 13f, false)
                }, sectionParams(top = 6))
            } else {
                displayInfo.finalPeerList.forEach { peer ->
                    content.addView(createCard(LinearLayout.VERTICAL).apply {
                        addText(peerTitle(peer), primaryTextColor, 15f, true)
                        addInfoRow(context.getString(R.string.easytier_virtual_ip), peer.virtualIp, topPadding = dp(8))
                        addInfoRow(context.getString(R.string.easytier_nat_type), peer.natType)
                        addInfoRow(
                                context.getString(if (peer.isDirectConnection) R.string.easytier_physical_address else R.string.easytier_next_hop),
                                peer.connectionDetails
                        )
                        addInfoRow(context.getString(R.string.easytier_latency), peer.latency)
                        addInfoRow(context.getString(R.string.easytier_traffic), peer.traffic)
                    }, sectionParams(top = 6))
                }
            }
        }

        private fun renderConfig() {
            content.addView(createTextField(
                    label = context.getString(R.string.easytier_network_name_label),
                    value = state.config.networkName,
                    hint = context.getString(R.string.easytier_network_name_hint)
            ) { value -> state = state.copy(config = state.config.copy(networkName = value)) })

            content.addView(createTextField(
                    label = context.getString(R.string.easytier_network_secret_label),
                    value = state.config.networkSecret,
                    hint = "",
                    helper = if (state.config.networkSecret.isBlank()) context.getString(R.string.easytier_network_secret_empty_hint) else ""
            ) { value -> state = state.copy(config = state.config.copy(networkSecret = value)) }, sectionParams(top = 8))

            content.addView(createTextField(
                    label = context.getString(R.string.easytier_virtual_ipv4_label),
                    value = state.config.ipv4,
                    hint = context.getString(R.string.easytier_virtual_ipv4_hint)
            ) { value -> state = state.copy(config = state.config.copy(ipv4 = value)) }, sectionParams(top = 8))

            content.addView(createTextField(
                    label = context.getString(R.string.easytier_listeners_label),
                    value = state.config.listeners,
                    hint = context.getString(R.string.easytier_listeners_hint),
                    minLines = 2
            ) { value -> state = state.copy(config = state.config.copy(listeners = value)) }, sectionParams(top = 8))

            content.addView(createTextField(
                    label = context.getString(R.string.easytier_peers_label),
                    value = state.config.peers,
                    hint = context.getString(R.string.easytier_peers_hint),
                    minLines = 2
            ) { value -> state = state.copy(config = state.config.copy(peers = value)) }, sectionParams(top = 8))

            content.addView(createButton(
                    context.getString(if (state.advancedExpanded) R.string.easytier_hide_advanced_flags else R.string.easytier_show_advanced_flags),
                    false
            ) {
                state = state.copy(advancedExpanded = !state.advancedExpanded)
                renderContent()
            }, sectionParams(top = 10, height = dp(44)))

            if (state.advancedExpanded) {
                addSectionTitle(context.getString(R.string.easytier_core_network_behavior))
                addSwitchRow(context.getString(R.string.easytier_use_smoltcp), state.config.useSmoltcp) { state = state.copy(config = state.config.copy(useSmoltcp = it)) }
                addSwitchRow(context.getString(R.string.easytier_latency_priority), state.config.latencyFirst) { state = state.copy(config = state.config.copy(latencyFirst = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_p2p), state.config.disableP2p) { state = state.copy(config = state.config.copy(disableP2p = it)) }
                addSwitchRow(context.getString(R.string.easytier_private_mode), state.config.privateMode) { state = state.copy(config = state.config.copy(privateMode = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_ipv6), state.config.disableIpv6) { state = state.copy(config = state.config.copy(disableIpv6 = it)) }

                addSectionTitle(context.getString(R.string.easytier_proxy_protocol))
                addSwitchRow(context.getString(R.string.easytier_enable_kcp_proxy), state.config.enableKcpProxy) { state = state.copy(config = state.config.copy(enableKcpProxy = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_kcp_input), state.config.disableKcpInput) { state = state.copy(config = state.config.copy(disableKcpInput = it)) }
                addSwitchRow(context.getString(R.string.easytier_enable_quic_proxy), state.config.enableQuicProxy) { state = state.copy(config = state.config.copy(enableQuicProxy = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_quic_input), state.config.disableQuicInput) { state = state.copy(config = state.config.copy(disableQuicInput = it)) }
                addSwitchRow(context.getString(R.string.easytier_proxy_forward_by_system), state.config.proxyForwardBySystem) { state = state.copy(config = state.config.copy(proxyForwardBySystem = it)) }

                addSectionTitle(context.getString(R.string.easytier_security_connection))
                addSwitchRow(context.getString(R.string.easytier_disable_encryption), state.config.disableEncryption) { state = state.copy(config = state.config.copy(disableEncryption = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_udp_hole_punching), state.config.disableUdpHolePunching) { state = state.copy(config = state.config.copy(disableUdpHolePunching = it)) }
                addSwitchRow(context.getString(R.string.easytier_disable_sym_hole_punching), state.config.disableSymHolePunching) { state = state.copy(config = state.config.copy(disableSymHolePunching = it)) }
            }
        }

        private fun createTextField(
                label: String,
                value: String,
                hint: String,
                helper: String = "",
                minLines: Int = 1,
                onChanged: (String) -> Unit
        ): View {
            return createCard(LinearLayout.VERTICAL).apply {
                addText(label, primaryTextColor, 13f, true)
                addView(EditText(context).apply {
                    setText(value)
                    setTextColor(primaryTextColor)
                    setHintTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    this.hint = hint
                    minHeight = dp(if (minLines > 1) 70 else 42)
                    this.minLines = minLines
                    maxLines = if (minLines > 1) 5 else 1
                    gravity = if (minLines > 1) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
                    inputType = if (minLines > 1) {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                    setSingleLine(minLines == 1)
                    background = roundedBackground(softFill, cardStroke, dp(10))
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            onChanged(s?.toString().orEmpty())
                        }
                    })
                }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                })
                if (helper.isNotBlank()) {
                    addText(helper, secondaryTextColor, 12f, false, topPadding = dp(6))
                }
            }
        }

        private fun addSwitchRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
            content.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                background = rowBackground(false)
                isFocusable = true
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = rowBackground(hasFocus)
                }
                addView(TextView(context).apply {
                    text = label
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(AndroidSwitch(context).apply {
                    isChecked = checked
                    thumbTintList = switchThumbTint()
                    trackTintList = switchTrackTint()
                    setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
                })
            }, sectionParams(top = 6))
        }

        private fun createStatusToolbar(onRefresh: () -> Unit): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                addView(createIconButton(
                        context.getString(R.string.easytier_refresh_status),
                        R.drawable.ic_easytier_refresh,
                        onRefresh
                ), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(36)
                ))
            }
        }

        private fun createIconButton(text: String, iconRes: Int, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryTextColor)
                minWidth = dp(104)
                setPadding(dp(12), 0, dp(14), 0)
                val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
                icon?.setTint(primaryTextColor)
                icon?.setBounds(0, 0, dp(17), dp(17))
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = dp(7)
                background = roundedBackground(softFill, cardStroke, dp(16))
                isClickable = true
                isFocusable = true
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = roundedBackground(
                            if (hasFocus) focusedFill else softFill,
                            if (hasFocus) focusedStroke else cardStroke,
                            dp(16)
                    )
                }
                setOnClickListener { onClick() }
            }
        }

        private fun createButton(text: String, accent: Boolean, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (accent) accentTextColor else primaryTextColor)
                background = roundedBackground(
                        if (accent) accentColor else softFill,
                        if (accent) accentFocus else cardStroke,
                        dp(18)
                )
                isClickable = true
                isFocusable = true
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = roundedBackground(
                            if (accent) accentColor else if (hasFocus) focusedFill else softFill,
                            if (accent) accentFocus else if (hasFocus) focusedStroke else cardStroke,
                            dp(18)
                    )
                }
                setOnClickListener { onClick() }
            }
        }

        private fun addSectionTitle(title: String) {
            content.addView(TextView(context).apply {
                text = title
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
            }, sectionParams(top = 12))
        }

        private fun LinearLayout.addInfoRow(label: String, value: String?, topPadding: Int = dp(4)) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, topPadding, 0, 0)
                addView(TextView(context).apply {
                    text = label
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
                addView(TextView(context).apply {
                    text = value ?: context.getString(R.string.easytier_unknown)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f))
            })
        }

        private fun LinearLayout.addText(
                textValue: String,
                color: Int,
                sizeSp: Float,
                bold: Boolean,
                topPadding: Int = 0
        ) {
            addView(TextView(context).apply {
                text = textValue
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                if (bold) {
                    typeface = Typeface.DEFAULT_BOLD
                }
                setPadding(0, topPadding, 0, 0)
            })
        }

        private fun createCard(orientationValue: Int): LinearLayout {
            return LinearLayout(context).apply {
                orientation = orientationValue
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(cardFill, cardStroke, dp(16))
            }
        }

        private fun rowBackground(focused: Boolean): GradientDrawable {
            return roundedBackground(cardFill, if (focused) focusedStroke else cardStroke, dp(14))
        }

        private fun sectionParams(top: Int = 0, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
            return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
                topMargin = top
            }
        }

        private fun peerTitle(peer: FinalPeerInfo): String {
            return when {
                !peer.isInSameSubnet -> "${peer.hostname} (${context.getString(R.string.easytier_subnet_mismatch)}!)"
                !peer.isDirectConnection -> "${peer.hostname} (${context.getString(R.string.easytier_relayed)})"
                else -> peer.hostname
            }
        }

        private fun roundedBackground(fillColor: Int, strokeColor: Int, radius: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius.toFloat()
                setColor(fillColor)
                setStroke(dp(1), strokeColor)
            }
        }

        private fun switchThumbTint(): ColorStateList {
            return ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accentColor, primaryTextColor)
            )
        }

        private fun switchTrackTint(): ColorStateList {
            return ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(withAlpha(accentColor, 96), withAlpha(secondaryTextColor, 70))
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return AndroidColor.argb(
                    alpha,
                    AndroidColor.red(color),
                    AndroidColor.green(color),
                    AndroidColor.blue(color)
            )
        }

        private fun dp(value: Int): Int {
            return (value * resources.displayMetrics.density + 0.5f).toInt()
        }
    }

    // ==================== 配置管理 ====================

    private fun getEasyTierConfig(): String {
        val prefs = activity.getSharedPreferences(EASYTIER_PREFS, Context.MODE_PRIVATE)
        val defaultConfig = "instance_name = \"Default\"\n" +
                "hostname = \"moonlight-V+\"\n" +
                "ipv4 = \"10.0.0.1/24\"\n" +
                "dhcp = false\n" +
                "listeners = [\"tcp://0.0.0.0:11010\", \"udp://0.0.0.0:11010\", \"wg://0.0.0.0:11011\"]\n" +
                "rpc_portal = \"0.0.0.0:0\"\n" +
                "\n" +
                "[network_identity]\n" +
                "network_name = \"easytier\"\n" +
                "network_secret = \"\"\n" +
                "\n" +
                "[[peer]]\n" +
                "uri = \"tcp://public.easytier.top:11010\"\n" +
                "\n" +
                "[flags]\n"
        return prefs.getString(KEY_TOML_CONFIG, defaultConfig)!!
    }

    private fun loadConfigurationState(): EasyTierConfigUiState {
        val currentTomlConfig = getEasyTierConfig()

        val ipv4Full = extractValue(currentTomlConfig, "ipv4", "")
        val ipv4 = if (ipv4Full.contains("/")) {
            ipv4Full.split("/")[0]
        } else {
            ipv4Full
        }
        val isIpv6Enabled = extractValue(currentTomlConfig, "enable_ipv6", "true").toBoolean()
        val isEncryptionEnabled = extractValue(currentTomlConfig, "enable_encryption", "true").toBoolean()

        return EasyTierConfigUiState(
                networkName = extractValue(currentTomlConfig, "network_name", ""),
                networkSecret = extractValue(currentTomlConfig, "network_secret", ""),
                ipv4 = ipv4,
                listeners = extractListAsString(currentTomlConfig, "listeners"),
                peers = extractListAsString(currentTomlConfig, "uri"),
                useSmoltcp = extractValue(currentTomlConfig, "use_smoltcp", "false").toBoolean(),
                latencyFirst = extractValue(currentTomlConfig, "latency_first", "false").toBoolean(),
                disableP2p = extractValue(currentTomlConfig, "disable_p2p", "false").toBoolean(),
                privateMode = extractValue(currentTomlConfig, "private_mode", "false").toBoolean(),
                disableIpv6 = !isIpv6Enabled,
                enableKcpProxy = extractValue(currentTomlConfig, "enable_kcp_proxy", "false").toBoolean(),
                disableKcpInput = extractValue(currentTomlConfig, "disable_kcp_input", "false").toBoolean(),
                enableQuicProxy = extractValue(currentTomlConfig, "enable_quic_proxy", "false").toBoolean(),
                disableQuicInput = extractValue(currentTomlConfig, "disable_quic_input", "false").toBoolean(),
                proxyForwardBySystem = extractValue(currentTomlConfig, "proxy_forward_by_system", "false").toBoolean(),
                disableEncryption = !isEncryptionEnabled,
                disableUdpHolePunching = extractValue(currentTomlConfig, "disable_udp_hole_punching", "false").toBoolean(),
                disableSymHolePunching = extractValue(currentTomlConfig, "disable_sym_hole_punching", "false").toBoolean()
        )
    }

    private fun saveConfiguration(config: EasyTierConfigUiState, showToast: Boolean) {
        // 保存配置
        activity.getSharedPreferences(EASYTIER_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOML_CONFIG, buildTomlFromConfig(config))
                .apply()

        // 重新初始化
        initEasyTierManager()

        if (showToast) {
            Toast.makeText(activity, R.string.easytier_config_saved, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTomlFromConfig(config: EasyTierConfigUiState): String {
        val sb = StringBuilder()
        sb.append("hostname = \"moonlight-V+\"\n")
        sb.append("instance_name = \"Default\"\n")
        sb.append("dhcp = false\n")
        sb.append("ipv4 = \"").append(config.ipv4).append("/24\"\n")

        // 构建listeners
        if (!TextUtils.isEmpty(config.listeners)) {
            val items = config.listeners.split("\n")
            val quotedItems = ArrayList<String>()
            for (item in items) {
                if (item.trim().isNotEmpty()) quotedItems.add("\"" + item.trim() + "\"")
            }
            if (quotedItems.isNotEmpty()) {
                sb.append("listeners = [").append(TextUtils.join(", ", quotedItems)).append("]\n")
            }
        }

        sb.append("rpc_portal = \"0.0.0.0:0\"\n")
        sb.append("\n[network_identity]\n")

        if (!TextUtils.isEmpty(config.networkName)) {
            sb.append("network_name = \"").append(config.networkName).append("\"\n")
        }
        sb.append("network_secret = \"").append(config.networkSecret).append("\"\n")

        // 构建peers
        val peerItems = config.peers.split("\n")
        for (peer in peerItems) {
            if (peer.trim().isNotEmpty()) {
                sb.append("\n[[peer]]\n")
                sb.append("uri = \"").append(peer.trim()).append("\"\n")
            }
        }

        // 构建[flags]部分
        sb.append("\n[flags]\n")
        appendFlagIfNotDefault(sb, "use_smoltcp", config.useSmoltcp, false)
        appendFlagIfNotDefault(sb, "latency_first", config.latencyFirst, false)
        appendFlagIfNotDefault(sb, "disable_p2p", config.disableP2p, false)
        appendFlagIfNotDefault(sb, "private_mode", config.privateMode, false)
        appendFlagIfNotDefault(sb, "enable_ipv6", !config.disableIpv6, true)
        appendFlagIfNotDefault(sb, "enable_kcp_proxy", config.enableKcpProxy, false)
        appendFlagIfNotDefault(sb, "disable_kcp_input", config.disableKcpInput, false)
        appendFlagIfNotDefault(sb, "enable_quic_proxy", config.enableQuicProxy, false)
        appendFlagIfNotDefault(sb, "disable_quic_input", config.disableQuicInput, false)
        appendFlagIfNotDefault(sb, "proxy_forward_by_system", config.proxyForwardBySystem, false)
        appendFlagIfNotDefault(sb, "enable_encryption", !config.disableEncryption, true)
        appendFlagIfNotDefault(sb, "disable_udp_hole_punching", config.disableUdpHolePunching, false)
        appendFlagIfNotDefault(sb, "disable_sym_hole_punching", config.disableSymHolePunching, false)

        return sb.toString()
    }

    // ==================== 状态管理 ====================

    private fun parseNetworkInfoForDialog(jsonString: String, instanceName: String): EasyTierDisplayInfo {
        val displayInfo = EasyTierDisplayInfo()
        try {
            val root = JSONObject(jsonString)
            val instance = resolveInstanceInfo(root, instanceName)
                    ?: throw IllegalStateException("No EasyTier network info instance found")

            // 解析本机信息
            val myNode = instance.getJSONObject("my_node_info")
            var myIp: String? = null
            var myPrefix = 0
            displayInfo.hostname = myNode.getString("hostname")
            displayInfo.version = myNode.getString("version")

            val virtualIpv4 = myNode.optJSONObject("virtual_ipv4")
            if (virtualIpv4 != null) {
                myPrefix = virtualIpv4.getInt("network_length")
                myIp = ipFromInt(virtualIpv4.getJSONObject("address").getInt("addr"))
                displayInfo.virtualIp = "$myIp/$myPrefix"
            } else {
                displayInfo.virtualIp = activity.getString(R.string.easytier_loading)
            }

            val stunInfo = myNode.getJSONObject("stun_info")
            val publicIps = stunInfo.optJSONArray("public_ip")
            if (publicIps != null && publicIps.length() > 0) {
                val ipBuilder = StringBuilder()
                for (i in 0 until publicIps.length()) {
                    if (i > 0) ipBuilder.append("\n")
                    ipBuilder.append(publicIps.getString(i))
                }
                displayInfo.publicIp = ipBuilder.toString()
            } else {
                displayInfo.publicIp = "N/A"
            }

            displayInfo.natType = parseNatType(stunInfo.getInt("udp_nat_type"))

            // 解析路由和对等连接
            val routesMap = parseRoutesToMap(instance.getJSONArray("routes"))
            val peersMap = parsePeersToMap(instance.getJSONArray("peers"))

            val finalPeerList = ArrayList<FinalPeerInfo>()
            for (route in routesMap.values) {
                var inSameSubnet = true
                if (myIp != null && myPrefix > 0 && route.virtualIp != activity.getString(R.string.easytier_none)) {
                    inSameSubnet = isInSameSubnet(myIp, route.virtualIp, myPrefix)
                }

                val peerConn = peersMap[route.peerId]

                if (peerConn != null) {
                    // 直接连接
                    finalPeerList.add(FinalPeerInfo(
                            route.hostname,
                            route.virtualIp,
                            true,
                            inSameSubnet,
                            peerConn.physicalAddr,
                            "${peerConn.latencyUs / 1000} ms",
                            "${formatBytes(peerConn.rxBytes)} / ${formatBytes(peerConn.txBytes)}",
                            route.version,
                            route.natType,
                            route.cost,
                            route.nextHopPeerId,
                            route.peerId,
                            route.instId
                    ))
                } else {
                    // 中继路由
                    val nextHop = routesMap[route.nextHopPeerId]
                    val nextHopHostname = nextHop?.hostname ?: activity.getString(R.string.easytier_unknown)
                    finalPeerList.add(FinalPeerInfo(
                            route.hostname,
                            route.virtualIp,
                            false,
                            inSameSubnet,
                            activity.getString(R.string.easytier_via_peer, nextHopHostname),
                            activity.getString(R.string.easytier_path_latency, route.pathLatency),
                            "N/A",
                            route.version,
                            route.natType,
                            route.cost,
                            route.nextHopPeerId,
                            route.peerId,
                            route.instId
                    ))
                }
            }

            finalPeerList.sortBy { it.hostname }
            displayInfo.finalPeerList = finalPeerList

        } catch (e: Exception) {
            LimeLog.warning("解析JSON失败:$e")
            displayInfo.hostname = activity.getString(R.string.easytier_parse_error)
            displayInfo.version = e.message
        }
        return displayInfo
    }

    // ==================== 工具方法 ====================

    private fun extractValue(toml: String, key: String, defaultValue: String): String {
        for (rawLine in toml.split("\n")) {
            val line = rawLine.trim()
            if (line.startsWith("$key =")) {
                try {
                    return line.split("=", limit = 2)[1].trim().replace("\"", "")
                } catch (e: Exception) { /* ignore */ }
            }
        }
        return defaultValue
    }

    private fun extractListAsString(toml: String, key: String): String {
        if ("uri" == key) {
            val peers = StringBuilder()
            for (rawLine in toml.split("\n")) {
                val line = rawLine.trim()
                if (line.startsWith("uri =")) {
                    if (peers.isNotEmpty()) peers.append("\n")
                    peers.append(line.split("=", limit = 2)[1].trim().replace("\"", ""))
                }
            }
            return peers.toString()
        }
        for (rawLine in toml.split("\n")) {
            val line = rawLine.trim()
            if (line.startsWith("$key =")) {
                try {
                    val list = line.substring(line.indexOf('[') + 1, line.lastIndexOf(']'))
                    return list.replace("\"", "").replace(", ", "\n")
                } catch (e: Exception) { /* ignore */ }
            }
        }
        return ""
    }

    private fun appendFlagIfNotDefault(sb: StringBuilder, key: String, value: Boolean, defaultValue: Boolean) {
        if (value != defaultValue) {
            sb.append(key).append(" = ").append(value).append("\n")
        }
    }

    private fun resolveInstanceInfo(root: JSONObject, preferredName: String): JSONObject? {
        val instances = root.optJSONObject("map") ?: return null
        instances.optJSONObject(preferredName)?.let { return it }

        val keys = instances.keys()
        while (keys.hasNext()) {
            val fallbackName = keys.next()
            val fallback = instances.optJSONObject(fallbackName)
            if (fallback != null) {
                LimeLog.warning("EasyTier instance '$preferredName' not found; using '$fallbackName'")
                return fallback
            }
        }

        return null
    }

    private fun ipFromInt(addr: Int): String {
        return "${(addr ushr 24) and 0xFF}.${(addr ushr 16) and 0xFF}.${(addr ushr 8) and 0xFF}.${addr and 0xFF}"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun parseNatType(typeCode: Int): String {
        return when (typeCode) {
            0 -> activity.getString(R.string.easytier_nat_unknown)
            1 -> activity.getString(R.string.easytier_nat_open_internet)
            2 -> activity.getString(R.string.easytier_nat_no_pat)
            3 -> activity.getString(R.string.easytier_nat_full_cone)
            4 -> activity.getString(R.string.easytier_nat_restricted_cone)
            5 -> activity.getString(R.string.easytier_nat_port_restricted)
            6 -> activity.getString(R.string.easytier_nat_symmetric)
            7 -> activity.getString(R.string.easytier_nat_symmetric_udp_firewall)
            8 -> activity.getString(R.string.easytier_nat_symmetric_easy_inc)
            9 -> activity.getString(R.string.easytier_nat_symmetric_easy_dec)
            else -> "Other Type ($typeCode)"
        }
    }

    private fun isInSameSubnet(ip1: String, ip2: String, prefix: Int): Boolean {
        try {
            val ip1Int = ipToInt(ip1)
            val ip2Int = ipToInt(ip2)
            val mask = -1 shl (32 - prefix)
            val network1 = ip1Int and mask
            val network2 = ip2Int and mask
            return network1 == network2
        } catch (e: Exception) {
            LimeLog.warning("未能检查子网的IP：$ip1, $ip2$e")
            return false
        }
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        return (parts[0].toInt() shl 24) or
                (parts[1].toInt() shl 16) or
                (parts[2].toInt() shl 8) or
                parts[3].toInt()
    }

    private fun parseRoutesToMap(routesJson: JSONArray): Map<Long, RouteData> {
        val map = HashMap<Long, RouteData>()
        for (i in 0 until routesJson.length()) {
            val route = routesJson.getJSONObject(i)
            val peerId = route.getLong("peer_id")
            val ipv4AddrJson = route.optJSONObject("ipv4_addr")
            val virtualIp = if (ipv4AddrJson != null) ipFromInt(ipv4AddrJson.getJSONObject("address").getInt("addr")) else activity.getString(R.string.easytier_none)

            map[peerId] = RouteData(
                    peerId,
                    route.getString("hostname"),
                    virtualIp,
                    route.getLong("next_hop_peer_id"),
                    route.getInt("path_latency"),
                    route.getInt("cost"),
                    route.getString("version"),
                    parseNatType(route.getJSONObject("stun_info").getInt("udp_nat_type")),
                    route.getString("inst_id")
            )
        }
        return map
    }

    private fun parsePeersToMap(peersJson: JSONArray): Map<Long, PeerConnectionData> {
        val map = HashMap<Long, PeerConnectionData>()
        for (i in 0 until peersJson.length()) {
            val peer = peersJson.getJSONObject(i)
            val conns = peer.getJSONArray("conns")
            if (conns.length() > 0) {
                val conn = conns.getJSONObject(0)
                val peerId = conn.getLong("peer_id")
                map[peerId] = PeerConnectionData(
                        peerId,
                        conn.getJSONObject("tunnel").getJSONObject("remote_addr").getString("url"),
                        conn.getJSONObject("stats").getLong("latency_us"),
                        conn.getJSONObject("stats").getLong("rx_bytes"),
                        conn.getJSONObject("stats").getLong("tx_bytes")
                )
            }
        }
        return map
    }

    // ==================== 内部数据类 ====================

    private class EasyTierDisplayInfo {
        var hostname: String? = null
        var version: String? = null
        var virtualIp: String? = null
        var publicIp: String? = null
        var natType: String? = null
        var finalPeerList: List<FinalPeerInfo> = ArrayList()
    }

    private class FinalPeerInfo(
            val hostname: String,
            val virtualIp: String?,
            val isDirectConnection: Boolean,
            val isInSameSubnet: Boolean,
            val connectionDetails: String?,
            val latency: String?,
            val traffic: String?,
            val version: String?,
            val natType: String?,
            val routeCost: Int,
            val nextHopPeerId: Long,
            val peerId: Long,
            val instId: String?
    )

    private class RouteData(
            val peerId: Long,
            val hostname: String,
            val virtualIp: String,
            val nextHopPeerId: Long,
            val pathLatency: Int,
            val cost: Int,
            val version: String,
            val natType: String,
            val instId: String
    )

    private class PeerConnectionData(
            val peerId: Long,
            val physicalAddr: String,
            val latencyUs: Long,
            val rxBytes: Long,
            val txBytes: Long
    )

    companion object {
        private const val TAG = "EasyTierController"
        private const val EASYTIER_PREFS = "easytier_preferences"
        private const val KEY_TOML_CONFIG = "toml_config_string"
    }
}
