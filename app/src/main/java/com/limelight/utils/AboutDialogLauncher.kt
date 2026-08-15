package com.limelight.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.ConfigurationCompat
import com.limelight.R
import com.limelight.handbook.HandbookLauncher
import com.limelight.ui.DialogSideScrollbarView
import com.limelight.ui.GridFocusDirection
import com.limelight.ui.GridFocusNavigator
import com.limelight.ui.UiDialogKeyHandler
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.roundToInt

object AboutDialogLauncher {
    private const val OFFICIAL_SITE_CN_URL = "https://www.alkaidlab.cn/"
    private const val OFFICIAL_SITE_GLOBAL_URL = "https://www.alkaidlab.com/"
    private const val GITHUB_URL = "https://github.com/qiin2333/moonlight-vplus"
    private const val GITHUB_STAR_URL =
        "https://github.com/qiin2333/moonlight-vplus/stargazers"
    private const val BILIBILI_URL = "https://space.bilibili.com/3690974838524514"
    private const val FOUNDATION_SUNSHINE_URL =
        "https://github.com/AlkaidLab/foundation-sunshine"
    private const val MOONLIGHT_PC_URL = "https://github.com/qiin2333/moonlight-qt"
    private const val MOONLIGHT_VPLUS_URL = "https://github.com/qiin2333/moonlight-vplus"
    private const val VOIDLINK_URL =
        "https://apps.apple.com/us/app/voidlink-extreme/id6755103808"
    private const val HARMONY_URL =
        "https://appgallery.huawei.com/app/detail?id=com.alkaidlab.sdream"
    private const val MOONLIGHT_MAC_URL =
        "https://github.com/skyhua0224/moonlight-macos-enhanced"
    private const val QQ_GROUP_KEY = "LlbLDIF_YolaM4HZyLx0xAXXo04ZmoBM"
    private const val ECOSYSTEM_MENU_MIN_WIDTH_DP = 164
    private const val ECOSYSTEM_MENU_MAX_WIDTH_DP = 232

    private data class ActiveDialogs(
        val owner: WeakReference<Context>,
        val orientation: Int,
        var main: AlertDialog? = null,
        var ecosystem: AlertDialog? = null
    ) {
        fun isOwnedBy(context: Context): Boolean = owner.get() === context
    }

    private var activeDialogs: ActiveDialogs? = null

