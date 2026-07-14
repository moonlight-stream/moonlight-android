@file:Suppress("DEPRECATION")
package com.limelight.preferences

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.preference.Preference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView

import com.limelight.R
import com.limelight.utils.AppDialogStyler

class AboutDialogPreference : Preference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    @Deprecated("Deprecated in Java")
    override fun onClick() {
        showAboutDialog()
    }

    private fun showAboutDialog() {
        val context = context

        // 创建自定义布局
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about, null)

        // 设置版本信息
        val versionText = dialogView.findViewById<TextView>(R.id.text_version)
        versionText.text = getVersionInfo(context)

        // 设置应用名称
        val appNameText = dialogView.findViewById<TextView>(R.id.text_app_name)
        appNameText.text = getAppName(context)

        // 设置描述信息
        val descriptionText = dialogView.findViewById<TextView>(R.id.text_description)
        descriptionText.setText(R.string.about_dialog_description)

        // 创建对话框
        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)

        builder.setPositiveButton(R.string.about_dialog_github) { _, _ ->
            openUrl(GITHUB_REPO_URL)
        }
        builder.setNeutralButton(R.string.about_dialog_star) { _, _ ->
            openUrl(GITHUB_STAR_URL)
        }
        builder.setNegativeButton(R.string.about_dialog_close) { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        AppDialogStyler.applyAboutDialog(dialog, context)
    }

    @SuppressLint("DefaultLocale")
    private fun getVersionInfo(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            String.format("Version %s (Build %d)", packageInfo.versionName, androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            "Version Unknown"
        }
    }

    private fun getAppName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: "Moonlight V+"
        } catch (e: PackageManager.NameNotFoundException) {
            "Moonlight V+"
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 如果无法打开链接，忽略错误
        }
    }

    companion object {
        private const val GITHUB_REPO_URL = "https://github.com/qiin2333/moonlight-vplus"
        private const val GITHUB_STAR_URL = "https://github.com/qiin2333/moonlight-vplus/stargazers"
    }
}
