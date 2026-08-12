package com.limelight.utils.easytier

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.jni.EasyTierManager
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.ui.theme.AppShapes
import com.limelight.utils.AppDialogStyler

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

    private data class EasyTierDialogUiState(
            val selectedTab: EasyTierTab = EasyTierTab.STATUS,
            val config: EasyTierConfigUiState = EasyTierConfigUiState(),
            val statusJson: String? = null,
            val advancedExpanded: Boolean = false
    ) {
        val isRunning: Boolean
            get() = !statusJson.isNullOrEmpty()
    }

    private sealed class EasyTierDialogAction {
        data class SelectTab(val tab: EasyTierTab) : EasyTierDialogAction()
        data class UpdateConfig(val config: EasyTierConfigUiState) : EasyTierDialogAction()
        data class SetAdvancedExpanded(val expanded: Boolean) : EasyTierDialogAction()
        object RefreshStatus : EasyTierDialogAction()
        object ToggleService : EasyTierDialogAction()
        object SaveConfig : EasyTierDialogAction()
        object Close : EasyTierDialogAction()
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
        val initialState = EasyTierDialogUiState(
                config = loadConfigurationState(),
                statusJson = easyTierManager?.latestNetworkInfoJson
        )

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val uiState = remember { mutableStateOf(initialState) }
                val dispatch = { action: EasyTierDialogAction ->
                    handleDialogAction(action, uiState.value) { uiState.value = it }
                }

                EasyTierPanel(
                        state = uiState.value,
                        onAction = dispatch
                )
            }
        }

        currentDialog = ComponentDialog(activity, R.style.AppDialogStyle).apply {
            setContentView(composeView)
        }
        currentDialog?.show()
        currentDialog?.let { AppDialogStyler.applyCustomContent(it, activity) }
    }

    private fun handleDialogAction(
            action: EasyTierDialogAction,
            state: EasyTierDialogUiState,
            updateState: (EasyTierDialogUiState) -> Unit
    ) {
        when (action) {
            is EasyTierDialogAction.SelectTab -> updateState(state.copy(selectedTab = action.tab))
            is EasyTierDialogAction.UpdateConfig -> updateState(state.copy(config = action.config))
            is EasyTierDialogAction.SetAdvancedExpanded -> updateState(state.copy(advancedExpanded = action.expanded))
            EasyTierDialogAction.RefreshStatus -> {
                updateState(state.copy(statusJson = easyTierManager?.latestNetworkInfoJson))
                Toast.makeText(activity, R.string.easytier_status_refreshed, Toast.LENGTH_SHORT).show()
            }
            EasyTierDialogAction.ToggleService -> {
                if (state.isRunning) {
                    Toast.makeText(activity, R.string.easytier_stopped, Toast.LENGTH_SHORT).show()
                    easyTierManager?.stop()
                    updateState(state.copy(statusJson = null))
                    currentDialog?.dismiss()
                } else {
                    saveConfiguration(state.config, showToast = false)
                    vpnCallback.requestVpnPermission()
                    currentDialog?.dismiss()
                }
            }
            EasyTierDialogAction.SaveConfig -> {
                saveConfiguration(state.config, showToast = true)
                updateState(state.copy(statusJson = easyTierManager?.latestNetworkInfoJson))
            }
            EasyTierDialogAction.Close -> currentDialog?.dismiss()
        }
    }

    @Composable
    private fun EasyTierPanel(
            state: EasyTierDialogUiState,
            onAction: (EasyTierDialogAction) -> Unit
    ) {
        val accent = colorResource(R.color.crown_accent)
        val panel = colorResource(R.color.crown_panel_background)
        val card = colorResource(R.color.crown_section_background)
        val input = colorResource(R.color.crown_input_background)
        val textPrimary = colorResource(R.color.crown_text_primary)
        val textSecondary = colorResource(R.color.crown_text_secondary)
        MaterialTheme(
                colorScheme = darkColorScheme(
                        primary = accent,
                        surface = panel,
                        onSurface = textPrimary,
                        surfaceVariant = input,
                        onSurfaceVariant = textSecondary
                )
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                Surface(
                        modifier = Modifier
                                .widthIn(max = 560.dp)
                                .heightIn(max = 560.dp),
                        shape = AppShapes.medium,
                        color = panel,
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp
                ) {
                    Column(
                            modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = stringResource(R.string.easytier_panel_title),
                                        color = textPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Text(
                                        text = stringResource(
                                                if (state.isRunning) R.string.easytier_status_running
                                                else R.string.easytier_status_stopped
                                        ),
                                        color = textSecondary,
                                        fontSize = 12.5.sp
                                )
                            }
                            EasyTierStatusPill(state.isRunning)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TabRow(
                                selectedTabIndex = if (state.selectedTab == EasyTierTab.STATUS) 0 else 1,
                                containerColor = Color.Transparent,
                                contentColor = accent
                        ) {
                            Tab(
                                    selected = state.selectedTab == EasyTierTab.STATUS,
                                    onClick = { onAction(EasyTierDialogAction.SelectTab(EasyTierTab.STATUS)) },
                                    text = { Text(stringResource(R.string.easytier_tab_status)) }
                            )
                            Tab(
                                    selected = state.selectedTab == EasyTierTab.CONFIG,
                                    onClick = { onAction(EasyTierDialogAction.SelectTab(EasyTierTab.CONFIG)) },
                                    text = { Text(stringResource(R.string.easytier_tab_config)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .heightIn(max = 360.dp)
                        ) {
                            when (state.selectedTab) {
                                EasyTierTab.STATUS -> EasyTierStatusTab(
                                        state.statusJson,
                                        onRefresh = { onAction(EasyTierDialogAction.RefreshStatus) }
                                )
                                EasyTierTab.CONFIG -> EasyTierConfigTab(
                                        config = state.config,
                                        advancedExpanded = state.advancedExpanded,
                                        onConfigChange = { onAction(EasyTierDialogAction.UpdateConfig(it)) },
                                        onAdvancedExpandedChange = { onAction(EasyTierDialogAction.SetAdvancedExpanded(it)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                    onClick = { onAction(EasyTierDialogAction.Close) },
                                    modifier = Modifier.weight(1f),
                                    shape = AppShapes.medium
                            ) {
                                Text(stringResource(R.string.dialog_button_close))
                            }
                            ComposeButton(
                                    onClick = { onAction(EasyTierDialogAction.SaveConfig) },
                                    modifier = Modifier.weight(1.25f),
                                    shape = AppShapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                            containerColor = input,
                                            contentColor = textPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.config_sync_action_export), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ComposeButton(
                                    onClick = { onAction(EasyTierDialogAction.ToggleService) },
                                    modifier = Modifier.weight(1.25f),
                                    shape = AppShapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = colorResource(R.color.app_dialog_text_primary)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                        stringResource(if (state.isRunning) R.string.dialog_button_stop else R.string.dialog_button_start),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EasyTierStatusPill(isRunning: Boolean) {
        val accent = colorResource(if (isRunning) R.color.crown_accent else R.color.crown_text_secondary)
        val textColor = colorResource(R.color.crown_text_primary)
        Box(
                modifier = Modifier
                        .background(accent.copy(alpha = if (isRunning) 0.28f else 0.16f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                    text = stringResource(if (isRunning) R.string.easytier_pill_running else R.string.easytier_pill_idle),
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun EasyTierStatusTab(statusJson: String?, onRefresh: () -> Unit) {
        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ComposeButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.crown_input_background),
                            contentColor = colorResource(R.color.crown_text_primary)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.easytier_refresh_status))
            }

            if (statusJson.isNullOrEmpty()) {
                EasyTierInfoCard {
                    Text(
                            text = stringResource(R.string.easytier_service_not_running),
                            color = colorResource(R.color.crown_text_primary),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                            text = stringResource(R.string.easytier_refresh_hint),
                            color = colorResource(R.color.crown_text_secondary),
                            fontSize = 13.sp
                    )
                }
                return@Column
            }

            val displayInfo = remember(statusJson) {
                parseNetworkInfoForDialog(statusJson, instanceName)
            }

            EasyTierSectionTitle(stringResource(R.string.easytier_local_info))
            EasyTierInfoCard {
                EasyTierInfoRow(stringResource(R.string.easytier_hostname), displayInfo.hostname)
                EasyTierInfoRow(stringResource(R.string.easytier_virtual_ip), displayInfo.virtualIp)
                EasyTierInfoRow(stringResource(R.string.easytier_public_ip), displayInfo.publicIp)
                EasyTierInfoRow(stringResource(R.string.easytier_nat_type), displayInfo.natType)
            }

            EasyTierSectionTitle(stringResource(R.string.easytier_peers_count, displayInfo.finalPeerList.size))
            if (displayInfo.finalPeerList.isEmpty()) {
                EasyTierInfoCard {
                    Text(
                            text = stringResource(R.string.easytier_no_peers),
                            color = colorResource(R.color.crown_text_secondary),
                            fontSize = 13.sp
                    )
                }
            } else {
                displayInfo.finalPeerList.forEach { peer ->
                    EasyTierPeerCard(peer)
                }
            }
        }
    }

    @Composable
    private fun EasyTierConfigTab(
            config: EasyTierConfigUiState,
            advancedExpanded: Boolean,
            onConfigChange: (EasyTierConfigUiState) -> Unit,
            onAdvancedExpandedChange: (Boolean) -> Unit
    ) {
        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EasyTierTextField(
                    label = stringResource(R.string.layout_dlg_easytier_panel_text_7095b),
                    value = config.networkName,
                    onValueChange = { onConfigChange(config.copy(networkName = it)) },
                    placeholder = stringResource(R.string.layout_dlg_easytier_panel_hint_99c30)
            )
            EasyTierTextField(
                    label = stringResource(R.string.layout_dlg_easytier_panel_text_89d10),
                    value = config.networkSecret,
                    onValueChange = { onConfigChange(config.copy(networkSecret = it)) }
            )
            EasyTierTextField(
                    label = stringResource(R.string.layout_dlg_easytier_panel_text_4af73),
                    value = config.ipv4,
                    onValueChange = { onConfigChange(config.copy(ipv4 = it)) },
                    placeholder = stringResource(R.string.layout_dlg_easytier_panel_hint_8913a)
            )
            EasyTierTextField(
                    label = stringResource(R.string.layout_dlg_easytier_panel_text_ed11e),
                    value = config.listeners,
                    onValueChange = { onConfigChange(config.copy(listeners = it)) },
                    placeholder = stringResource(R.string.layout_dlg_easytier_panel_hint_388a0),
                    minLines = 2
            )
            EasyTierTextField(
                    label = stringResource(R.string.layout_dlg_easytier_panel_text_89168),
                    value = config.peers,
                    onValueChange = { onConfigChange(config.copy(peers = it)) },
                    placeholder = stringResource(R.string.layout_dlg_easytier_panel_hint_eabff),
                    minLines = 2
            )

            TextButton(
                    onClick = { onAdvancedExpandedChange(!advancedExpanded) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
            ) {
                Text(stringResource(
                        if (advancedExpanded) R.string.easytier_hide_advanced_flags
                        else R.string.easytier_show_advanced_flags
                ))
            }

            if (advancedExpanded) {
                EasyTierSwitchSection(stringResource(R.string.layout_dlg_easytier_panel_text_e7e21))
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_4ad29), config.useSmoltcp) { onConfigChange(config.copy(useSmoltcp = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_6785a), config.latencyFirst) { onConfigChange(config.copy(latencyFirst = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_faebe), config.disableP2p) { onConfigChange(config.copy(disableP2p = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_fd3ab), config.privateMode) { onConfigChange(config.copy(privateMode = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_c2266), config.disableIpv6) { onConfigChange(config.copy(disableIpv6 = it)) }

                EasyTierSwitchSection(stringResource(R.string.layout_dlg_easytier_panel_text_416db))
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_93070), config.enableKcpProxy) { onConfigChange(config.copy(enableKcpProxy = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_6ea99), config.disableKcpInput) { onConfigChange(config.copy(disableKcpInput = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_6618e), config.enableQuicProxy) { onConfigChange(config.copy(enableQuicProxy = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_02eb9), config.disableQuicInput) { onConfigChange(config.copy(disableQuicInput = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_4b503), config.proxyForwardBySystem) { onConfigChange(config.copy(proxyForwardBySystem = it)) }

                EasyTierSwitchSection(stringResource(R.string.layout_dlg_easytier_panel_text_9c403))
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_e7fa3), config.disableEncryption) { onConfigChange(config.copy(disableEncryption = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_c5d9c), config.disableUdpHolePunching) { onConfigChange(config.copy(disableUdpHolePunching = it)) }
                EasyTierSwitchRow(stringResource(R.string.layout_dlg_easytier_panel_text_8ccac), config.disableSymHolePunching) { onConfigChange(config.copy(disableSymHolePunching = it)) }
            }
        }
    }

    @Composable
    private fun EasyTierInfoCard(content: @Composable () -> Unit) {
        Card(
                colors = CardDefaults.cardColors(containerColor = colorResource(R.color.crown_section_background)),
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                    modifier = Modifier.padding(12.dp),
                    content = { content() }
            )
        }
    }

    @Composable
    private fun EasyTierPeerCard(peer: FinalPeerInfo) {
        val titleColor = if (!peer.isInSameSubnet) {
            Color(0xFFFF7777)
        } else {
            colorResource(R.color.crown_text_primary)
        }
        EasyTierInfoCard {
            Text(
                    text = peerTitle(peer),
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            EasyTierInfoRow(stringResource(R.string.easytier_virtual_ip), peer.virtualIp)
            EasyTierInfoRow(stringResource(R.string.easytier_nat_type), peer.natType)
            EasyTierInfoRow(
                    stringResource(if (peer.isDirectConnection) R.string.easytier_physical_address else R.string.easytier_next_hop),
                    peer.connectionDetails
            )
            EasyTierInfoRow(stringResource(R.string.easytier_latency), peer.latency)
            EasyTierInfoRow(stringResource(R.string.easytier_traffic), peer.traffic)
        }
    }

    private fun peerTitle(peer: FinalPeerInfo): String {
        return when {
            !peer.isInSameSubnet -> "${peer.hostname} (${activity.getString(R.string.easytier_subnet_mismatch)}!)"
            !peer.isDirectConnection -> "${peer.hostname} (${activity.getString(R.string.easytier_relayed)})"
            else -> peer.hostname
        }
    }

    @Composable
    private fun EasyTierInfoRow(label: String, value: String?) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
        ) {
            Text(
                    text = label,
                    color = colorResource(R.color.crown_text_primary),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(104.dp)
            )
            Text(
                    text = value ?: stringResource(R.string.easytier_unknown),
                    color = colorResource(R.color.crown_text_secondary),
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun EasyTierSectionTitle(title: String) {
        Text(
                text = title,
                color = colorResource(R.color.crown_text_primary),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
        )
    }

    @Composable
    private fun EasyTierTextField(
            label: String,
            value: String,
            onValueChange: (String) -> Unit,
            placeholder: String = "",
            minLines: Int = 1
    ) {
        OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = {
                    if (placeholder.isNotBlank()) {
                        Text(placeholder)
                    }
                },
                minLines = minLines,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small
        )
    }

    @Composable
    private fun EasyTierSwitchSection(title: String) {
        Text(
                text = title,
                color = colorResource(R.color.crown_text_secondary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
    }

    @Composable
    private fun EasyTierSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.crown_section_background), AppShapes.small)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = label,
                    color = colorResource(R.color.crown_text_primary),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.size(width = 48.dp, height = 32.dp)
            )
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
        return EasyTierTomlCodec.parseConfig(getEasyTierConfig())
    }

    private fun saveConfiguration(config: EasyTierConfigUiState, showToast: Boolean) {
        // 保存配置
        activity.getSharedPreferences(EASYTIER_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOML_CONFIG, EasyTierTomlCodec.build(config))
                .apply()

        // 重新初始化
        initEasyTierManager()

        if (showToast) {
            Toast.makeText(activity, R.string.easytier_config_saved, Toast.LENGTH_LONG).show()
        }
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
                displayInfo.publicIp = activity.getString(R.string.easytier_unknown)
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
                            activity.getString(R.string.easytier_unknown),
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
