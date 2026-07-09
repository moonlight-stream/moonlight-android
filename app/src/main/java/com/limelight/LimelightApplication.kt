package com.limelight

import android.app.Application
import android.util.Log

import com.google.firebase.FirebaseApp
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.crash.CrashReporter
import com.limelight.utils.ConfigurationSyncScheduler
import com.limelight.utils.UiHelper

/**
 * Custom Application that wires up crash diagnostics as early as possible.
 *
 * Firebase 自动初始化已通过 AndroidManifest.xml 中 `tools:node="remove"` 禁用
 * FirebaseInitProvider（issue #310）。改为在此处用 try-catch 手动初始化，
 * 防止无 GMS 框架的 ROM（峰米投影仪 / 国产盒子 / 车机）在 Application.onCreate
 * 之前因 ContentProvider 自动初始化触发 SecurityException 导致 app 闪退。
 *
 * 有 google-services.json 且 GMS 可用的设备：手动 initializeApp 成功，
 * Crashlytics + Analytics 正常工作。
 *
 * 无 GMS 或缺失配置的设备：initializeApp 抛异常被吞，AnalyticsManager / Crashlytics
 * 调用全部静默失败（这些类内部都已包了 try-catch + 空指针守卫）。本地崩溃文件
 * 上报路径仍然有效。
 */
class LimelightApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UiHelper.applyStoredAppTheme(this)
        initializeFirebaseSafely()
        CrashReporter.install(this)
        ConfigurationSyncScheduler.runNow(this)
        warmUpClientCertificate()
    }

    /**
     * 手动初始化 Firebase，捕获无 GMS 设备上的 SecurityException 等异常。
     *
     * 必须在 CrashReporter.install 之前调用，让 Crashlytics 的 UncaughtExceptionHandler
     * 有机会安装；CrashReporter 会链式接在它后面。
     */
    private fun initializeFirebaseSafely() {
        try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            // 无 GMS / 缺 google-services.json / 系统权限异常等：静默降级
            Log.w(TAG, "Firebase init skipped: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * 异步预热客户端证书。
     *
     * 冷启动首次进入 PcView → ComputerManagerService.poll → NvHTTP 时会同步调用
     * `cryptoProvider.getClientCertificate()`，首次使用要么从磁盘加载（数十毫秒
     * 的 IO），要么生成 RSA 2048 密钥对（数百毫秒甚至秒级 CPU）。这段耗时正好卡
     * 在用户最关心的 "PC 卡片何时亮起绿点" 路径上。
     *
     * 把这次访问提前到 Application.onCreate 里、用最低优先级线程做，等用户走到
     * PcView 时证书已经在内存里，`getClientCertificate()` 直接返回缓存。
     *
     * 用低优先级 Thread 而不是协程是为了：
     * - 避免拉起 CoroutineScope / Dispatchers 体系，减少冷启动模块加载
     * - 显式 `MIN_PRIORITY` 让出 CPU 给 UI 线程的首帧绘制
     */
    private fun warmUpClientCertificate() {
        Thread({
            try {
                AndroidCryptoProvider(this).getClientCertificate()
            } catch (t: Throwable) {
                // 预热失败不影响后续真实路径——它会自己重试。
                t.printStackTrace()
            }
        }, "cert-warmup").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val TAG = "LimelightApp"
    }
}
