package com.limelight.utils

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.ConfigurationCompat
import com.limelight.R
import com.limelight.handbook.HandbookLauncher
import com.limelight.ui.AboutDialogContent
import com.limelight.ui.EcosystemDialogContent
import com.limelight.ui.EcosystemProject
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Compose-based About / Ecosystem dialog launcher.
 *
 * The previous Views implementation (dialog_about*.xml + 9 selector drawables +
 * manual D-pad focus navigation) is replaced by a single Compose file
 * [com.limelight.ui.AboutDialog.kt]. This class only keeps dialog lifecycle,
 * external-link opening, and the PcView configuration-change contract.
 */
object AboutDialogLauncher {
    internal data class DialogSnapshot(
        val main: Dialog?,
        val ecosystem: Dialog?,
        val mainFocusIndex: Int,
        val ecosystemFocusIndex: Int
    )

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

    private data class ActiveDialogs(
        val owner: WeakReference<Context>,
        val orientation: Int,
        var main: Dialog? = null,
        var ecosystem: Dialog? = null,
        var mainFocusIndex: Int = 0,
        var ecosystemFocusIndex: Int = 0,
        val mainFocusRequestGeneration: androidx.compose.runtime.MutableIntState = mutableIntStateOf(0)
    ) {
        fun isOwnedBy(context: Context): Boolean = owner.get() === context
    }

    private var activeDialogs: ActiveDialogs? = null

    fun show(context: Context): Dialog = show(context, initialFocusIndex = 0)

    private fun show(context: Context, initialFocusIndex: Int): Dialog {
        dismissActiveDialogs()
        val state = ActiveDialogs(
            owner = WeakReference(context),
            orientation = context.resources.configuration.orientation,
            mainFocusIndex = initialFocusIndex
        )
        activeDialogs = state

        val dialog = createDialog(context) { d ->
            AboutDialogContent(
                appName = getAppName(context),
                versionInfo = getVersionInfo(context),
                onHandbook = {
                    d.dismiss()
                    HandbookLauncher.openIndex(context)
                },
                onEcosystem = { showEcosystemDialog(context) },
                onStar = { if (BrowserOnlyLauncher.open(context, GITHUB_STAR_URL)) d.dismiss() },
                onBilibili = { if (BrowserOnlyLauncher.open(context, BILIBILI_URL)) d.dismiss() },
                onGithub = { if (BrowserOnlyLauncher.open(context, GITHUB_URL)) d.dismiss() },
                onQq = { if (openQqGroup(context)) d.dismiss() },
                onSite = {
                    if (BrowserOnlyLauncher.open(context, officialSiteUrl(currentLocale(context)))) {
                        d.dismiss()
                    }
                },
                onClose = { d.cancel() },
                initialFocusIndex = state.mainFocusIndex,
                focusRequestGeneration = state.mainFocusRequestGeneration.intValue,
                onFocusChanged = { state.mainFocusIndex = it }
            )
        }

        state.main = dialog
        dialog.setOnDismissListener {
            if (activeDialogs === state && state.main === dialog) {
                state.main = null
                clearStateIfEmpty(state)
            }
        }
        dialog.show()
        applyDialogWidth(dialog, context, portraitMaxDp = 448, landscapeMaxDp = 860)
        return dialog
    }

    internal fun showEcosystemDialog(
        context: Context,
        initialFocusIndex: Int = 0
    ): Dialog {
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

        state.ecosystem?.dismiss()
        val projects = ecosystemProjects(context)
        state.ecosystemFocusIndex = initialFocusIndex
        val dialog = createDialog(context) { d ->
            EcosystemDialogContent(
                projects = projects,
                onOpen = { project ->
                    if (BrowserOnlyLauncher.open(context, project.url)) {
                        d.dismiss()
                    }
                },
                onClose = { d.cancel() },
                initialFocusIndex = state.ecosystemFocusIndex,
                onFocusChanged = { state.ecosystemFocusIndex = it }
            )
        }

        state.ecosystem = dialog
        dialog.setOnDismissListener {
            if (activeDialogs === state && state.ecosystem === dialog) {
                state.ecosystem = null
                if (state.main?.isShowing == true) {
                    state.mainFocusRequestGeneration.intValue++
                }
                clearStateIfEmpty(state)
            }
        }
        dialog.show()
        applyDialogWidth(dialog, context, portraitMaxDp = 650, landscapeMaxDp = 920)
        return dialog
    }

