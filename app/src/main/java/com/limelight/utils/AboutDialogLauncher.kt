package com.limelight.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import com.limelight.R
import com.limelight.handbook.HandbookLauncher

object AboutDialogLauncher {
    private const val OFFICIAL_SITE_URL = "https://www.alkaidlab.com/"
    private const val GITHUB_URL = "https://github.com/qiin2333/moonlight-vplus"
    private const val QQ_GROUP_KEY = "LlbLDIF_YolaM4HZyLx0xAXXo04ZmoBM"

    fun show(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_about, null)

        dialogView.findViewById<TextView>(R.id.text_version).text =
            getVersionInfo(activity)
        dialogView.findViewById<TextView>(R.id.text_app_name).text =
            getAppName(activity)
        dialogView.findViewById<TextView>(R.id.text_description)
            .setText(R.string.about_dialog_description)

        // PcView is a framework Activity, so keep the system dialog theme that
        // works without requiring an AppCompat host theme on older Android versions.
        val dialog = AlertDialog.Builder(
            activity,
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setView(dialogView)
            .setPositiveButton(R.string.about_dialog_official_site) { _, _ ->
                BrowserOnlyLauncher.open(activity, OFFICIAL_SITE_URL)
            }
            .setNeutralButton(R.string.about_dialog_github) { _, _ ->
                BrowserOnlyLauncher.open(activity, GITHUB_URL)
            }
            .setNegativeButton(R.string.about_dialog_qq) { _, _ ->
                openQqGroup(activity)
            }
            .create()

        dialogView.findViewById<View>(R.id.about_handbook_button).setOnClickListener {
            dialog.dismiss()
            HandbookLauncher.openIndex(activity)
        }
        dialog.show()
        AppDialogStyler.applyAboutDialog(dialog, activity)
    }

    @SuppressLint("DefaultLocale")
    private fun getVersionInfo(activity: Activity): String {
        return try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            String.format(
                "Version %s (Build %d)",
                info.versionName,
                PackageInfoCompat.getLongVersionCode(info)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            "Version Unknown"
        }
    }

    private fun getAppName(activity: Activity): String {
        return try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            info.applicationInfo
                ?.loadLabel(activity.packageManager)
                ?.toString()
                ?: "Moonlight V+"
        } catch (_: PackageManager.NameNotFoundException) {
            "Moonlight V+"
        }
    }

    private fun openQqGroup(activity: Activity) {
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
        BrowserOnlyLauncher.open(activity, url.toString())
    }
}
