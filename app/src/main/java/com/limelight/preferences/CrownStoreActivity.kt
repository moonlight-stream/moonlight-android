package com.limelight.preferences

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.limelight.R
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.share.CrownProfileShareManager
import com.limelight.binding.input.advance_setting.share.GitHubCrownProfileStorePublisher
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.limelight.ui.theme.AppShapes
import com.limelight.utils.AppDialogStyler
import com.limelight.utils.ConfigurationSyncScheduler
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.abs

class CrownStoreActivity : AppCompatActivity() {
    private enum class CrownTab {
        STORE,
        MINE
    }

    private data class LocalCrownProfile(
        val id: String,
        val name: String,
        val isActive: Boolean
    )

    private data class CrownStoreUiState(
        val selectedTab: CrownTab = CrownTab.STORE,
        val selectedStoreProfile: CrownProfileShareManager.StoreProfile? = null,
        val storeProfiles: List<CrownProfileShareManager.StoreProfile>? = null,
        val storeLoading: Boolean = false,
        val storeError: String? = null,
        val localProfiles: List<LocalCrownProfile> = emptyList()
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val helper: SuperConfigDatabaseHelper by lazy { SuperConfigDatabaseHelper(this) }

    private var selectedTab = CrownTab.STORE
    private var storeProfiles: List<CrownProfileShareManager.StoreProfile>? = null
    private var storeLoading = false
    private var storeError: String? = null
    private var pendingCrownShareExportString = ""
    private var exportConfigString = ""
    private var mergeTargetConfigId: String? = null
    private var pendingCrownShareImport: CrownProfileShareManager.ImportedProfile? = null
    @Volatile
    private var developerUnlockVerificationRunning = false
    @Volatile
    private var developerPendingDeviceCode: GitHubStarVerifier.DeviceCode? = null
    private var developerDeviceCodeDialog: AlertDialog? = null
    private val composeUiState = mutableStateOf(CrownStoreUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrownStoreScreen(composeUiState.value)
        }
        selectTab(CrownTab.STORE)
    }

    override fun onResume() {
        super.onResume()
        if (selectedTab == CrownTab.MINE) {
            renderMineTab()
        }
    }

    private fun selectTab(tab: CrownTab) {
        selectedTab = tab
        composeUiState.value = composeUiState.value.copy(
            selectedTab = tab,
            selectedStoreProfile = null
        )
        when (tab) {
            CrownTab.STORE -> {
                if (storeProfiles == null && !storeLoading && storeError == null) {
                    loadStoreProfiles()
                } else {
                    renderStoreTab()
                }
            }
            CrownTab.MINE -> renderMineTab()
        }
    }

    private fun renderStoreTab() {
        composeUiState.value = composeUiState.value.copy(
            selectedTab = CrownTab.STORE,
            storeProfiles = storeProfiles,
            storeLoading = storeLoading,
            storeError = storeError
        )
    }

    private fun loadStoreProfiles(force: Boolean = false) {
        if (storeLoading) return
        if (!force && storeProfiles != null) {
            renderStoreTab()
            return
        }

        storeLoading = true
        storeError = null
        renderStoreTab()
        thread(name = "CrownStoreIndex") {
            val result = runCatching {
                val indexText = downloadRemoteText(CROWN_STORE_INDEX_URL, CROWN_STORE_MAX_INDEX_BYTES)
                CrownProfileShareManager.parseStoreIndex(indexText)
            }
            mainHandler.post {
                storeLoading = false
                result
                    .onSuccess {
                        storeProfiles = it
                        storeError = null
                    }
                    .onFailure {
                        Log.e("CrownStore", "Failed to load Crown profile store", it)
                        storeProfiles = null
                        storeError = it.message ?: it.javaClass.simpleName
                        Toast.makeText(this, R.string.toast_crown_store_failed, Toast.LENGTH_LONG).show()
                    }
                if (selectedTab == CrownTab.STORE) {
                    renderStoreTab()
                }
            }
        }
    }

    private fun importStoreProfile(profile: CrownProfileShareManager.StoreProfile) {
        if (!isLayoutBasisCompatible(profile.layoutBasis, currentLayoutBasis())) {
            showStoreLayoutCompatibilityDialog(profile) {
                importStoreProfileUnchecked(profile)
            }
            return
        }
        importStoreProfileUnchecked(profile)
    }

    private fun importStoreProfileUnchecked(profile: CrownProfileShareManager.StoreProfile) {
        val profileUrl = try {
            CrownProfileShareManager.resolveStoreProfileUrl(CROWN_STORE_INDEX_URL, profile.url)
        } catch (e: Exception) {
            Log.e("CrownStore", "Invalid Crown store profile URL", e)
            Toast.makeText(this, R.string.toast_crown_store_profile_failed, Toast.LENGTH_LONG).show()
            return
        }
        importCrownShareFromUrl(
            profileUrl,
            sourceLabelOverride = getString(R.string.crown_store_source_label, profile.name),
            failureToastRes = R.string.toast_crown_store_profile_failed
        )
    }