    /** Re-create dialogs on orientation change because PcView handles config changes itself. */
    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        val state = activeDialogs?.takeIf { it.isOwnedBy(context) } ?: return
        if (newConfig.orientation == state.orientation) return

        val mainShowing = state.main?.isShowing == true
        val ecosystemShowing = state.ecosystem?.isShowing == true
        val mainFocusIndex = state.mainFocusIndex
        val ecosystemFocusIndex = state.ecosystemFocusIndex
        if (!mainShowing && !ecosystemShowing) return

        activeDialogs = null
        state.ecosystem?.dismiss()
        state.main?.dismiss()

        if (mainShowing) {
            show(context, mainFocusIndex)
        }
        if (ecosystemShowing) {
            showEcosystemDialog(context, ecosystemFocusIndex)
        }
    }

    fun release(context: Context) {
        if (activeDialogs?.isOwnedBy(context) == true) {
            dismissActiveDialogs()
        }
    }

    internal fun dialogSnapshot(): DialogSnapshot? = activeDialogs?.let { state ->
        DialogSnapshot(
            main = state.main,
            ecosystem = state.ecosystem,
            mainFocusIndex = state.mainFocusIndex,
            ecosystemFocusIndex = state.ecosystemFocusIndex
        )
    }

    private fun createDialog(
        context: Context,
        content: @Composable (Dialog) -> Unit
    ): Dialog {
        val dialog = ComponentDialog(context, R.style.AppComposeDialogStyle)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { content(dialog) }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        AppDialogStyler.installDismissKeys(dialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun applyDialogWidth(
        dialog: Dialog,
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

    private fun currentLocale(context: Context): Locale {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0]
            ?: Locale.getDefault()
    }

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

    private fun ecosystemProjects(context: Context): List<EcosystemProject> {
        fun s(@StringRes id: Int) = context.getString(id)
        return listOf(
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_sunshine_badge),
                s(R.string.about_dialog_ecosystem_sunshine_title),
                s(R.string.about_dialog_ecosystem_sunshine_platform),
                s(R.string.about_dialog_ecosystem_sunshine_description),
                FOUNDATION_SUNSHINE_URL
            ),
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_pc_badge),
                s(R.string.about_dialog_ecosystem_pc_title),
                s(R.string.about_dialog_ecosystem_pc_platform),
                s(R.string.about_dialog_ecosystem_pc_description),
                MOONLIGHT_PC_URL
            ),
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_vplus_badge),
                s(R.string.about_dialog_ecosystem_vplus_title),
                s(R.string.about_dialog_ecosystem_vplus_platform),
                s(R.string.about_dialog_ecosystem_vplus_description),
                MOONLIGHT_VPLUS_URL
            ),
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_voidlink_badge),
                s(R.string.about_dialog_ecosystem_voidlink_title),
                s(R.string.about_dialog_ecosystem_voidlink_platform),
                s(R.string.about_dialog_ecosystem_voidlink_description),
                VOIDLINK_URL
            ),
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_harmony_badge),
                s(R.string.about_dialog_ecosystem_harmony_title),
                s(R.string.about_dialog_ecosystem_harmony_platform),
                s(R.string.about_dialog_ecosystem_harmony_description),
                HARMONY_URL
            ),
            EcosystemProject(
                s(R.string.about_dialog_ecosystem_mac_badge),
                s(R.string.about_dialog_ecosystem_mac_title),
                s(R.string.about_dialog_ecosystem_mac_platform),
                s(R.string.about_dialog_ecosystem_mac_description),
                MOONLIGHT_MAC_URL
            )
        )
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

    private fun dpToPx(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
