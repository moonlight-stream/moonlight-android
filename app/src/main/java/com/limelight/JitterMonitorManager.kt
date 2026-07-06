package com.limelight

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

import com.limelight.binding.video.JitterMonitor
import com.limelight.binding.video.JitterMonitorView
import com.limelight.preferences.PreferenceConfiguration

/**
 * 抖动监控浮层的生命周期管理器。
 *
 * 仅当 `prefConfig.enableJitterMonitor` 开启时才创建 View、置位 [JitterMonitor.enabled]、
 * 并启动 ~300ms 的重绘 tick；关闭时不建 View、不排 Handler、[JitterMonitor.enabled] 保持 false，
 * 对串流零开销。
 */
class JitterMonitorManager(
    private val activity: Activity,
    private val prefConfig: PreferenceConfiguration
) {
    private var monitorView: JitterMonitorView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    private val refreshIntervalMs = 300L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!ticking) return
            val view = monitorView
            if (view != null && view.visibility == View.VISIBLE) {
                JitterMonitor.snapshot()?.let { view.update(it) }
            }
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    /** 串流启动时调用。开启则挂载浮层并开始采集/重绘。 */
    fun initialize() {
        if (!prefConfig.enableJitterMonitor) {
            JitterMonitor.enabled = false
            return
        }
        JitterMonitor.enabled = true
        ensureViewAttached()
        applyVisibility()
    }

    private fun ensureViewAttached() {
        if (monitorView != null) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = JitterMonitorView(activity)
        val lp = FrameLayout.LayoutParams(dp(244f), dp(178f)).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(12f)
            topMargin = dp(48f)
        }
        view.layoutParams = lp
        view.elevation = dp(15f).toFloat()
        root.addView(view)
        monitorView = view
    }

    /** 依据当前偏好显隐浮层（配置变化/退出 PiP 后调用）。 */
    fun applyVisibility() {
        val show = prefConfig.enableJitterMonitor
        monitorView?.visibility = if (show) View.VISIBLE else View.GONE
        // 可见时确保 tick 在跑，隐藏时停 tick，避免空耗主线程
        if (show && monitorView != null) startTicking() else stopTicking()
    }

    /** 进入 PiP 时立即隐藏并停止重绘 tick。 */
    fun hideImmediate() {
        monitorView?.visibility = View.GONE
        stopTicking()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        handler.postDelayed(tickRunnable, refreshIntervalMs)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tickRunnable)
    }

    /** 串流结束/销毁时调用：停止 tick、移除 View、关闭采集。 */
    fun destroy() {
        stopTicking()
        JitterMonitor.enabled = false
        monitorView?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
        }
        monitorView = null
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics
    ).toInt()
}
