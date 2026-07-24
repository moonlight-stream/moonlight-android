@file:Suppress("DEPRECATION")
package com.limelight.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Proxy
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

import com.limelight.R
import com.limelight.handbook.HandbookLauncher

import org.json.JSONObject

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.edit
import androidx.core.net.toUri

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_API_URL = "https://api.github.com/repos/qiin2333/moonlight-vplus/releases/latest"
    private const val GITHUB_RELEASE_PAGE = "https://github.com/qiin2333/moonlight-vplus/releases/latest"
    private const val UPDATE_CHECK_INTERVAL = 4 * 60 * 60 * 1000L

    // 代理发现地址
    private const val PROXY_DISCOVERY_URL = "https://ghproxy.link/js/src_views_home_HomeView_vue.js"

    // API与下载的代理前缀（按优先级尝试）- 将在运行时动态更新
    @Volatile
    private var PROXY_PREFIXES: Array<String> = emptyArray()

    private val isChecking = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // 代理缓存相关
    private const val PROXY_CACHE_DURATION = 24 * 60 * 60 * 1000L // 24小时
    private const val PREF_LAST_PROXY_UPDATE_TIME = "last_proxy_update_time"

    // SharedPreferences 中存储下载信息的 key
    private const val PREF_DOWNLOAD_ID = "update_download_id"
    private const val PREF_DOWNLOAD_APK_NAME = "update_download_apk_name"
    // 预期 SHA256（从 release body 提取），下载完成后用于完整性校验
    private const val PREF_DOWNLOAD_SHA256 = "update_download_sha256"
    private const val PREF_DOWNLOAD_EXPECTED_SIZE = "update_download_expected_size"
    // 用户主动跳过的版本：同名版本下次启动检查不弹对话框（手动检查仍会弹）
    private const val PREF_SKIPPED_VERSION = "update_skipped_version"

    // 安装权限请求码（供 Activity 的 onActivityResult 使用）
    const val INSTALL_PERMISSION_REQUEST_CODE = 9527

    // 等待安装权限授予后再执行下载的暂存信息
    @Volatile
    private var pendingUpdateInfo: UpdateInfo? = null

    // 下载进度轮询间隔
    private const val PROGRESS_POLL_INTERVAL_MS = 300L

    // 当前进度对话框与 handler（用于在 Activity 销毁时清理）
    private var currentProgressDialog: AlertDialog? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------

    fun checkForUpdates(context: Context, showToast: Boolean) {
        if (isChecking.getAndSet(true)) {
            // 连点防抖：手动检查时反馈 toast，避免用户以为点了没反应
            if (showToast) {
                Toast.makeText(context, context.getString(R.string.toast_check_update_in_progress), Toast.LENGTH_SHORT).show()
            }
            return
        }
        executor.execute(UpdateCheckTask(context, showToast))
    }

    fun checkForUpdatesOnStartup(context: Context) {
        val lastCheckTime = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                .getLong("last_check_time", 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastCheckTime > UPDATE_CHECK_INTERVAL) {
            checkForUpdates(context, false)
        }
    }

    /**
     * 当用户在系统设置中授予安装权限后由 Activity.onActivityResult 调用。
     * 如果之前有暂存的更新信息且权限已授予，则自动开始下载。
     */
    fun onInstallPermissionResult(context: Context) {
        if (pendingUpdateInfo != null && canInstallApk(context)) {
            val info = pendingUpdateInfo
            pendingUpdateInfo = null
            startDirectDownload(context, info!!)
        } else if (pendingUpdateInfo != null) {
            pendingUpdateInfo = null
            Toast.makeText(context, context.getString(R.string.toast_install_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 由 [UpdateDownloadReceiver] 在下载完成时调用。
     * 也会由应用内进度轮询在检测到完成时调用。
     */
    fun onDownloadComplete(context: Context, completedDownloadId: Long) {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val savedDownloadId = prefs.getLong(PREF_DOWNLOAD_ID, -1)

        if (savedDownloadId == -1L || savedDownloadId != completedDownloadId) {
            return // 不是我们的下载
        }

        val apkName = prefs.getString(PREF_DOWNLOAD_APK_NAME, "update.apk")
        val expectedSha256 = prefs.getString(PREF_DOWNLOAD_SHA256, null)
        val expectedSize = prefs.getLong(PREF_DOWNLOAD_EXPECTED_SIZE, -1L)
        val candidates = splitStrings(prefs.getString("update_download_candidates", null))
        val candidateIndex = prefs.getInt("update_download_candidate_index", 0)

        // 清除已保存的下载信息
        prefs.edit {
            remove(PREF_DOWNLOAD_ID)
                .remove(PREF_DOWNLOAD_APK_NAME)
                .remove(PREF_DOWNLOAD_SHA256)
                .remove(PREF_DOWNLOAD_EXPECTED_SIZE)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return

        // 检查下载是否成功
        val query = DownloadManager.Query()
        query.setFilterById(completedDownloadId)
        dm.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val downloadedUri = dm.getUriForDownloadedFile(completedDownloadId)
                        if (downloadedUri == null) {
                            Log.w(TAG, "Cannot get downloaded APK URI")
                            try { dm.remove(completedDownloadId) } catch (_: Exception) {}
                            Toast.makeText(context, context.getString(R.string.toast_cannot_get_download_file), Toast.LENGTH_LONG).show()
                            return
                        }
                        val invalidReason = validateDownloadedApk(context, downloadedUri, expectedSize, expectedSha256)
                        if (invalidReason != null) {
                            Log.w(TAG, "Downloaded update APK failed validation: $invalidReason")
                            if (retryNextDownloadSource(context, dm, completedDownloadId, apkName ?: "update.apk", expectedSha256, expectedSize, candidates, candidateIndex)) {
                                return
                            }
                            Toast.makeText(context, context.getString(R.string.toast_update_integrity_mismatch), Toast.LENGTH_LONG).show()
                            return
                        }
                        Log.d(TAG, "下载成功，准备安装: $apkName")
                        installApk(context, completedDownloadId)
                        return
                    }
                }
            }
        }

        Log.w(TAG, "下载可能失败，downloadId=$completedDownloadId")
    }

    /**
     * 清理进度对话框资源（在 Activity 销毁时调用）
     */
    fun cleanup() {
        dismissProgressDialog()
        if (progressHandler != null && progressRunnable != null) {
            progressHandler!!.removeCallbacks(progressRunnable!!)
        }
    }

    // ------------------------------------------------------------------
    // 更新检查
    // ------------------------------------------------------------------

    private class UpdateCheckTask(private val context: Context, private val showToast: Boolean) : Runnable {

        override fun run() {
            var updateInfo: UpdateInfo? = null
            var releaseHandled = false
            try {
                if (shouldUpdateProxyList(context)) {
                    updateProxyList(context)
                }

                try {
                    val json = httpGetWithProxies(context, GITHUB_API_URL)
                    if (json != null) {
                        val jsonResponse = JSONObject(json)
                        val latestVersion = jsonResponse.optString("tag_name", "").replaceFirst("^[Vv]".toRegex(), "")
                        val releaseNotes = jsonResponse.optString("body", "")
                        val releasePageUrl = jsonResponse.optString("html_url", "").takeIf { it.isNotBlank() }
                            ?: GITHUB_RELEASE_PAGE.removeSuffix("/latest") + "/tag/v$latestVersion"

                        // 解析资产，优先选择APK
                        var apkUrl: String? = null
                        var apkName: String? = null
                        var apkAsset: JSONObject? = null
                        val assets = jsonResponse.optJSONArray("assets")
                        if (assets != null) {
                            val apkAssets = ArrayList<JSONObject>()
                            for (i in 0 until assets.length()) {
                                val a = assets.optJSONObject(i)
                                if (a != null) {
                                    val name = a.optString("name", "")
                                    val url = a.optString("browser_download_url", "")
                                    if (name.endsWith(".apk") && url.startsWith("http")) {
                                        apkAssets.add(a)
                                    }
                                }
                            }
                            // 优先匹配root/nonRoot
                            for (a in apkAssets) {
                                val name = a.optString("name", "")
                                val isRootApk = name.lowercase().contains("root")
                                if (!isRootApk) {
                                    apkName = name
                                    apkUrl = a.opt("browser_download_url") as? String
                                    apkAsset = a
                                    break
                                }
                            }
                            // 若没匹配到，退而求其次取第一个APK
                            if (apkUrl == null && apkAssets.isNotEmpty()) {
                                val a = apkAssets[0]
                                apkName = a.opt("name") as? String
                                apkUrl = a.opt("browser_download_url") as? String
                                apkAsset = a
                            }
                        }

                        val assetSha256 = extractSha256FromDigest(apkAsset?.optString("digest"))
                        val sha256 = assetSha256 ?: extractSha256ForApk(releaseNotes, apkName)
                        val expectedSize = apkAsset?.optLong("size", -1L)?.takeIf { it > 0L }
                        updateInfo = UpdateInfo(latestVersion, releaseNotes, releasePageUrl, apkName, apkUrl, sha256, expectedSize)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "检查更新失败", e)
                }

                val finalUpdateInfo = updateInfo
                val runner = Runnable {
                    try { handleUpdateResult(finalUpdateInfo) }
                    finally { isChecking.set(false) }
                }

                if (context is Activity) {
                    if (context.isFinishing || context.isDestroyed) {
                        // 页面已销毁，不能跳 UI；仅走 SP 写入逻辑并释放锁
                        runner.run()
                    } else {
                        context.runOnUiThread(runner)
                    }
                } else {
                    runner.run()
                }
                releaseHandled = true
            } finally {
                // 其他异常路径兑底，保证 isChecking 不会被永久占据
                if (!releaseHandled) {
                    isChecking.set(false)
                }
            }
        }

        private fun handleUpdateResult(updateInfo: UpdateInfo?) {
            // 仅在检查成功时写入 lastCheckTime，避免失败后 4 小时内拒不重试
            if (updateInfo != null) {
                context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                        .edit {
                            putLong("last_check_time", System.currentTimeMillis())
                        }
            }

            if (updateInfo == null) {
                if (showToast) {
                    Toast.makeText(context, context.getString(R.string.toast_check_update_failed), Toast.LENGTH_SHORT).show()
                }
                return
            }

            val currentVersion = getCurrentVersion(context)
            if (isNewVersionAvailable(currentVersion, updateInfo.version)) {
                // 后台启动检查时如果用户已跳过该版本，不要打扰；手动检查仍弹
                if (!showToast) {
                    val skipped = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                            .getString(PREF_SKIPPED_VERSION, null)
                    if (skipped != null && skipped == updateInfo.version) {
                        Log.d(TAG, "用户已跳过版本 $skipped，启动检查不弹窗")
                        return
                    }
                }
                showUpdateDialog(context, updateInfo)
            } else if (showToast) {
                showLatestVersionDialog(context, currentVersion, updateInfo.releaseNotes, updateInfo.releasePageUrl)
            }
        }
    }

    // ------------------------------------------------------------------
    // 对话框
    // ------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun showLatestVersionDialog(
        context: Context,
        currentVersion: String,
        releaseNotes: String?,
        releasePageUrl: String?
    ) {
        if (context !is Activity) {
            Toast.makeText(context, context.getString(R.string.toast_already_latest_version, currentVersion), Toast.LENGTH_SHORT).show()
            return
        }

        context.runOnUiThread {
            val builder = AlertDialog.Builder(context, R.style.AppDialogStyle)
            builder.setTitle(context.getString(R.string.update_already_latest_title))

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)

            val version = view.findViewById<TextView>(R.id.update_version)
            version.text = "v$currentVersion"

            if (releaseNotes != null && releaseNotes.trim().isNotEmpty()) {
                val accentColor = ContextCompat.getColor(context, R.color.app_dialog_accent_color)
                val notesScroll = view.findViewById<ScrollView>(R.id.update_notes_scroll)
                notesScroll.visibility = View.VISIBLE
                val notes = view.findViewById<TextView>(R.id.update_notes)
                notes.text = SimpleMarkdownRenderer.render(
                    releaseNotes,
                    accentColor,
                    releasePageUrl,
                    onLink = { url ->
                        openReleaseNoteLink(context, url)
                    }
                )
                configureUpdateNotesLinks(notes, accentColor)
                constrainUpdateNotesScroll(context, notesScroll, releaseNotes)
            }

            builder.setView(view)
            builder.setPositiveButton(context.getString(R.string.update_btn_got_it), null)
            builder.setCancelable(true)
            val dialog = builder.show()
            AppDialogStyler.tintTitle(dialog, context)
        }
    }

    private fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        if (context !is Activity) {
            return
        }

        context.runOnUiThread {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)

            val curVer = getCurrentVersion(context)
            val version = view.findViewById<TextView>(R.id.update_version)
            version.text = "v$curVer → v${updateInfo.version}"

            if (!updateInfo.releaseNotes.isNullOrEmpty()) {
                val accentColor = ContextCompat.getColor(context, R.color.app_dialog_accent_color)
                val notesScroll = view.findViewById<ScrollView>(R.id.update_notes_scroll)
                notesScroll.visibility = View.VISIBLE
                val notesView = view.findViewById<TextView>(R.id.update_notes)
                notesView.text = SimpleMarkdownRenderer.render(
                    updateInfo.releaseNotes,
                    accentColor,
                    updateInfo.releasePageUrl,
                    onLink = { url ->
                        openReleaseNoteLink(context, url)
                    }
                )
                configureUpdateNotesLinks(notesView, accentColor)
                constrainUpdateNotesScroll(context, notesScroll, updateInfo.releaseNotes)
            }

            if (updateInfo.apkName != null) {
                val fileName = view.findViewById<TextView>(R.id.update_file_name)
                fileName.text = updateInfo.apkName
                fileName.visibility = View.VISIBLE
            }

            val builder = AlertDialog.Builder(context, R.style.AppDialogStyle)
            builder.setTitle(context.getString(R.string.update_new_version_title))
            builder.setView(view)

            builder.setPositiveButton(context.getString(R.string.update_btn_browser_download)) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, GITHUB_RELEASE_PAGE.toUri())
                context.startActivity(intent)
            }

            if (updateInfo.apkDownloadUrl != null) {
                builder.setNeutralButton(context.getString(R.string.update_btn_direct_download)) { _, _ ->
                    if (!canInstallApk(context)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            showInstallPermissionDialog(context, updateInfo)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_cannot_get_install_permission),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        startDirectDownload(context, updateInfo)
                    }
                }
            }

            builder.setNegativeButton(context.getString(R.string.update_btn_later), null)
            builder.setCancelable(true)
            val dialog = builder.show()
            AppDialogStyler.tintTitle(dialog, context)
            // “稍后”按钮长按 = 跳过此版本（启动检查不再弹，手动检查仍弹）
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnLongClickListener {
                context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                        .edit { putString(PREF_SKIPPED_VERSION, updateInfo.version) }
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_update_skipped_version, updateInfo.version),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                true
            }
        }
    }

    // ------------------------------------------------------------------
    // 安装权限
    // ------------------------------------------------------------------

    private fun canInstallApk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.packageManager.canRequestPackageInstalls()
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun showInstallPermissionDialog(context: Context, info: UpdateInfo) {
        // 暂存更新信息，等权限授予后恢复
        pendingUpdateInfo = info

        if (context !is Activity) {
            Toast.makeText(context, context.getString(R.string.toast_need_install_permission), Toast.LENGTH_LONG).show()
            return
        }

        val builder = AlertDialog.Builder(context, R.style.AppDialogStyle)
        builder.setTitle(context.getString(R.string.update_install_permission_title))
        builder.setMessage(context.getString(R.string.update_install_permission_msg))
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = ("package:" + context.packageName).toUri()
                @Suppress("DEPRECATION")
                context.startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    @Suppress("DEPRECATION")
                    context.startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE)
                } catch (e2: Exception) {
                    pendingUpdateInfo = null
                    Toast.makeText(context, context.getString(R.string.toast_cannot_open_settings), Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { _, _ -> pendingUpdateInfo = null }
        builder.setCancelable(false)
        val dialog = builder.show()
        AppDialogStyler.apply(dialog, context)
    }

    // ------------------------------------------------------------------
    // 下载 APK
    // ------------------------------------------------------------------

    private fun startDirectDownload(context: Context, info: UpdateInfo) {
        try {
            val src = info.apkDownloadUrl!!
            val fileName = info.apkName ?: ("moonlight-" + info.version + ".apk")

            // Prefer the original GitHub asset URL. User gateway proxies/VPNs already
            // intercept this path, and wrapping it in a GitHub proxy can return HTML.
            val candidates = ArrayList<String>()
            candidates.add(src)
            for (p in getEffectiveProxyPrefixes(context)) {
                candidates.add(p + src)
            }
            candidates.add(src) // 直连兜底

            // 使用第一个候选 URL 开始下载
            val downloadCandidates = candidates.distinct()
            val primaryUrl = downloadCandidates[0]

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm == null) {
                Toast.makeText(context, context.getString(R.string.toast_download_service_unavailable), Toast.LENGTH_SHORT).show()
                return
            }

            val downloadId = dm.enqueue(createUpdateDownloadRequest(context, primaryUrl, fileName))
            Log.d(TAG, "已启动下载，ID: $downloadId, URL: $primaryUrl")

            // 保存下载信息到 SharedPreferences（供 BroadcastReceiver 和代理重试使用）
            val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putLong(PREF_DOWNLOAD_ID, downloadId)
                    .putString(PREF_DOWNLOAD_APK_NAME, fileName)
                    // 保存完整候选 URL 列表用于重试
                    .putString("update_download_candidates", joinStrings(downloadCandidates))
                    .putInt("update_download_candidate_index", 0)
                if (info.expectedSha256 != null) {
                    putString(PREF_DOWNLOAD_SHA256, info.expectedSha256)
                } else {
                    remove(PREF_DOWNLOAD_SHA256)
                }
                if (info.expectedSize != null) {
                    putLong(PREF_DOWNLOAD_EXPECTED_SIZE, info.expectedSize)
                } else {
                    remove(PREF_DOWNLOAD_EXPECTED_SIZE)
                }
            }

            // 如果当前在 Activity 中，显示应用内进度对话框
            if (context is Activity) {
                showDownloadProgressDialog(context, downloadId, dm, downloadCandidates, fileName)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_download_started), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            Toast.makeText(context, context.getString(R.string.toast_download_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------
    // 应用内下载进度对话框
    // ------------------------------------------------------------------

    private fun showDownloadProgressDialog(activity: Activity, downloadId: Long,
                                           dm: DownloadManager,
                                           candidates: List<String>, fileName: String) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_download_progress, null)

        val progressBar = view.findViewById<ProgressBar>(R.id.download_progress_bar)
        val progressText = view.findViewById<TextView>(R.id.download_progress_text)

        val dialog = AlertDialog.Builder(activity, R.style.AppDialogStyle)
                .setTitle(activity.getString(R.string.update_downloading_title))
                .setView(view)
                .setNegativeButton(activity.getString(R.string.update_btn_background)) { _, _ ->
                    Toast.makeText(activity, activity.getString(R.string.toast_download_continue_bg), Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .create()

        dialog.show()
        AppDialogStyler.tintTitle(dialog, activity)
        currentProgressDialog = dialog

        // 使用 Handler 轮询下载进度
        val handler = Handler(Looper.getMainLooper())
        progressHandler = handler
        val currentDownloadId = longArrayOf(downloadId)
        val currentCandidateIndex = intArrayOf(0)

        val runnable = object : Runnable {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    dismissProgressDialog()
                    return
                }

                val query = DownloadManager.Query()
                query.setFilterById(currentDownloadId[0])

                try {
                    dm.query(query)?.use { cursor ->
                        if (!cursor.moveToFirst()) {
                            // 下载可能已被取消
                            dismissProgressDialog()
                            return
                        }

                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                        if (statusIndex < 0) {
                            dismissProgressDialog()
                            return
                        }

                        val status = cursor.getInt(statusIndex)
                        val bytesDownloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                        val bytesTotal = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else -1L

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                if (bytesTotal > 0) {
                                    val progress = (bytesDownloaded * 100 / bytesTotal).toInt()
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = progress
                                    val downloadedMB = String.format("%.1f", bytesDownloaded / 1048576.0)
                                    val totalMB = String.format("%.1f", bytesTotal / 1048576.0)
                                    progressText.text = "$downloadedMB MB / $totalMB MB ($progress%)"
                                } else {
                                    progressBar.isIndeterminate = true
                                    val downloadedMB = String.format("%.1f", bytesDownloaded / 1048576.0)
                                    progressText.text = activity.getString(R.string.update_progress_downloaded, downloadedMB)
                                }
                                handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
                            }

                            DownloadManager.STATUS_PENDING -> {
                                progressBar.isIndeterminate = true
                                progressText.text = activity.getString(R.string.update_progress_waiting)
                                handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
                            }

                            DownloadManager.STATUS_PAUSED -> {
                                progressText.text = activity.getString(R.string.update_progress_paused)
                                handler.postDelayed(this, 1000)
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {
                                progressBar.progress = 100
                                progressText.text = activity.getString(R.string.update_progress_installing)
                                dismissProgressDialog()
                                // 触发安装
                                onDownloadComplete(activity, currentDownloadId[0])
                            }

                            DownloadManager.STATUS_FAILED -> {
                                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                                Log.w(TAG, "下载失败, reason=$reason, candidateIndex=${currentCandidateIndex[0]}")

                                // 尝试下一个代理
                                currentCandidateIndex[0]++
                                if (currentCandidateIndex[0] < candidates.size) {
                                    dm.remove(currentDownloadId[0])
                                    val nextUrl = candidates[currentCandidateIndex[0]]
                                    Log.d(TAG, "尝试备用下载链接: $nextUrl")
                                    progressText.text = activity.getString(R.string.update_progress_switching_source)

                                    try {
                                        val newDownloadId = dm.enqueue(createUpdateDownloadRequest(activity, nextUrl, fileName))
                                        currentDownloadId[0] = newDownloadId

                                        // 更新 SharedPreferences 中的下载 ID
                                        val prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                                        prefs.edit {
                                            putLong(PREF_DOWNLOAD_ID, newDownloadId)
                                                .putInt(
                                                    "update_download_candidate_index",
                                                    currentCandidateIndex[0]
                                                )
                                        }

                                        handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "备用下载也失败", e)
                                        dismissProgressDialog()
                                        Toast.makeText(activity, activity.getString(R.string.toast_all_sources_failed), Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    dismissProgressDialog()
                                    Toast.makeText(activity, activity.getString(R.string.toast_download_failed_try_browser), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } ?: run {
                        // cursor is null
                        dismissProgressDialog()
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "查询下载进度失败", e)
                    handler.postDelayed(this, 1000)
                }
            }
        }

        progressRunnable = runnable
        handler.post(runnable)
    }

    private fun dismissProgressDialog() {
        if (currentProgressDialog != null && currentProgressDialog!!.isShowing) {
            try {
                currentProgressDialog!!.dismiss()
            } catch (ignored: Exception) {
            }
        }
        currentProgressDialog = null
    }

    // ------------------------------------------------------------------
    // APK 安装
    // ------------------------------------------------------------------

    private fun installApk(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return

            val downloadedUri = dm.getUriForDownloadedFile(downloadId)
            if (downloadedUri == null) {
                Log.w(TAG, "无法获取下载文件 URI")
                Toast.makeText(context, context.getString(R.string.toast_cannot_get_download_file), Toast.LENGTH_LONG).show()
                return
            }

            val installIntent = Intent(Intent.ACTION_VIEW)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 content:// URI
                // DownloadManager.getUriForDownloadedFile 在 N+ 已经返回 content:// URI
                installIntent.setDataAndType(downloadedUri, "application/vnd.android.package-archive")
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                installIntent.setDataAndType(downloadedUri, "application/vnd.android.package-archive")
            }

            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(installIntent)

            Log.d(TAG, "已启动安装界面")
        } catch (e: Exception) {
            Log.e(TAG, "启动安装失败", e)
            Toast.makeText(context, context.getString(R.string.toast_install_launch_failed), Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------
    // 版本比较
    // ------------------------------------------------------------------

    private fun getCurrentVersion(context: Context): String {
        try {
            val packageInfo = context.packageManager
                    .getPackageInfo(context.packageName, 0)
            return packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            Log.e(TAG, "获取当前版本失败", e)
            return "0.0.0"
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        try {
            return compareVersions(latestVersion, currentVersion) > 0
        } catch (e: Exception) {
            Log.e(TAG, "版本号格式错误: current=$currentVersion, latest=$latestVersion", e)
            return false
        }
    }

    private fun compareVersions(leftVersion: String, rightVersion: String): Int {
        val left = parseVersion(leftVersion)
        val right = parseVersion(rightVersion)

        val numberCount = left.numbers.size.coerceAtLeast(right.numbers.size)
        for (i in 0 until numberCount) {
            val leftNumber = left.numbers.getOrElse(i) { 0 }
            val rightNumber = right.numbers.getOrElse(i) { 0 }
            if (leftNumber != rightNumber) {
                return leftNumber.compareTo(rightNumber)
            }
        }

        if (left.qualifierRank != right.qualifierRank) {
            return left.qualifierRank.compareTo(right.qualifierRank)
        }

        val qualifierNumberCount = left.qualifierNumbers.size.coerceAtLeast(right.qualifierNumbers.size)
        for (i in 0 until qualifierNumberCount) {
            val leftNumber = left.qualifierNumbers.getOrElse(i) { 0 }
            val rightNumber = right.qualifierNumbers.getOrElse(i) { 0 }
            if (leftNumber != rightNumber) {
                return leftNumber.compareTo(rightNumber)
            }
        }

        return 0
    }

    private fun parseVersion(version: String): ParsedVersion {
        val normalized = version.trim()
                .replaceFirst("^[Vv]".toRegex(), "")
                .substringBefore('+')
        val qualifierStart = normalized.indexOfFirst { it == '-' || it == '_' || it.isWhitespace() }
        val numberPart = if (qualifierStart >= 0) normalized.substring(0, qualifierStart) else normalized
        val qualifierPart = if (qualifierStart >= 0) normalized.substring(qualifierStart + 1) else ""
        val numbers = numberPart.split('.')
                .map { part -> part.toIntOrNull() ?: 0 }
                .ifEmpty { listOf(0) }
        val qualifierTokens = qualifierPart
                .lowercase()
                .split('.', '-', '_', ' ')
                .filter { it.isNotBlank() }
        val qualifierName = qualifierTokens.firstOrNull { token -> token.any { it.isLetter() } } ?: ""
        val qualifierRank = when (qualifierName) {
            "hotfix", "fix", "patch" -> 1
            "", "stable", "release", "final" -> 0
            "rc" -> -1
            "beta" -> -2
            "alpha" -> -3
            else -> -1
        }
        val qualifierNumbers = qualifierTokens.mapNotNull { token ->
            "\\d+".toRegex().find(token)?.value?.toIntOrNull()
        }

        return ParsedVersion(numbers, qualifierRank, qualifierNumbers)
    }

    private data class ParsedVersion(
            val numbers: List<Int>,
            val qualifierRank: Int,
            val qualifierNumbers: List<Int>
    )

    // ------------------------------------------------------------------
    // 代理相关（保持原有逻辑）
    // ------------------------------------------------------------------

    private fun shouldUpdateProxyList(context: Context): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastUpdateTime = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                .getLong(PREF_LAST_PROXY_UPDATE_TIME, 0)
        return (currentTime - lastUpdateTime) > PROXY_CACHE_DURATION || PROXY_PREFIXES.isEmpty()
    }

    private fun updateProxyList(context: Context) {
        try {
            Log.d(TAG, "开始更新代理列表...")
            val scriptContent = fetchScriptContent()
            if (scriptContent != null) {
                val newProxies = extractProxiesFromScript(scriptContent)
                if (newProxies.isNotEmpty()) {
                    val allProxies = HashSet(PROXY_PREFIXES.toList())
                    allProxies.addAll(newProxies)

                    PROXY_PREFIXES = allProxies.toTypedArray()

                    context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                            .edit {
                                putLong(PREF_LAST_PROXY_UPDATE_TIME, System.currentTimeMillis())
                            }

                    Log.d(TAG, "代理列表已更新，共 ${PROXY_PREFIXES.size} 个代理：${PROXY_PREFIXES.contentToString()}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "更新代理列表失败: ${e.message}")
        }
    }

    private fun fetchScriptContent(): String? {
        try {
            val url = URL(PROXY_DISCOVERY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)")
            conn.connectTimeout = 2000
            conn.readTimeout = 2000

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val content = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    content.append(line).append("\n")
                }
                reader.close()
                return content.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取代理发现脚本失败: ${e.message}")
        }
        return null
    }

    private fun extractProxiesFromScript(scriptContent: String): Array<String> {
        val proxies = ArrayList<String>()

        try {
            val patterns = arrayOf(
                    "[\"']https://[\\w.-]+\\.(?:com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|design|cloud)/[\"']",
                    "baseUrl\\s*=\\s*[\"']https://[\\w.-]+\\.(?:com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|design|cloud)/[\"']",
                    "url:\\s*[\"']https://[\\w.-]+\\.(?:com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|design|cloud)/[\"']",
                    "[\"']https://(?:gh|mirror|proxy|cdn)[\\w.-]*\\.(?:com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|design|cloud)/[\"']"
            )

            for (patternStr in patterns) {
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(scriptContent)

                while (matcher.find()) {
                    val match = matcher.group()
                    var urlStr = match.replace("[\"']".toRegex(), "")

                    if (urlStr.startsWith("https://")) {
                        if (!urlStr.endsWith("/")) {
                            urlStr = "$urlStr/"
                        }
                        if (isValidProxyUrl(urlStr)) {
                            proxies.add(urlStr)
                            Log.d(TAG, "发现代理地址: $urlStr")
                        }
                    }
                }
            }

            val domainPattern = Pattern.compile("(?:proxy|mirror|gh|cdn)[\\w.-]*\\.(?:com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|design|cloud)", Pattern.CASE_INSENSITIVE)
            val domainMatcher = domainPattern.matcher(scriptContent)

            while (domainMatcher.find()) {
                val domain = domainMatcher.group()
                val proxyUrl = "https://$domain/"
                if (isValidProxyUrl(proxyUrl)) {
                    proxies.add(proxyUrl)
                    Log.d(TAG, "发现域名代理: $proxyUrl")
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "解析代理地址失败: ${e.message}")
        }

        val uniqueProxies = HashSet(proxies)
        return uniqueProxies.toTypedArray()
    }

    private fun isValidProxyUrl(url: String?): Boolean {
        if (url == null || url.length < 15 || url.length > 100) {
            return false
        }

        val blacklist = arrayOf(
                "github.com", "googleapis.com", "gstatic.com",
                "jquery.com", "bootstrap.com", "cdnjs.com",
                "unpkg.com", "jsdelivr.net", "ghproxy.link"
        )

        for (blocked in blacklist) {
            if (url.contains(blocked)) {
                return false
            }
        }

        return !detectRedirectToGhproxyLink(url)
    }

    private fun detectRedirectToGhproxyLink(proxyUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        try {
            val testUrl = proxyUrl + "https://api.github.com/zen"
            conn = URL(testUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)")
            conn.connectTimeout = 2000
            conn.readTimeout = 2000

            val responseCode = conn.responseCode

            if (responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                if (location != null && location.contains("ghproxy.link")) {
                    Log.d(TAG, "代理重定向回 ghproxy.link，排除: $proxyUrl")
                    return true
                }
            }

            if (conn.url.toString().contains("ghproxy.link")) {
                Log.d(TAG, "代理最终URL包含 ghproxy.link，排除: $proxyUrl")
                return true
            }

            val server = conn.getHeaderField("Server")
            if (server != null && server.lowercase().contains("ghproxy")) {
                Log.d(TAG, "代理服务器信息包含 ghproxy，排除: $proxyUrl")
                return true
            }

            Log.d(TAG, "代理检测通过: $proxyUrl (响应码: $responseCode)")
            return false

        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "代理连接失败，排除: $proxyUrl - ${e.message}")
            return true
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "代理连接失败，排除: $proxyUrl - ${e.message}")
            return true
        } catch (e: Exception) {
            Log.d(TAG, "代理检测异常但不排除: $proxyUrl - ${e.message}")
            return false
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpGetWithProxies(context: Context, url: String): String? {
        val tries = buildProxiedUrls(context, url).take(6)
        if (tries.isEmpty()) return null
        // 并发竞速：同时发起多个候选（代理 + 直连），取首个成功响应
        val pool = Executors.newFixedThreadPool(tries.size)
        val cs = ExecutorCompletionService<String?>(pool)
        val futures = ArrayList<Future<String?>>()
        try {
            for (u in tries) {
                futures.add(cs.submit(Callable { fetchSingleUrl(u) }))
            }
            val deadline = System.currentTimeMillis() + 6000L // 总超时 6s
            for (i in tries.indices) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                val f = cs.poll(remaining, TimeUnit.MILLISECONDS) ?: break
                val res = try { f.get() } catch (e: Exception) { null }
                if (res != null) {
                    return res
                }
            }
            return null
        } finally {
            for (f in futures) f.cancel(true)
            pool.shutdownNow()
        }
    }

    private fun fetchSingleUrl(u: String): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(u).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Moonlight-Android")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    return response.toString()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request failed: $u - ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return null
    }

    /**
     * Build a list of candidate URLs: proxied variants first (faster in CN), direct as fallback.
     */
    fun buildProxiedUrls(context: Context, url: String): List<String> {
        val tries = ArrayList<String>()
        for (p in getEffectiveProxyPrefixes(context)) {
            tries.add(p + url)
        }
        tries.add(url) // 直连兌底
        return tries
    }

    fun buildProxiedUrls(url: String): List<String> {
        val tries = ArrayList<String>()
        for (p in PROXY_PREFIXES) {
            tries.add(p + url)
        }
        tries.add(url)
        return tries
    }

    private fun getEffectiveProxyPrefixes(context: Context): Array<String> {
        if (isUserProxyOrVpnActive(context)) {
            Log.d(TAG, "User proxy/VPN active; skipping built-in GitHub proxies")
            return emptyArray()
        }
        return PROXY_PREFIXES
    }

    private fun isUserProxyOrVpnActive(context: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val caps = if (network != null) cm.getNetworkCapabilities(network) else null
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    return true
                }
                val proxyInfo = cm?.defaultProxy
                if (!proxyInfo?.host.isNullOrBlank()) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to inspect active network proxy/VPN: ${e.message}")
        }

        return try {
            !Proxy.getDefaultHost().isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun ensureProxyListUpdated(context: Context) {
        if (shouldUpdateProxyList(context)) {
            updateProxyList(context)
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun configureUpdateNotesLinks(notesView: TextView, linkColor: Int) {
        notesView.setLinkTextColor(linkColor)
        notesView.movementMethod = LinkMovementMethod.getInstance()
        notesView.linksClickable = true
    }

    private fun openReleaseNoteLink(context: Context, url: String) {
        if (!HandbookLauncher.openUrl(context, url)) {
            BrowserOnlyLauncher.open(context, url)
        }
    }

    private fun constrainUpdateNotesScroll(context: Context, notesScroll: ScrollView, rawNotes: String) {
        val lineCount = rawNotes.count { it == '\n' } + 1
        if (rawNotes.length < 700 && lineCount < 12) {
            return
        }

        val metrics = context.resources.displayMetrics
        val isLandscape = metrics.widthPixels > metrics.heightPixels
        val maxFraction = if (isLandscape) 0.42f else 0.48f
        val maxHeight = (metrics.heightPixels * maxFraction).toInt()
            .coerceAtLeast(dpToPx(context, if (isLandscape) 180 else 260))

        notesScroll.layoutParams = (notesScroll.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).apply {
            height = maxHeight
        }
        notesScroll.isVerticalScrollBarEnabled = true
    }

    private fun joinStrings(list: List<String>): String {
        val sb = StringBuilder()
        for (i in list.indices) {
            if (i > 0) sb.append("|||")
            sb.append(list[i])
        }
        return sb.toString()
    }

    /**
     * Splits a SharedPreferences-safe string list created by [joinStrings].
     */
    private fun splitStrings(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("|||").filter { it.isNotBlank() }
    }

    private fun createUpdateDownloadRequest(
            context: Context,
            url: String,
            fileName: String
    ): DownloadManager.Request {
        return DownloadManager.Request(url.toUri()).apply {
            setTitle(context.getString(R.string.update_download_notification_title))
            setDescription(fileName)
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setVisibleInDownloadsUi(true)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)")
            addRequestHeader("Accept", "*/*")
            addRequestHeader("Referer", "https://github.com/")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
    }

    private fun validateDownloadedApk(
            context: Context,
            uri: Uri,
            expectedSize: Long,
            expectedSha256: String?
    ): String? {
        if (!hasZipHeader(context, uri)) {
            return "missing APK ZIP header"
        }

        if (expectedSize > 0L) {
            val actualSize = getUriSize(context, uri)
            if (actualSize <= 0L || actualSize != expectedSize) {
                return "size mismatch expected=$expectedSize actual=$actualSize"
            }
        }

        if (expectedSha256 != null) {
            val actualSha256 = computeFileSha256(context, uri)
            if (actualSha256 == null || !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                return "sha256 mismatch expected=$expectedSha256 actual=$actualSha256"
            }
        }

        return null
    }

    private fun retryNextDownloadSource(
            context: Context,
            dm: DownloadManager,
            oldDownloadId: Long,
            apkName: String,
            expectedSha256: String?,
            expectedSize: Long,
            candidates: List<String>,
            candidateIndex: Int
    ): Boolean {
        var nextIndex = candidateIndex + 1
        while (nextIndex < candidates.size) {
            val nextUrl = candidates[nextIndex]
            try {
                try { dm.remove(oldDownloadId) } catch (_: Exception) {}

                val newDownloadId = dm.enqueue(createUpdateDownloadRequest(context, nextUrl, apkName))
                context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).edit {
                    putLong(PREF_DOWNLOAD_ID, newDownloadId)
                        .putString(PREF_DOWNLOAD_APK_NAME, apkName)
                        .putString("update_download_candidates", joinStrings(candidates))
                        .putInt("update_download_candidate_index", nextIndex)
                    if (expectedSha256 != null) {
                        putString(PREF_DOWNLOAD_SHA256, expectedSha256)
                    } else {
                        remove(PREF_DOWNLOAD_SHA256)
                    }
                    if (expectedSize > 0L) {
                        putLong(PREF_DOWNLOAD_EXPECTED_SIZE, expectedSize)
                    } else {
                        remove(PREF_DOWNLOAD_EXPECTED_SIZE)
                    }
                }
                Toast.makeText(context, context.getString(R.string.update_progress_switching_source), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Retrying update download with candidate[$nextIndex]: $nextUrl")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retry update download candidate[$nextIndex]: ${e.message}")
                nextIndex++
            }
        }

        try { dm.remove(oldDownloadId) } catch (_: Exception) {}
        return false
    }

    private fun hasZipHeader(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read APK header: ${e.message}")
            false
        }
    }

    private fun extractSha256FromDigest(digest: String?): String? {
        if (digest.isNullOrBlank()) return null
        val normalized = digest.trim().removePrefix("sha256:").lowercase()
        return if (normalized.matches(Regex("[a-f0-9]{64}"))) normalized else null
    }

    private fun getUriSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) {
                    return afd.length
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read APK size from descriptor: ${e.message}")
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n.toLong()
                }
                total
            } ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "getUriSize failed: ${e.message}")
            -1L
        }
    }

    /**
     * Extracts the target APK SHA256 from release notes.
     * Supports sha256sum format and "apkName: sha256" variants.
     */
    private fun extractSha256ForApk(notes: String?, apkName: String?): String? {
        if (notes.isNullOrEmpty() || apkName.isNullOrEmpty()) return null
        try {
            val nameQ = Pattern.quote(apkName)
            val patterns = arrayOf(
                Pattern.compile("([a-fA-F0-9]{64})\\s+\\*?$nameQ", Pattern.CASE_INSENSITIVE),
                Pattern.compile("$nameQ\\s*[:=\\-]?\\s*([a-fA-F0-9]{64})", Pattern.CASE_INSENSITIVE)
            )
            for (p in patterns) {
                val m = p.matcher(notes)
                if (m.find()) {
                    return m.group(1)?.lowercase()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析 SHA256 失败: ${e.message}")
        }
        return null
    }

    /**
     * 计算下载文件的 SHA256 hex（小写）。失败返回 null。
     */
    private fun computeFileSha256(context: Context, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (e: Exception) {
            Log.e(TAG, "computeFileSha256 失败", e)
            null
        }
    }

    // ------------------------------------------------------------------
    // 数据类
    // ------------------------------------------------------------------

    private class UpdateInfo(
            val version: String,
            val releaseNotes: String?,
            val releasePageUrl: String?,
            val apkName: String?,
            val apkDownloadUrl: String?,
            val expectedSha256: String? = null,
            val expectedSize: Long? = null
    )

}