    private fun showStoreLayoutCompatibilityDialog(
        profile: CrownProfileShareManager.StoreProfile,
        onContinue: () -> Unit
    ) {
        AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.title_crown_store_layout_check)
            .setMessage(layoutCompatibilityMessage(profile.layoutBasis))
            .setPositiveButton(R.string.action_crown_store_continue_import) { _, _ -> onContinue() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun reportStoreProfile(profile: CrownProfileShareManager.StoreProfile) {
        val profileUrl = runCatching {
            CrownProfileShareManager.resolveStoreProfileUrl(CROWN_STORE_INDEX_URL, profile.url)
        }.getOrDefault(profile.url)
        val title = getString(R.string.crown_store_report_issue_title, profile.name)
        val body = getString(R.string.crown_store_report_issue_body, profile.name, profileUrl)
        openUrl(
            "${CROWN_STORE_REPORT_URL}?template=crown_store_report.md" +
                "&title=${urlParam(title)}&body=${urlParam(body)}"
        )
    }

    private fun openStoreProfileDetail(profile: CrownProfileShareManager.StoreProfile) {
        composeUiState.value = composeUiState.value.copy(selectedStoreProfile = profile)
    }

    private fun closeStoreProfileDetail() {
        composeUiState.value = composeUiState.value.copy(selectedStoreProfile = null)
    }

    private fun renderMineTab() {
        val profiles = loadLocalProfiles()
        composeUiState.value = composeUiState.value.copy(
            selectedTab = CrownTab.MINE,
            selectedStoreProfile = null,
            localProfiles = profiles
        )
    }

    @Composable
    private fun CrownStoreScreen(state: CrownStoreUiState) {
        val background = colorResource(R.color.advance_setting_background)
        val selectedProfile = state.selectedStoreProfile
        val storeGridState = rememberLazyStaggeredGridState()
        BackHandler(enabled = selectedProfile != null) {
            closeStoreProfileDetail()
        }
        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = background
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = { CrownStoreTopBar(state) },
                    bottomBar = {
                        if (state.selectedStoreProfile == null) {
                            CrownStoreBottomBar(state.selectedTab)
                        }
                    }
                ) { innerPadding ->
                    if (selectedProfile != null) {
                        CrownStoreProfileDetail(
                            profile = selectedProfile,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        when (state.selectedTab) {
                            CrownTab.STORE -> CrownStoreTabContent(
                                state = state,
                                gridState = storeGridState,
                                modifier = Modifier.padding(innerPadding)
                            )
                            CrownTab.MINE -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                CrownMineTabContent(state.localProfiles)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CrownStoreTopBar(state: CrownStoreUiState) {
        val selectedProfile = state.selectedStoreProfile
        val title = selectedProfile?.name ?: when (state.selectedTab) {
            CrownTab.STORE -> stringResource(R.string.crown_store_tab_store)
            CrownTab.MINE -> stringResource(R.string.crown_store_tab_mine)
        }
        val textColor = colorResource(R.color.crown_text_primary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.crown_store_top_bar_padding_top))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedProfile != null) {
                    closeStoreProfileDetail()
                } else {
                    finish()
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = stringResource(R.string.crown_store_action_back),
                    modifier = Modifier.rotate(180f),
                    tint = textColor
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
    }

    @Composable
    private fun CrownStoreBottomBar(selectedTab: CrownTab) {
        val container = colorResource(R.color.crown_panel_background)
        val selected = colorResource(R.color.crown_accent)
        val unselected = colorResource(R.color.crown_text_secondary)
        NavigationBar(
            containerColor = container,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            NavigationBarItem(
                selected = selectedTab == CrownTab.STORE,
                onClick = { selectTab(CrownTab.STORE) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.phc_crown),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.crown_store_tab_store)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selected,
                    selectedTextColor = selected,
                    unselectedIconColor = unselected,
                    unselectedTextColor = unselected,
                    indicatorColor = colorResource(R.color.crown_accent_pressed)
                )
            )
            NavigationBarItem(
                selected = selectedTab == CrownTab.MINE,
                onClick = { selectTab(CrownTab.MINE) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.phc_list),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.crown_store_tab_mine)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selected,
                    selectedTextColor = selected,
                    unselectedIconColor = unselected,
                    unselectedTextColor = unselected,
                    indicatorColor = colorResource(R.color.crown_accent_pressed)
                )
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun CrownStoreTabContent(
        state: CrownStoreUiState,
        gridState: LazyStaggeredGridState,
        modifier: Modifier = Modifier
    ) {
        val profiles = state.storeProfiles
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column {
                    CrownBodyText(stringResource(R.string.crown_store_store_summary))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrownActionButton(
                            text = stringResource(R.string.crown_store_action_refresh),
                            iconRes = R.drawable.phc_action_reset,
                            modifier = Modifier.weight(1f)
                        ) {
                            loadStoreProfiles(force = true)
                        }
                        CrownActionButton(
                            text = stringResource(R.string.crown_share_action_import_url),
                            iconRes = R.drawable.phc_plug,
                            modifier = Modifier.weight(1f)
                        ) {
                            showCrownShareUrlImportDialog()
                        }
                    }
                }
            }
            when {
                state.storeLoading -> item(span = StaggeredGridItemSpan.FullLine) {
                    CrownLoadingState(stringResource(R.string.toast_crown_store_loading))
                }
                state.storeError != null -> item(span = StaggeredGridItemSpan.FullLine) {
                    CrownStateCard(
                        title = stringResource(R.string.toast_crown_store_failed),
                        message = state.storeError,
                        buttonText = stringResource(R.string.crown_store_action_refresh)
                    ) {
                        loadStoreProfiles(force = true)
                    }
                }
                profiles == null -> item(span = StaggeredGridItemSpan.FullLine) {
                    CrownStateCard(
                        title = stringResource(R.string.crown_store_empty_state_title),
                        message = stringResource(R.string.crown_store_empty_state_message),
                        buttonText = stringResource(R.string.crown_store_action_refresh)
                    ) {
                        loadStoreProfiles(force = true)
                    }
                }
                profiles.isEmpty() -> item(span = StaggeredGridItemSpan.FullLine) {
                    CrownStateCard(
                        title = stringResource(R.string.toast_crown_store_empty),
                        message = stringResource(R.string.crown_store_empty_state_message),
                        buttonText = stringResource(R.string.crown_store_action_refresh)
                    ) {
                        loadStoreProfiles(force = true)
                    }
                }
                else -> items(profiles) { profile ->
                    CrownStoreProfileCard(
                        profile = profile,
                        modifier = Modifier.clickable { openStoreProfileDetail(profile) }
                    )
                }
            }
        }
    }