    fun show(context: Context): AlertDialog {
        dismissActiveDialogs()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about, null)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.about_close_button)
        val handbookButton = dialogView.findViewById<Button>(R.id.about_handbook_button)
        val ecosystemButton = dialogView.findViewById<Button>(R.id.about_ecosystem_button)
        val starButton = dialogView.findViewById<Button>(R.id.about_star_button)
        val bilibiliButton = dialogView.findViewById<View>(R.id.about_bilibili_button)
        val githubButton = dialogView.findViewById<Button>(R.id.about_github_button)
        val qqButton = dialogView.findViewById<Button>(R.id.about_qq_button)
        val siteButton = dialogView.findViewById<Button>(R.id.about_site_button)

        dialogView.findViewById<TextView>(R.id.text_version).text = getVersionInfo(context)
        dialogView.findViewById<TextView>(R.id.text_app_name).text = getAppName(context)
        dialogView.findViewById<TextView>(R.id.text_description)
            .setText(R.string.about_dialog_description)

        // PcView is a framework Activity, so keep the system dialog theme that
        // works without requiring an AppCompat host theme on older Android versions.
        val dialog = AlertDialog.Builder(
            context,
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.cancel() }
        handbookButton.setOnClickListener {
            dialog.dismiss()
            HandbookLauncher.openIndex(context)
        }
        ecosystemButton.setOnClickListener {
            showEcosystemDialog(context, ecosystemButton)
        }
        starButton.setOnClickListener {
            if (BrowserOnlyLauncher.open(context, GITHUB_STAR_URL)) {
                dialog.dismiss()
            }
        }
        bilibiliButton.setOnClickListener {
            if (BrowserOnlyLauncher.open(context, BILIBILI_URL)) {
                dialog.dismiss()
            }
        }
        githubButton.setOnClickListener {
            if (BrowserOnlyLauncher.open(context, GITHUB_URL)) {
                dialog.dismiss()
            }
        }
        qqButton.setOnClickListener {
            if (openQqGroup(context)) {
                dialog.dismiss()
            }
        }
        siteButton.setOnClickListener {
            if (BrowserOnlyLauncher.open(context, officialSiteUrl(currentLocale(context)))) {
                dialog.dismiss()
            }
        }

        dialog.show()
        val state = ActiveDialogs(
            owner = WeakReference(context),
            orientation = context.resources.configuration.orientation,
            main = dialog
        )
        activeDialogs = state
        dialog.setOnDismissListener {
            if (activeDialogs === state && state.main === dialog) {
                state.main = null
                clearStateIfEmpty(state)
            }
        }
        AppDialogStyler.applyAboutDialog(dialog, context)
        installDialogInput(dialog)
        applyDialogWidth(dialog, context, portraitMaxDp = 448, landscapeMaxDp = 860)
        bindScrollbar(
            dialogView,
            R.id.about_dialog_scroll,
            R.id.about_dialog_scrollbar
        )
        configureMainNavigation(
            closeButton,
            handbookButton,
            ecosystemButton,
            starButton,
            bilibiliButton,
            githubButton,
            qqButton,
            siteButton
        )
        handbookButton.post { handbookButton.requestFocus() }
        return dialog
    }

    internal fun showEcosystemDialog(
        context: Context,
        returnFocus: View? = null,
        initialFocusIndex: Int = 0
    ): AlertDialog {
        val existingState = activeDialogs
        val state = if (existingState?.isOwnedBy(context) == true) {
            existingState
        } else {
            dismissActiveDialogs()
            ActiveDialogs(
                owner = WeakReference(context),
                orientation = context.resources.configuration.orientation
            ).also { activeDialogs = it }
        }
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_about_ecosystem, null)
        val closeButton = dialogView.findViewById<ImageButton>(
            R.id.about_ecosystem_close_button
        )
        val isLandscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        val projects = ecosystemProjects()

        lateinit var dialog: AlertDialog
        val items: List<View>
        val configureNavigation: () -> Unit
        if (isLandscape) {
            val menu = requireNotNull(
                dialogView.findViewById<LinearLayout>(R.id.about_ecosystem_menu)
            )
            val menuScroll = requireNotNull(
                dialogView.findViewById<ScrollView>(R.id.about_ecosystem_menu_scroll)
            )
            val detailBadge = requireNotNull(
                dialogView.findViewById<TextView>(R.id.about_ecosystem_detail_badge)
            )
            val detailTitle = requireNotNull(
                dialogView.findViewById<TextView>(R.id.about_ecosystem_detail_title)
            )
            val detailPlatform = requireNotNull(
                dialogView.findViewById<TextView>(R.id.about_ecosystem_detail_platform)
            )
            val detailDescription = requireNotNull(
                dialogView.findViewById<TextView>(R.id.about_ecosystem_detail_description)
            )
            val openButton = requireNotNull(
                dialogView.findViewById<Button>(R.id.about_ecosystem_open_button)
            )

            applyEcosystemMenuWidth(context, menuScroll)
            lateinit var selectProject: (Int) -> Unit
            items = createEcosystemMenu(
                context,
                menu,
                projects,
                onSelect = { index -> selectProject(index) },
                onConfirm = { index ->
                    selectProject(index)
                    openButton.requestFocus()
                }
            )
            var selectedIndex = initialFocusIndex.coerceIn(0, projects.lastIndex)
            selectProject = { index ->
                selectedIndex = index.coerceIn(0, projects.lastIndex)
                val project = projects[selectedIndex]
                detailBadge.setText(project.badge)
                detailTitle.setText(project.title)
                detailPlatform.setText(project.platform)
                detailDescription.setText(project.description)
                items.forEachIndexed { itemIndex, item ->
                    item.isSelected = itemIndex == selectedIndex
                }
                openButton.setOnClickListener {
                    if (BrowserOnlyLauncher.open(context, project.url)) {
                        dialog.dismiss()
                    }
                }
            }
            selectProject(selectedIndex)
            configureNavigation = {
                configureEcosystemMenuNavigation(
                    closeButton,
                    items,
                    openButton
                ) { selectedIndex }
            }
        } else {
            val grid = requireNotNull(
                dialogView.findViewById<GridLayout>(R.id.about_ecosystem_grid)
            )
            val columnCount = ecosystemColumnCount(
                context.resources.configuration.screenWidthDp,
                context.resources.configuration.orientation
            )
            items = createEcosystemCards(context, grid, projects, columnCount) { url ->
                if (BrowserOnlyLauncher.open(context, url)) {
                    dialog.dismiss()
                }
            }
            configureNavigation = {
                configureEcosystemNavigation(closeButton, items, columnCount)
            }
        }
        dialog = AlertDialog.Builder(
            context,
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.cancel() }
        dialog.setOnDismissListener {
            if (activeDialogs === state && state.ecosystem === dialog) {
                state.ecosystem = null
                returnFocus?.post {
                    if (returnFocus.isAttachedToWindow && returnFocus.isShown) {
                        returnFocus.requestFocus()
                    }
                }
                clearStateIfEmpty(state)
            }
        }

        dialog.show()
        state.ecosystem = dialog
        AppDialogStyler.applyAboutDialog(dialog, context)
        installDialogInput(dialog)
        applyDialogWidth(dialog, context, portraitMaxDp = 650, landscapeMaxDp = 920)
        bindScrollbar(
            dialogView,
            R.id.about_ecosystem_scroll,
            R.id.about_ecosystem_scrollbar
        )
        configureNavigation()
        val initialFocus = items.getOrNull(initialFocusIndex) ?: closeButton
        initialFocus.post { initialFocus.requestFocus() }
        return dialog
    }

    /** Re-inflate the orientation-specific layout because PcView handles config changes itself. */
    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        val state = activeDialogs?.takeIf { it.isOwnedBy(context) } ?: return
        if (newConfig.orientation == state.orientation) return

        val mainDialog = state.main?.takeIf { it.isShowing }
        val ecosystemDialog = state.ecosystem?.takeIf { it.isShowing }
        if (mainDialog == null && ecosystemDialog == null) return

        val mainFocusId = mainDialog?.currentFocus?.id ?: View.NO_ID
        val ecosystemFocusIndex = ecosystemDialog?.let { dialog ->
            findEcosystemItemIndex(dialog)
        } ?: 0

        activeDialogs = null
        ecosystemDialog?.dismiss()
        mainDialog?.dismiss()

        if (mainDialog != null) {
            val recreatedMain = show(context)
            restoreFocus(recreatedMain, mainFocusId)
            if (ecosystemDialog != null) {
                val ecosystemButton = recreatedMain.findViewById<View>(R.id.about_ecosystem_button)
                showEcosystemDialog(context, ecosystemButton, ecosystemFocusIndex)
            }
        } else {
            showEcosystemDialog(context, initialFocusIndex = ecosystemFocusIndex)
        }
    }

    fun release(context: Context) {
        if (activeDialogs?.isOwnedBy(context) == true) {
            dismissActiveDialogs()
        }
    }

    internal fun officialSiteUrl(locale: Locale): String {
        val isSimplifiedChinese = locale.language.equals("zh", ignoreCase = true) &&
            (locale.script.equals("Hans", ignoreCase = true) ||
                (locale.script.isEmpty() && locale.country.equals("CN", ignoreCase = true)))
        return if (isSimplifiedChinese) {
            OFFICIAL_SITE_CN_URL
        } else {
            OFFICIAL_SITE_GLOBAL_URL
        }
    }

    internal fun ecosystemColumnCount(screenWidthDp: Int, orientation: Int): Int {
        return when {
            orientation == Configuration.ORIENTATION_LANDSCAPE && screenWidthDp >= 720 -> 3
            screenWidthDp >= 600 -> 2
            else -> 1
        }
    }

    private fun createEcosystemCards(
        context: Context,
        grid: GridLayout,
        projects: List<EcosystemProject>,
        columnCount: Int,
        onOpen: (String) -> Unit
    ): List<View> {
        val inflater = LayoutInflater.from(context)
        val gap = dpToPx(context, 5)
        grid.columnCount = columnCount
        grid.rowCount = (projects.size + columnCount - 1) / columnCount

        return projects.mapIndexed { index, project ->
            val card = inflater.inflate(R.layout.view_about_ecosystem_card, grid, false).apply {
                id = View.generateViewId()
                findViewById<TextView>(R.id.about_ecosystem_card_badge)
                    .setText(project.badge)
                findViewById<TextView>(R.id.about_ecosystem_card_title)
                    .setText(project.title)
                findViewById<TextView>(R.id.about_ecosystem_card_platform)
                    .setText(project.platform)
                findViewById<TextView>(R.id.about_ecosystem_card_description)
                    .setText(project.description)
                setOnClickListener { onOpen(project.url) }
            }
            val row = index / columnCount
            val column = index % columnCount
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(gap, gap, gap, gap)
            }
            grid.addView(card, params)
            card
        }
    }

    private fun createEcosystemMenu(
        context: Context,
        menu: LinearLayout,
        projects: List<EcosystemProject>,
        onSelect: (Int) -> Unit,
        onConfirm: (Int) -> Unit
    ): List<View> {
        val inflater = LayoutInflater.from(context)
        val itemGap = dpToPx(context, 4)
        return projects.mapIndexed { index, project ->
            inflater.inflate(R.layout.view_about_ecosystem_menu_item, menu, false).apply {
                id = View.generateViewId()
                (this as TextView).setText(project.title)
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onSelect(index)
                }
                setOnClickListener { onConfirm(index) }
            }.also { item ->
                menu.addView(
                    item,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index != 0) topMargin = itemGap
                    }
                )
            }
        }
    }

    private fun configureMainNavigation(
        closeButton: View,
        handbookButton: View,
        ecosystemButton: View,
        starButton: View,
        bilibiliButton: View,
        githubButton: View,
        qqButton: View,
        siteButton: View
    ) {
        listOf(
            closeButton,
            handbookButton,
            ecosystemButton,
            starButton,
            bilibiliButton,
            githubButton,
            qqButton,
            siteButton
        ).forEach {
            it.isFocusable = true
        }

        closeButton.nextFocusDownId = handbookButton.id
        handbookButton.nextFocusUpId = closeButton.id
        handbookButton.nextFocusRightId = ecosystemButton.id
        handbookButton.nextFocusDownId = bilibiliButton.id
        ecosystemButton.nextFocusUpId = closeButton.id
        ecosystemButton.nextFocusLeftId = handbookButton.id
        ecosystemButton.nextFocusRightId = starButton.id
        ecosystemButton.nextFocusDownId = bilibiliButton.id
        starButton.nextFocusUpId = closeButton.id
        starButton.nextFocusLeftId = ecosystemButton.id
        starButton.nextFocusRightId = starButton.id
        starButton.nextFocusDownId = bilibiliButton.id
        bilibiliButton.nextFocusUpId = handbookButton.id
        bilibiliButton.nextFocusDownId = githubButton.id
        githubButton.nextFocusUpId = bilibiliButton.id
        githubButton.nextFocusLeftId = githubButton.id
        githubButton.nextFocusRightId = qqButton.id
        qqButton.nextFocusUpId = bilibiliButton.id
        qqButton.nextFocusLeftId = githubButton.id
        qqButton.nextFocusRightId = siteButton.id
        siteButton.nextFocusUpId = bilibiliButton.id
        siteButton.nextFocusLeftId = qqButton.id
        siteButton.nextFocusRightId = siteButton.id
    }

    private fun configureEcosystemNavigation(
        closeButton: View,
        cards: List<View>,
        columnCount: Int
    ) {
        closeButton.isFocusable = true
        closeButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                cards.firstOrNull()?.requestFocus()
                true
            } else {
                false
            }
        }

        cards.forEachIndexed { index, card ->
            card.setOnKeyListener { _, keyCode, event ->
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> GridFocusDirection.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> GridFocusDirection.DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> GridFocusDirection.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> GridFocusDirection.RIGHT
                    else -> null
                }
                if (direction == null || event.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    val target = GridFocusNavigator.nextIndex(
                        currentIndex = index,
                        itemCount = cards.size,
                        columnCount = columnCount,
                        direction = direction
                    )
                    if (target == GridFocusNavigator.CLOSE_TARGET) {
                        closeButton.requestFocus()
                    } else {
                        cards.getOrNull(target)?.requestFocus()
                    }
                    true
                }
            }
        }
    }

    private fun configureEcosystemMenuNavigation(
        closeButton: View,
        menuItems: List<View>,
        openButton: View,
        selectedIndex: () -> Int
    ) {
        closeButton.isFocusable = true
        closeButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                menuItems.firstOrNull()?.requestFocus()
                true
            } else {
                false
            }
        }
        menuItems.forEachIndexed { index, item ->
            item.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index == 0) closeButton.requestFocus() else menuItems[index - 1].requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        menuItems.getOrNull(index + 1)?.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        openButton.requestFocus()
                        true
                    }
                    else -> false
                }
            }
        }
        openButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                menuItems.getOrNull(selectedIndex())?.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun installDialogInput(dialog: AlertDialog) {
        dialog.setOnKeyListener { _, keyCode, event ->
            UiDialogKeyHandler.handle(
                action = event.action,
                keyCode = keyCode,
                onDismiss = dialog::cancel,
                onConfirm = { dialog.currentFocus?.performClick() }
            )
        }
    }

    private fun restoreFocus(dialog: AlertDialog, viewId: Int) {
        if (viewId == View.NO_ID) return
        val target = dialog.findViewById<View>(viewId) ?: return
        target.post {
            if (target.isShown) {
                target.requestFocus()
            }
        }
    }

    private fun bindScrollbar(root: View, scrollId: Int, scrollbarId: Int) {
        root.findViewById<DialogSideScrollbarView>(scrollbarId)
            .bindTo(root.findViewById<ScrollView>(scrollId))
    }

    private fun clearStateIfEmpty(state: ActiveDialogs) {
        if (state.main == null && state.ecosystem == null && activeDialogs === state) {
            activeDialogs = null
        }
    }

    private fun dismissActiveDialogs() {
        val state = activeDialogs ?: return
        activeDialogs = null
        state.ecosystem?.dismiss()
        state.main?.dismiss()
    }

    private fun findEcosystemItemIndex(dialog: AlertDialog): Int? {
        val currentFocus = dialog.currentFocus ?: return null
        val container = dialog.findViewById<GridLayout>(R.id.about_ecosystem_grid)
            ?: dialog.findViewById<LinearLayout>(R.id.about_ecosystem_menu)
            ?: return null
        return container.indexOfChild(currentFocus).takeIf { it >= 0 }
    }

    private fun applyEcosystemMenuWidth(context: Context, menuScroll: View) {
        val desiredWidthDp = (context.resources.configuration.screenWidthDp * 0.26f)
            .roundToInt()
            .coerceIn(ECOSYSTEM_MENU_MIN_WIDTH_DP, ECOSYSTEM_MENU_MAX_WIDTH_DP)
        menuScroll.layoutParams = menuScroll.layoutParams.apply {
            width = dpToPx(context, desiredWidthDp)
        }
    }

    private fun applyDialogWidth(
        dialog: AlertDialog,
        context: Context,
        portraitMaxDp: Int,
        landscapeMaxDp: Int
    ) {
        val isLandscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        val maxWidth = dpToPx(context, if (isLandscape) landscapeMaxDp else portraitMaxDp)
        val horizontalInsetDp = if (isLandscape) 40 else 28
        val availableWidth = dpToPx(
            context,
            context.resources.configuration.screenWidthDp - horizontalInsetDp
        )
        dialog.window?.setLayout(
            minOf(maxWidth, availableWidth).coerceAtLeast(1),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun currentLocale(context: Context): Locale {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0]
            ?: Locale.getDefault()
    }

    @SuppressLint("DefaultLocale")
    private fun getVersionInfo(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            String.format(
                "Version %s (Build %d)",
                info.versionName,
                PackageInfoCompat.getLongVersionCode(info)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            "Version Unknown"
        }
    }

    private fun getAppName(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.applicationInfo
                ?.loadLabel(context.packageManager)
                ?.toString()
                ?: "Moonlight V+"
        } catch (_: PackageManager.NameNotFoundException) {
            "Moonlight V+"
        }
    }

    private fun openQqGroup(context: Context): Boolean {
        val url = Uri.Builder()
            .scheme("https")
            .authority("qm.qq.com")
            .appendPath("cgi-bin")
            .appendPath("qm")
            .appendPath("qr")
            .appendQueryParameter("from", "app")
            .appendQueryParameter("p", "android")
            .appendQueryParameter("jump_from", "webapi")
            .appendQueryParameter("k", QQ_GROUP_KEY)
            .build()
        return BrowserOnlyLauncher.open(context, url.toString())
    }

    private fun ecosystemProjects(): List<EcosystemProject> {
        return listOf(
            EcosystemProject(
                R.string.about_dialog_ecosystem_sunshine_badge,
                R.string.about_dialog_ecosystem_sunshine_title,
                R.string.about_dialog_ecosystem_sunshine_platform,
                R.string.about_dialog_ecosystem_sunshine_description,
                FOUNDATION_SUNSHINE_URL
            ),
            EcosystemProject(
                R.string.about_dialog_ecosystem_pc_badge,
                R.string.about_dialog_ecosystem_pc_title,
                R.string.about_dialog_ecosystem_pc_platform,
                R.string.about_dialog_ecosystem_pc_description,
                MOONLIGHT_PC_URL
            ),
            EcosystemProject(
                R.string.about_dialog_ecosystem_vplus_badge,
                R.string.about_dialog_ecosystem_vplus_title,
                R.string.about_dialog_ecosystem_vplus_platform,
                R.string.about_dialog_ecosystem_vplus_description,
                MOONLIGHT_VPLUS_URL
            ),
            EcosystemProject(
                R.string.about_dialog_ecosystem_voidlink_badge,
                R.string.about_dialog_ecosystem_voidlink_title,
                R.string.about_dialog_ecosystem_voidlink_platform,
                R.string.about_dialog_ecosystem_voidlink_description,
                VOIDLINK_URL
            ),
            EcosystemProject(
                R.string.about_dialog_ecosystem_harmony_badge,
                R.string.about_dialog_ecosystem_harmony_title,
                R.string.about_dialog_ecosystem_harmony_platform,
                R.string.about_dialog_ecosystem_harmony_description,
                HARMONY_URL
            ),
            EcosystemProject(
                R.string.about_dialog_ecosystem_mac_badge,
                R.string.about_dialog_ecosystem_mac_title,
                R.string.about_dialog_ecosystem_mac_platform,
                R.string.about_dialog_ecosystem_mac_description,
                MOONLIGHT_MAC_URL
            )
        )
    }

    private fun dpToPx(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private data class EcosystemProject(
        @param:StringRes val badge: Int,
        @param:StringRes val title: Int,
        @param:StringRes val platform: Int,
        @param:StringRes val description: Int,
        val url: String
    )
}