    @Composable
    private fun CrownMineTabContent(profiles: List<LocalCrownProfile>) {
        CrownMineHero(profiles.size)
        Spacer(modifier = Modifier.height(18.dp))
        CrownSectionHeader(
            title = stringResource(R.string.crown_store_local_profiles),
            iconRes = R.drawable.phc_list,
            countText = stringResource(R.string.crown_store_profile_count, profiles.size)
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (profiles.isEmpty()) {
            CrownStateCard(
                title = stringResource(R.string.crown_config_no_profiles),
                message = stringResource(R.string.crown_store_my_empty_message)
            )
        } else {
            CrownLocalProfilesList(profiles)
        }
        Spacer(modifier = Modifier.height(18.dp))
        CrownSectionHeader(
            title = stringResource(R.string.crown_store_local_tools),
            iconRes = R.drawable.phc_settings
        )
        Spacer(modifier = Modifier.height(10.dp))
        CrownLegacyToolsCard()
    }

    @Composable
    private fun CrownMineHero(profileCount: Int) {
        CrownProfileCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = colorResource(R.color.crown_accent_pressed),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.phc_crown),
                        contentDescription = null,
                        tint = colorResource(R.color.crown_accent),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crown_store_my_workspace_title),
                        color = colorResource(R.color.crown_text_primary),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CrownBodyText(stringResource(R.string.crown_store_my_summary), maxLines = 2)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.crown_input_background),
                            shape = AppShapes.medium
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = profileCount.toString(),
                            color = colorResource(R.color.crown_accent),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.crown_store_profile_unit),
                            color = colorResource(R.color.crown_text_secondary),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_import_package),
                    iconRes = R.drawable.ic_add,
                    primary = true,
                    compact = true,
                    modifier = Modifier.weight(1f)
                ) {
                    openCrownShareDocument()
                }
                CrownActionButton(
                    text = stringResource(R.string.crown_share_action_import_url),
                    iconRes = R.drawable.phc_plug,
                    compact = true,
                    modifier = Modifier.weight(1f)
                ) {
                    showCrownShareUrlImportDialog()
                }
            }
        }
    }

    @Composable
    private fun CrownSectionHeader(title: String, iconRes: Int, countText: String? = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colorResource(R.color.crown_accent),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = colorResource(R.color.crown_text_primary),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!countText.isNullOrBlank()) {
                Text(
                    text = countText,
                    color = colorResource(R.color.crown_text_secondary),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.crown_input_background),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }

    @Composable
    private fun CrownLegacyToolsCard() {
        CrownProfileCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.phc_list),
                    contentDescription = null,
                    tint = colorResource(R.color.crown_text_secondary),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crown_config_action_import_legacy),
                        color = colorResource(R.color.crown_text_primary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    CrownBodyText(stringResource(R.string.crown_store_legacy_tool_summary), maxLines = 2)
                }
                Spacer(modifier = Modifier.width(8.dp))
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_open),
                    iconRes = R.drawable.ic_add,
                    compact = true
                ) {
                    openLegacyImportDocument()
                }
            }
        }
    }

    @Composable
    private fun CrownStoreProfileCard(
        profile: CrownProfileShareManager.StoreProfile,
        modifier: Modifier = Modifier
    ) {
        CrownProfileCard(modifier) {
            CrownCardTitle(profile.name, maxLines = 2)
            val subtitle = listOf(profile.game, profile.author)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                CrownMetaText(subtitle, strong = false)
            }
            if (profile.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                CrownBodyText(profile.summary, maxLines = 2)
            }
            val layoutBasis = formatLayoutBasisCompact(profile.layoutBasis)
            val updatedAt = formatStoreUpdatedAtCompact(profile.updatedAt)
            if (!layoutBasis.isNullOrBlank() || updatedAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                CrownStoreCardFooter(
                    layoutBasis = layoutBasis.orEmpty(),
                    updatedAt = updatedAt
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_import_profile),
                    iconRes = R.drawable.ic_add,
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    importStoreProfile(profile)
                }
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_report_profile),
                    iconRes = R.drawable.phc_info,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reportStoreProfile(profile)
                }
            }
        }
    }

    @Composable
    private fun CrownStoreProfileDetail(
        profile: CrownProfileShareManager.StoreProfile,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            CrownProfileCard {
                Text(
                    text = profile.name,
                    color = colorResource(R.color.crown_text_primary),
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                CrownDetailField(
                    label = stringResource(R.string.label_crown_store_game),
                    value = profile.game
                )
                CrownDetailField(
                    label = stringResource(R.string.label_crown_store_author),
                    value = profile.author
                )
                CrownDetailField(
                    label = stringResource(R.string.label_crown_store_summary),
                    value = profile.summary
                )
                CrownDetailField(
                    label = stringResource(R.string.label_crown_store_tags),
                    value = profile.tags
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                )
                formatLayoutBasis(profile.layoutBasis)?.let {
                    CrownDetailMeta(stringResource(R.string.crown_store_layout_basis, it))
                }
                if (profile.updatedAt.isNotBlank()) {
                    CrownDetailMeta(stringResource(R.string.crown_store_updated_at, formatStoreUpdatedAt(profile.updatedAt)))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_import_profile),
                    iconRes = R.drawable.ic_add,
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    importStoreProfile(profile)
                }
                CrownActionButton(
                    text = stringResource(R.string.crown_store_action_report_profile),
                    iconRes = R.drawable.phc_info,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reportStoreProfile(profile)
                }
            }
        }
    }

    @Composable
    private fun CrownLocalProfilesList(profiles: List<LocalCrownProfile>) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            profiles.forEach { profile ->
                CrownLocalProfileCard(profile)
            }
        }
    }

    @Composable
    private fun CrownLocalProfileCard(
        profile: LocalCrownProfile,
        modifier: Modifier = Modifier
    ) {
        CrownProfileCard(
            modifier = modifier.pointerInput(profile.id) {
                detectTapGestures(onLongPress = { showDeleteLocalProfileDialog(profile) })
            }
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    CrownCardTitle(profile.name, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    CrownMetaText(getString(R.string.crown_store_local_profile_id, profile.id), strong = false)
                }
                if (profile.isActive) {
                    CrownStatusChip(stringResource(R.string.crown_store_profile_active))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CrownActionButton(
                        text = stringResource(R.string.crown_store_action_publish_profile),
                        iconRes = R.drawable.phc_action_check,
                        primary = true,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        showCrownStorePublishMetadataDialog(profile.id, profile.name)
                    }
                    CrownActionButton(
                        text = stringResource(R.string.crown_store_action_export_share),
                        iconRes = R.drawable.phc_action_copy,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        exportCrownSharePackage(profile.id, profile.name)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CrownActionButton(
                        text = stringResource(R.string.crown_store_action_export_legacy_short),
                        iconRes = R.drawable.phc_list,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        exportLegacyConfig(profile.id, profile.name)
                    }
                    CrownActionButton(
                        text = stringResource(R.string.crown_store_action_merge_short),
                        iconRes = R.drawable.phc_plug,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        openLegacyMergeDocument(profile.id)
                    }
                }
            }
        }
    }

    @Composable
    private fun CrownProfileCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = AppShapes.large,
            colors = CardDefaults.cardColors(
                containerColor = colorResource(R.color.crown_section_background)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(
                width = 1.dp,
                color = colorResource(R.color.crown_section_border)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }

    @Composable
    private fun CrownLoadingState(text: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = colorResource(R.color.crown_accent),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            CrownBodyText(text)
        }
    }

    @Composable
    private fun CrownStateCard(
        title: String,
        message: String,
        buttonText: String? = null,
        buttonAction: (() -> Unit)? = null
    ) {
        CrownProfileCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.phc_info),
                    contentDescription = null,
                    tint = colorResource(R.color.crown_text_primary),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = colorResource(R.color.crown_text_primary),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            CrownBodyText(message)
            if (buttonText != null && buttonAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                CrownActionButton(
                    text = buttonText,
                    iconRes = R.drawable.phc_action_reset,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = buttonAction
                )
            }
        }
    }

    @Composable
    private fun CrownCardTitle(text: String, maxLines: Int = 2) {
        Text(
            text = text,
            color = colorResource(R.color.crown_text_primary),
            fontSize = 15.4.sp,
            fontWeight = FontWeight.Bold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }

    @Composable
    private fun CrownBodyText(text: String, maxLines: Int = Int.MAX_VALUE) {
        Text(
            text = text,
            color = colorResource(R.color.crown_text_secondary),
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }

    @Composable
    private fun CrownDetailField(label: String, value: String) {
        if (value.isBlank()) return
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            color = colorResource(R.color.crown_text_primary),
            fontSize = 11.6.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = colorResource(R.color.crown_text_secondary),
            fontSize = 13.5.sp,
            lineHeight = 19.sp
        )
    }

    @Composable
    private fun CrownDetailMeta(text: String) {
        if (text.isBlank()) return
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = text,
            color = colorResource(R.color.crown_text_secondary),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.alpha(0.72f)
        )
    }

    @Composable
    private fun CrownMetaText(text: String, strong: Boolean) {
        Text(
            text = text,
            color = if (strong) colorResource(R.color.crown_text_primary) else colorResource(R.color.crown_text_secondary),
            fontSize = if (strong) 12.2.sp else 11.6.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (strong) 0.84f else 0.76f)
        )
    }

    @Composable
    private fun CrownFootnote(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            color = colorResource(R.color.crown_text_secondary),
            fontSize = 10.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.alpha(0.62f)
        )
    }

    @Composable
    private fun CrownStoreCardFooter(layoutBasis: String, updatedAt: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (layoutBasis.isNotBlank()) {
                CrownFootnote(
                    text = layoutBasis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (updatedAt.isNotBlank()) {
                CrownFootnote(
                    text = updatedAt,
                    modifier = if (layoutBasis.isBlank()) Modifier.weight(1f) else Modifier
                )
            }
        }
    }

    @Composable
    private fun CrownTags(tags: List<String>) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val visibleTags = tags.filter { it.isNotBlank() }.take(2)
            visibleTags.forEach { tag ->
                CrownTagChip(tag, Modifier.weight(1f))
            }
            val overflowCount = tags.size - visibleTags.size
            if (overflowCount > 0) {
                CrownTagChip("+$overflowCount", Modifier.width(36.dp))
            }
        }
    }

    @Composable
    private fun CrownTagChip(text: String, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .height(25.dp)
                .background(
                    color = colorResource(R.color.crown_input_background),
                    shape = AppShapes.small
                )
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = colorResource(R.color.crown_text_secondary),
                fontSize = 10.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun CrownStatusChip(text: String) {
        Row(
            modifier = Modifier
                .background(
                    color = colorResource(R.color.crown_accent_pressed),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colorResource(R.color.crown_accent), CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = colorResource(R.color.crown_accent),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun CrownActionButton(
        text: String,
        iconRes: Int,
        modifier: Modifier = Modifier,
        primary: Boolean = false,
        compact: Boolean = false,
        onClick: () -> Unit
    ) {
        val container = if (primary) colorResource(R.color.crown_accent) else colorResource(R.color.crown_input_background)
        val content = if (primary) colorResource(R.color.app_dialog_title_color) else colorResource(R.color.crown_text_primary)
        ComposeButton(
            onClick = onClick,
            modifier = modifier.height(if (compact) 38.dp else 44.dp),
            shape = AppShapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (primary) {
                    colorResource(R.color.crown_accent).copy(alpha = 0.75f)
                } else {
                    colorResource(R.color.crown_input_border)
                }
            ),
            contentPadding = if (compact) {
                PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            } else {
                ButtonDefaults.ButtonWithIconContentPadding
            }
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
            Text(
                text = text,
                fontSize = if (compact) 10.5.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    private fun showDeleteLocalProfileDialog(profile: LocalCrownProfile) {
        val configId = profile.id.toLongOrNull()
        if (configId == null) {
            Toast.makeText(this, R.string.toast_crown_store_delete_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (configId == DEFAULT_CROWN_CONFIG_ID) {
            Toast.makeText(this, R.string.toast_crown_store_delete_default_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.title_crown_store_delete_profile)
            .setMessage(getString(R.string.message_crown_store_delete_profile, profile.name))
            .setPositiveButton(R.string.action_crown_store_delete_profile) { _, _ ->
                deleteLocalProfile(configId, profile.name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun deleteLocalProfile(configId: Long, profileName: String) {
        runCatching {
            helper.deleteConfig(configId)
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (prefs.getLong(CURRENT_CROWN_CONFIG_ID_KEY, DEFAULT_CROWN_CONFIG_ID) == configId) {
                prefs.edit {
                    putLong(CURRENT_CROWN_CONFIG_ID_KEY, DEFAULT_CROWN_CONFIG_ID)
                }
            }
        }.onSuccess {
            onLocalProfilesChanged()
            Toast.makeText(
                this,
                getString(R.string.toast_crown_store_delete_success, profileName),
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure { error ->
            Log.e("CrownStore", "Failed to delete local Crown profile", error)
            Toast.makeText(this, R.string.toast_crown_store_delete_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLocalProfiles(): List<LocalCrownProfile> {
        val activeConfigId = PreferenceManager.getDefaultSharedPreferences(this)
            .getLong(CURRENT_CROWN_CONFIG_ID_KEY, DEFAULT_CROWN_CONFIG_ID)
            .toString()
        return loadConfigMap(helper).map { (id, name) ->
            LocalCrownProfile(id = id, name = name, isActive = id == activeConfigId)
        }
    }

    private fun loadConfigMap(helper: SuperConfigDatabaseHelper): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        for (id in helper.queryAllConfigIds()) {
            val name = helper.queryConfigAttribute(
                id,
                PageConfigController.COLUMN_STRING_CONFIG_NAME,
                "default"
            ) as String
            map[id.toString()] = name
        }
        return map
    }

    private fun exportCrownSharePackage(configId: String, profileName: String) {
        try {
            val payload = helper.exportConfig(configId.toLong())
            pendingCrownShareExportString = CrownProfileShareManager.createBundle(
                profileName = profileName,
                payload = payload,
                metadata = currentCrownShareExportMetadata()
            )
            createCrownShareDocument(profileName)
        } catch (e: Exception) {
            Log.e("CrownShare", "Failed to export Crown share package", e)
            Toast.makeText(this, R.string.toast_crown_share_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportLegacyConfig(configId: String, profileName: String) {
        exportConfigString = helper.exportConfig(configId.toLong())
        createConfigDocument(profileName)
    }

    private fun openLegacyMergeDocument(configId: String) {
        mergeTargetConfigId = configId
        openConfigDocument(REQUEST_CODE_OPEN_LEGACY_MERGE)
    }

    private fun openLegacyImportDocument() {
        openConfigDocument(REQUEST_CODE_OPEN_LEGACY_IMPORT)
    }

    private fun currentCrownShareExportMetadata(): CrownProfileShareManager.ExportMetadata {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return CrownProfileShareManager.ExportMetadata(
            packageName = packageName,
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

    private fun showCrownStorePublishMetadataDialog(configId: String, defaultName: String) {
        if (!GitHubStarVerifier.isConfigured()) {
            Toast.makeText(this, R.string.toast_developer_oauth_unconfigured, Toast.LENGTH_LONG).show()
            return
        }
        val accessToken = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(DeveloperUnlockSettings.PREF_ACCESS_TOKEN, null)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (accessToken.isNullOrBlank() ||
            !DeveloperUnlockSettings.hasAccessTokenScope(
                prefs,
                GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH
            )) {
            showCrownStoreGitHubAuthorizationRequiredDialog(clearSavedToken = false)
            return
        }

        val defaultAuthor = prefs.getString(DeveloperUnlockSettings.PREF_USER_LOGIN, null).orEmpty()
        val nameInput = crownStorePublishInput(defaultName, R.string.hint_crown_store_profile_name)
        val gameInput = crownStorePublishInput("", R.string.hint_crown_store_game)
        val authorInput = crownStorePublishInput(defaultAuthor, R.string.hint_crown_store_author)
        val tagsInput = crownStorePublishInput("", R.string.hint_crown_store_tags)
        val summaryInput = crownStorePublishInput("", R.string.hint_crown_store_summary).apply {
            setSingleLine(false)
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.START or Gravity.TOP
        }
        val termsCheckBox = CheckBox(this).apply {
            text = getString(R.string.message_crown_store_publish_terms)
            textSize = 12f
            includeFontPadding = true
            setTextColor(ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_title_color))
            buttonTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_accent_color)
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = dp(22)
            setPadding(inset, 0, inset, 0)
            addCrownStorePublishField(R.string.label_crown_store_profile_name, nameInput)
            addCrownStorePublishField(R.string.label_crown_store_game, gameInput)
            addCrownStorePublishField(R.string.label_crown_store_author, authorInput)
            addCrownStorePublishField(R.string.label_crown_store_tags, tagsInput)
            addCrownStorePublishField(R.string.label_crown_store_summary, summaryInput)
            addView(termsCheckBox, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            })
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            addView(container)
        }

        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.title_crown_store_publish_metadata)
            .setMessage(R.string.message_crown_store_publish_metadata)
            .setView(scrollView)
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
                if (!termsCheckBox.isChecked) {
                    Toast.makeText(
                        this,
                        R.string.toast_crown_store_publish_terms_required,
                        Toast.LENGTH_LONG
                    ).show()
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
                validateCrownStoreSubmission(profileName, game, author, summary, tags)?.let { reason ->
                    Toast.makeText(
                        this,
                        getString(R.string.toast_crown_store_publish_invalid_metadata, reason),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                try {
                    val payload = helper.exportConfig(configId.toLong())
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
                    validateCrownStoreBundleForPublishing(bundle)?.let { reason ->
                        Toast.makeText(
                            this,
                            getString(R.string.toast_crown_store_publish_invalid_metadata, reason),
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
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
                    Toast.makeText(this, R.string.toast_crown_store_publish_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
        AppDialogStyler.installDismissKeys(dialog)
    }

    private fun validateCrownStoreSubmission(
        profileName: String,
        game: String,
        author: String,
        summary: String,
        tags: List<String>
    ): String? {
        if (profileName.length > 80) return getString(R.string.crown_store_validation_name_too_long)
        if (game.length > 80) return getString(R.string.crown_store_validation_game_too_long)
        if (author.length > 60) return getString(R.string.crown_store_validation_author_too_long)
        if (summary.length > 240) return getString(R.string.crown_store_validation_summary_too_long)
        if (tags.size > 8) return getString(R.string.crown_store_validation_too_many_tags)
        if (tags.any { it.length > 24 }) return getString(R.string.crown_store_validation_tag_too_long)

        val metadataText = buildString {
            append(profileName).append('\n')
            append(game).append('\n')
            append(author).append('\n')
            append(summary).append('\n')
            append(tags.joinToString("\n"))
        }
        if (CROWN_STORE_EXTERNAL_LOCATOR_PATTERN.containsMatchIn(metadataText)) {
            return getString(R.string.crown_store_validation_external_locator)
        }
        if (CROWN_STORE_SENSITIVE_PATTERN.containsMatchIn(metadataText)) {
            return getString(R.string.crown_store_validation_sensitive_metadata)
        }
        return null
    }

    private fun validateCrownStoreBundleForPublishing(bundle: String): String? {
        return try {
            CrownProfileShareManager.parseImportText(bundle)
            val root = JSONObject(bundle)
            if (!root.hasOnlyKeys(CROWN_STORE_PUBLIC_BUNDLE_KEYS)) {
                return getString(R.string.crown_store_validation_invalid_bundle)
            }
            val profile = root.optJSONObject("profile")
                ?: return getString(R.string.crown_store_validation_invalid_bundle)
            if (!profile.hasOnlyKeys(CROWN_STORE_PUBLIC_PROFILE_KEYS)) {
                return getString(R.string.crown_store_validation_invalid_bundle)
            }
            val profilePayload = profile.optString("payload", "")
            if (CROWN_STORE_SENSITIVE_PATTERN.containsMatchIn(profilePayload)) {
                return getString(R.string.crown_store_validation_sensitive_metadata)
            }
            null
        } catch (e: Exception) {
            Log.e("CrownStore", "Invalid Crown Store bundle", e)
            getString(R.string.crown_store_validation_invalid_bundle)
        }
    }

    private fun crownStorePublishInput(value: String, hintRes: Int): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(value)
            hint = getString(hintRes)
            setTextColor(ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_title_color))
            setHintTextColor(ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_subtitle_color))
            backgroundTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf()
                ),
                intArrayOf(
                    ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_accent_color),
                    ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_subtitle_color)
                )
            )
        }
    }

    private fun LinearLayout.addCrownStorePublishField(labelRes: Int, input: EditText) {
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = getString(labelRes)
                setTextColor(ContextCompat.getColor(this@CrownStoreActivity, R.color.app_dialog_title_color))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            })
        }
        addView(block, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })
    }

    private fun publishCrownStoreProfile(request: GitHubCrownProfileStorePublisher.PublishRequest) {
        val appContext = applicationContext
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

        Toast.makeText(this, R.string.toast_crown_store_publish_started, Toast.LENGTH_LONG).show()
        thread(name = "CrownStorePublish") {
            val result = runCatching {
                GitHubCrownProfileStorePublisher.publish(accessToken, request)
            }

            mainHandler.post {
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
        if (clearSavedToken) {
            PreferenceManager.getDefaultSharedPreferences(this).edit {
                remove(DeveloperUnlockSettings.PREF_ACCESS_TOKEN)
                remove(DeveloperUnlockSettings.PREF_ACCESS_TOKEN_SCOPE)
                remove(DeveloperUnlockSettings.PREF_UNLOCKED)
                remove(DeveloperUnlockSettings.PREF_VERIFIED_AT_MS)
            }
        }

        AlertDialog.Builder(this, R.style.AppDialogStyle)
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
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun showCrownStorePublishSuccessDialog(result: GitHubCrownProfileStorePublisher.PublishResult) {
        AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.title_crown_store_publish_success)
            .setMessage(
                getString(
                    R.string.message_crown_store_publish_success,
                    result.profilePath,
                    result.pullRequestUrl
                )
            )
            .setPositiveButton(R.string.action_open_pull_request) { _, _ ->
                openUrl(result.pullRequestUrl)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun startDeveloperUnlockVerification(scope: GitHubStarVerifier.OAuthScope) {
        if (!GitHubStarVerifier.isConfigured()) {
            Toast.makeText(this, R.string.toast_developer_oauth_unconfigured, Toast.LENGTH_LONG).show()
            return
        }
        if (developerUnlockVerificationRunning) {
            Toast.makeText(this, R.string.toast_developer_verification_running, Toast.LENGTH_LONG).show()
            return
        }

        developerUnlockVerificationRunning = true
        Toast.makeText(this, R.string.toast_developer_verification_started, Toast.LENGTH_LONG).show()
        val appContext = applicationContext
        thread(name = "CrownStoreGitHubDeviceCode") {
            try {
                val deviceCode = GitHubStarVerifier.requestDeviceCode(scope)
                developerPendingDeviceCode = deviceCode
                GitHubDeviceAuthorization.savePendingDeviceCode(appContext, deviceCode)
                mainHandler.post {
                    showDeveloperDeviceCodeDialog(deviceCode)
                }
            } catch (e: Exception) {
                Log.e("DeveloperUnlock", "GitHub star verification failed", e)
                failDeveloperUnlockVerification(appContext, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun showDeveloperDeviceCodeDialog(deviceCode: GitHubStarVerifier.DeviceCode) {
        developerDeviceCodeDialog?.dismiss()
        GitHubDeviceAuthorization.copyDeviceCodeToClipboard(this, deviceCode)
        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
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
                clearDeveloperPendingDeviceCode(applicationContext)
            }
            .setOnCancelListener {
                developerUnlockVerificationRunning = false
                clearDeveloperPendingDeviceCode(applicationContext)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                GitHubDeviceAuthorization.copyDeviceCodeToClipboard(
                    this,
                    deviceCode,
                    showToast = false
                )
                openUrl(GitHubDeviceAuthorization.authorizationUrl(deviceCode))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                pollDeveloperPendingDeviceCode(showPendingToast = true)
            }
        }
        dialog.setOnDismissListener {
            if (developerDeviceCodeDialog === dialog) {
                developerDeviceCodeDialog = null
            }
        }
        developerDeviceCodeDialog = dialog
        dialog.show()
        AppDialogStyler.installDismissKeys(dialog)
    }

    private fun pollDeveloperPendingDeviceCode(showPendingToast: Boolean) {
        val deviceCode = developerPendingDeviceCode ?: GitHubDeviceAuthorization.loadPendingDeviceCode(this)
        if (deviceCode == null) {
            developerUnlockVerificationRunning = false
            Toast.makeText(this, R.string.toast_developer_verification_expired, Toast.LENGTH_LONG).show()
            return
        }

        developerUnlockVerificationRunning = true
        val appContext = applicationContext
        thread(name = "CrownStoreGitHubDevicePoll") {
            try {
                when (val poll = GitHubStarVerifier.pollAccessToken(deviceCode)) {
                    is GitHubStarVerifier.TokenPollResult.Authorized -> {
                        val starCheck = GitHubStarVerifier.checkStar(poll.accessToken)
                        runIfDeveloperAttemptActive(deviceCode) {
                            completeDeveloperUnlockVerification(
                                appContext,
                                poll.accessToken,
                                starCheck,
                                deviceCode.scope
                            )
                        }
                    }
                    GitHubStarVerifier.TokenPollResult.Pending -> {
                        if (showPendingToast) {
                            runIfDeveloperAttemptActive(deviceCode) {
                                Toast.makeText(
                                    appContext,
                                    R.string.toast_developer_authorization_pending,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    is GitHubStarVerifier.TokenPollResult.SlowDown -> {
                        runIfDeveloperAttemptActive(deviceCode) {
                            GitHubDeviceAuthorization.savePendingDeviceCode(
                                appContext,
                                deviceCode.copy(intervalSeconds = poll.intervalSeconds)
                            )
                        }
                    }
                    is GitHubStarVerifier.TokenPollResult.Failed -> {
                        runIfDeveloperAttemptActive(deviceCode) {
                            failDeveloperUnlockVerification(appContext, poll.message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DeveloperUnlock", "GitHub star foreground verification failed", e)
                runIfDeveloperAttemptActive(deviceCode) {
                    failDeveloperUnlockVerification(appContext, e.message ?: e.javaClass.simpleName)
                }
            } finally {
                mainHandler.post {
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
        mainHandler.post {
            if (developerPendingDeviceCode == deviceCode) {
                action()
            }
        }
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
        mainHandler.post {
            if (developerDeviceCodeDialog === dialogToDismiss) {
                dialogToDismiss?.dismiss()
            }
            if (scope == GitHubStarVerifier.OAuthScope.CROWN_STORE_PUBLISH) {
                Toast.makeText(this, R.string.toast_crown_store_github_connected, Toast.LENGTH_LONG).show()
            } else if (starCheck.starred) {
                Toast.makeText(this, R.string.toast_developer_unlocked, Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setTitle(R.string.title_developer_unlock)
                    .setMessage(R.string.message_developer_star_not_found)
                    .setPositiveButton(R.string.action_developer_open_project) { _, _ ->
                        openUrl(DeveloperUnlockSettings.GITHUB_REPO_URL)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                    .also { AppDialogStyler.installDismissKeys(it) }
            }
        }
    }

    private fun failDeveloperUnlockVerification(ctx: Context, message: String) {
        val dialogToDismiss = developerDeviceCodeDialog
        developerUnlockVerificationRunning = false
        clearDeveloperPendingDeviceCode(ctx)
        Log.w("DeveloperUnlock", "GitHub star verification failed: $message")
        mainHandler.post {
            if (developerDeviceCodeDialog === dialogToDismiss) {
                dialogToDismiss?.dismiss()
            }
            Toast.makeText(
                this,
                getString(R.string.toast_developer_verification_failed, message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearDeveloperPendingDeviceCode(ctx: Context) {
        developerPendingDeviceCode = null
        GitHubDeviceAuthorization.clearPendingDeviceCode(ctx)
    }

    private fun showCrownShareUrlImportDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = getString(R.string.hint_crown_share_import_url)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = dp(24)
            setPadding(inset, 0, inset, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
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
        dialog.show()
        AppDialogStyler.installDismissKeys(dialog)
    }

    private fun importCrownShareFromUrl(
        url: String,
        sourceLabelOverride: String? = null,
        failureToastRes: Int = R.string.toast_crown_share_url_failed
    ) {
        val normalizedUrl = url.trim()
        if (!normalizedUrl.startsWith("https://", ignoreCase = true) &&
            !normalizedUrl.startsWith("http://", ignoreCase = true)) {
            Toast.makeText(this, R.string.toast_crown_share_url_invalid, Toast.LENGTH_LONG).show()
            return
        }

        val appContext = applicationContext
        Toast.makeText(this, R.string.toast_crown_share_url_loading, Toast.LENGTH_SHORT).show()
        thread(name = "CrownShareUrlImport") {
            val result = runCatching {
                val importText = downloadRemoteText(normalizedUrl, CROWN_SHARE_MAX_DOWNLOAD_BYTES)
                CrownProfileShareManager.parseImportText(importText)
                    .copy(sourceLabel = sourceLabelOverride ?: crownShareSourceLabel(normalizedUrl))
            }

            mainHandler.post {
                result
                    .onSuccess { importedProfile ->
                        pendingCrownShareImport = importedProfile
                        showCrownShareImportPreview(importedProfile)
                    }
                    .onFailure {
                        Log.e("CrownShare", "Failed to import Crown share package from URL", it)
                        Toast.makeText(appContext, failureToastRes, Toast.LENGTH_LONG).show()
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
        ) + "\n\n" + layoutCompatibilityMessage(profile.layoutBasis)

        AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.crown_share_action_import)
            .setMessage(details)
            .setPositiveButton(R.string.crown_share_install_as_new) { _, _ ->
                importPendingCrownShareAsNew()
            }
            .setNeutralButton(R.string.crown_share_merge_into_existing) { _, _ ->
                showCrownShareMergeTargetDialog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun importPendingCrownShareAsNew() {
        val profile = pendingCrownShareImport ?: return
        val errorCode = helper.importConfig(CrownProfileShareManager.payloadForInstallAsNew(profile))
        if (errorCode == 0) {
            pendingCrownShareImport = null
            onLocalProfilesChanged()
            Toast.makeText(this, R.string.toast_crown_share_import_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCrownShareMergeTargetDialog() {
        val profile = pendingCrownShareImport ?: return
        val configMap = loadConfigMap(helper)
        if (configMap.isEmpty()) {
            Toast.makeText(this, R.string.crown_config_no_profiles, Toast.LENGTH_SHORT).show()
            return
        }

        val ids = configMap.keys.toTypedArray()
        val names = configMap.values.toTypedArray<CharSequence>()
        val dialog = AlertDialog.Builder(this, R.style.AppDialogStyle)
            .setTitle(R.string.crown_share_merge_into_existing)
            .setItems(names) { _, which ->
                val errorCode = helper.mergeConfig(profile.payload, ids[which].toLong())
                if (errorCode == 0) {
                    pendingCrownShareImport = null
                    onLocalProfilesChanged()
                    Toast.makeText(this, R.string.toast_crown_share_merge_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
                }
            }
            .create()
        dialog.show()
        AppDialogStyler.applySystemChoiceList(dialog, this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_CODE_CREATE_CROWN_SHARE -> handleCrownShareExportResult(data)
            REQUEST_CODE_OPEN_CROWN_SHARE -> handleCrownShareImportResult(data)
            REQUEST_CODE_CREATE_LEGACY_EXPORT -> handleLegacyExportResult(data)
            REQUEST_CODE_OPEN_LEGACY_IMPORT -> handleLegacyImportResult(data)
            REQUEST_CODE_OPEN_LEGACY_MERGE -> handleLegacyMergeResult(data)
        }
    }

    private fun handleCrownShareExportResult(data: Intent?) {
        val uri = data?.data ?: return
        try {
            writeDocumentText(uri, pendingCrownShareExportString)
            Toast.makeText(this, R.string.toast_crown_share_export_success, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("CrownShare", "Failed to write Crown share package", e)
            Toast.makeText(this, R.string.toast_crown_share_export_failed, Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, R.string.toast_crown_share_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleLegacyExportResult(data: Intent?) {
        val uri = data?.data ?: return
        try {
            writeDocumentText(uri, exportConfigString)
            Toast.makeText(this, R.string.toast_crown_config_export_success, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, R.string.toast_crown_config_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLegacyImportResult(data: Intent?) {
        val uri = data?.data ?: return
        try {
            val fileContent = readDocumentText(uri)
            val errorCode = helper.importConfig(fileContent)
            if (errorCode == 0) {
                onLocalProfilesChanged()
            }
            showLegacyResultToast(errorCode, R.string.toast_crown_config_import_success)
        } catch (e: IOException) {
            Toast.makeText(this, R.string.toast_crown_config_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLegacyMergeResult(data: Intent?) {
        val uri = data?.data ?: return
        val targetId = mergeTargetConfigId ?: return
        try {
            val fileContent = readDocumentText(uri)
            val errorCode = helper.mergeConfig(fileContent, targetId.toLong())
            if (errorCode == 0) {
                onLocalProfilesChanged()
            }
            showLegacyResultToast(errorCode, R.string.toast_crown_config_merge_success)
        } catch (e: IOException) {
            Toast.makeText(this, R.string.toast_crown_config_import_failed, Toast.LENGTH_SHORT).show()
        } finally {
            mergeTargetConfigId = null
        }
    }

    private fun showLegacyResultToast(errorCode: Int, successRes: Int) {
        val messageRes = when (errorCode) {
            0 -> successRes
            -1, -2 -> R.string.toast_crown_config_import_failed
            -3 -> R.string.toast_crown_config_version_unsupported
            else -> R.string.toast_crown_config_import_failed
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun onLocalProfilesChanged() {
        ConfigurationSyncScheduler.requestSyncSoon(this)
        if (selectedTab == CrownTab.MINE) {
            renderMineTab()
        }
    }

    private fun createConfigDocument(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_TITLE, "$fileName.mdat")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE_CREATE_LEGACY_EXPORT)
    }

    private fun createCrownShareDocument(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_TITLE, CrownProfileShareManager.suggestedFileName(fileName))
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE_CREATE_CROWN_SHARE)
    }

    private fun openConfigDocument(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
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

    private fun readDocumentText(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IOException("Unable to open input stream")
    }

    private fun writeDocumentText(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            ?: throw IOException("Unable to open output stream")
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

    private fun readLimitedText(input: java.io.InputStream, maxBytes: Int): String {
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

    private fun formatLayoutBasis(layoutBasis: CrownProfileShareManager.LayoutBasis?): String? {
        if (layoutBasis == null || !layoutBasis.isPresent()) return null
        val orientationText = when (layoutBasis.orientation.lowercase(Locale.US)) {
            "landscape" -> getString(R.string.crown_store_layout_orientation_landscape)
            "portrait" -> getString(R.string.crown_store_layout_orientation_portrait)
            else -> getString(R.string.crown_store_layout_orientation_unknown)
        }
        val dpiText = if (layoutBasis.densityDpi > 0) {
            " · ${layoutBasis.densityDpi} dpi"
        } else {
            ""
        }
        return "${layoutBasis.widthPx}x${layoutBasis.heightPx}$dpiText · $orientationText"
    }

    private fun layoutCompatibilityMessage(layoutBasis: CrownProfileShareManager.LayoutBasis?): String {
        val currentLayout = currentLayoutBasis()
        val currentText = formatLayoutBasis(currentLayout)
            ?: getString(R.string.crown_store_layout_orientation_unknown)
        val profileText = formatLayoutBasis(layoutBasis)
        return when {
            layoutBasis == null || !layoutBasis.isPresent() -> {
                getString(R.string.message_crown_store_layout_missing, currentText)
            }
            isLayoutBasisCompatible(layoutBasis, currentLayout) -> {
                getString(R.string.message_crown_store_layout_match, currentText)
            }
            else -> {
                getString(R.string.message_crown_store_layout_mismatch, profileText, currentText)
            }
        }
    }

    private fun formatLayoutBasisCompact(layoutBasis: CrownProfileShareManager.LayoutBasis?): String? {
        if (layoutBasis == null || !layoutBasis.isPresent()) return null
        val sizeText = "${layoutBasis.widthPx}x${layoutBasis.heightPx}"
        val dpiText = if (layoutBasis.densityDpi > 0) {
            "${layoutBasis.densityDpi} dpi"
        } else {
            ""
        }
        return listOf(sizeText, dpiText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    private fun isLayoutBasisCompatible(
        profileLayout: CrownProfileShareManager.LayoutBasis?,
        currentLayout: CrownProfileShareManager.LayoutBasis
    ): Boolean {
        if (profileLayout == null || !profileLayout.isPresent() || !currentLayout.isPresent()) return false
        val sameSize = profileLayout.widthPx == currentLayout.widthPx &&
            profileLayout.heightPx == currentLayout.heightPx
        val sameDpi = profileLayout.densityDpi <= 0 ||
            currentLayout.densityDpi <= 0 ||
            abs(profileLayout.densityDpi - currentLayout.densityDpi) <= LAYOUT_DPI_TOLERANCE
        val sameOrientation = profileLayout.orientation.isBlank() ||
            profileLayout.orientation.equals("unknown", ignoreCase = true) ||
            currentLayout.orientation.equals("unknown", ignoreCase = true) ||
            profileLayout.orientation.equals(currentLayout.orientation, ignoreCase = true)
        return sameSize && sameDpi && sameOrientation
    }

    private fun formatStoreUpdatedAt(updatedAt: String): String {
        val trimmed = updatedAt.trim()
        if (trimmed.isBlank()) return trimmed

        val parsedDate = parseStoreUpdatedAt(trimmed) ?: return trimmed
        return DateFormat
            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(parsedDate)
    }

    private fun formatStoreUpdatedAtCompact(updatedAt: String): String {
        val trimmed = updatedAt.trim()
        if (trimmed.isBlank()) return ""

        val parsedDate = parseStoreUpdatedAt(trimmed) ?: return trimmed.take(10)
        val now = Calendar.getInstance()
        val updated = Calendar.getInstance().apply { time = parsedDate }
        val sameYear = now.get(Calendar.YEAR) == updated.get(Calendar.YEAR)
        val sameDay = sameYear &&
            now.get(Calendar.DAY_OF_YEAR) == updated.get(Calendar.DAY_OF_YEAR)
        val hasTime = trimmed.contains('T')
        val pattern = when {
            sameDay && hasTime -> "HH:mm"
            sameYear -> "MMM d"
            else -> "MMM d, yyyy"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(parsedDate)
    }

    private fun parseStoreUpdatedAt(updatedAt: String): Date? {
        val utcPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (pattern in utcPatterns) {
            val date = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(updatedAt)
            }.getOrNull()
            if (date != null) return date
        }

        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(updatedAt)
        }.getOrNull()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_developer_open_project_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun urlParam(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun JSONObject.hasOnlyKeys(allowedKeys: Set<String>): Boolean {
        val iterator = keys()
        while (iterator.hasNext()) {
            if (iterator.next() !in allowedKeys) {
                return false
            }
        }
        return true
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val REQUEST_CODE_CREATE_LEGACY_EXPORT = 501
        private const val REQUEST_CODE_OPEN_LEGACY_IMPORT = 502
        private const val REQUEST_CODE_OPEN_LEGACY_MERGE = 503
        private const val REQUEST_CODE_CREATE_CROWN_SHARE = 504
        private const val REQUEST_CODE_OPEN_CROWN_SHARE = 505
        private const val CROWN_STORE_INDEX_URL =
            "https://raw.githubusercontent.com/qiin2333/crown-profiles/main/index/v1.json"
        private const val CROWN_STORE_MAX_INDEX_BYTES = 256 * 1024
        private const val CROWN_SHARE_MAX_DOWNLOAD_BYTES = 512 * 1024
        private const val LAYOUT_DPI_TOLERANCE = 8
        private const val DEFAULT_CROWN_CONFIG_ID = 0L
        private const val CURRENT_CROWN_CONFIG_ID_KEY = "current_config_id"
        private const val CROWN_STORE_REPORT_URL =
            "https://github.com/qiin2333/crown-profiles/issues/new"
        private val CROWN_STORE_EXTERNAL_LOCATOR_PATTERN =
            Regex("https?://|www\\.|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
        private val CROWN_STORE_SENSITIVE_PATTERN =
            Regex("ghp_|github_pat_|-----BEGIN|password|passwd|token|secret|private key|pairing|clientcert|clientkey", RegexOption.IGNORE_CASE)
        private val CROWN_STORE_PUBLIC_BUNDLE_KEYS = setOf(
            "kind",
            "schemaVersion",
            "bundleId",
            "name",
            "summary",
            "compatibility",
            "profile",
            "createdAt",
            "updatedAt",
            "packageName",
            "layoutBasis",
            "author",
            "game",
            "tags",
            "appVersionName"
        )
        private val CROWN_STORE_PUBLIC_PROFILE_KEYS = setOf(
            "profileId",
            "name",
            "payload",
            "payloadSha256"
        )
    }
}
