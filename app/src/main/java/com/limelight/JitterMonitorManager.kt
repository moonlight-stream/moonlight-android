package com.limelight

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout

import com.limelight.binding.video.JitterMonitor
import com.limelight.binding.video.JitterMonitorView
import com.limelight.preferences.PreferenceConfiguration

import kotlin.math.abs

/**
 * Owns the jitter monitor overlay lifecycle.
 *
 * When disabled, no view is attached and [JitterMonitor.enabled] stays false. When enabled,
 * the chart is shown immediately and displays an empty state until frame samples arrive.
 */
class JitterMonitorManager(
    private val activity: Activity,
    private val prefConfig: PreferenceConfiguration
) {
    private var monitorView: JitterMonitorView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    private var isDragging = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragDeltaX = 0f
    private var dragDeltaY = 0f
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

    private val overlayPrefs: SharedPreferences by lazy {
        activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!ticking) return
            monitorView?.let { updateFromSnapshot(it) }
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    /** Called when the stream starts. Attaches and ticks the overlay only if enabled. */
    fun initialize() {
        if (!prefConfig.enableJitterMonitor) {
            JitterMonitor.enabled = false
            return
        }

        JitterMonitor.enabled = true
        ensureViewAttached()
        applyVisibility()
    }

    /** Enables or disables the monitor while a stream is already running. */
    fun setEnabled(enabled: Boolean) {
        prefConfig.enableJitterMonitor = enabled
        if (!enabled) {
            monitorView?.visibility = View.GONE
            stopTicking()
            JitterMonitor.enabled = false
            return
        }

        JitterMonitor.enabled = true
        ensureViewAttached()
        monitorView?.showEmptyState()
        applyVisibility()
    }

    /** Applies the requested overlay visibility after settings, PiP, or size changes. */
    fun applyVisibility() {
        val show = prefConfig.enableJitterMonitor
        monitorView?.let {
            clampViewWithinParent(it)
            applyViewVisibility(it)
        }

        // Keep polling while enabled; the panel shows an empty state until data arrives.
        if (show && monitorView != null) startTicking() else stopTicking()
    }

    /** Hides immediately for PiP and stops UI polling. */
    fun hideImmediate() {
        monitorView?.visibility = View.GONE
        stopTicking()
    }

    /** Tears down the overlay when the stream ends. */
    fun destroy() {
        stopTicking()
        JitterMonitor.enabled = false
        monitorView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        monitorView = null
    }

    private fun ensureViewAttached() {
        if (monitorView != null) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = JitterMonitorView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(OVERLAY_WIDTH_DP), dp(OVERLAY_HEIGHT_DP)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(DEFAULT_LEFT_MARGIN_DP)
                topMargin = dp(DEFAULT_TOP_MARGIN_DP)
            }
            elevation = dp(OVERLAY_ELEVATION_DP).toFloat()
            visibility = View.GONE
        }

        setupDragging(view)
        root.addView(view)
        monitorView = view
        view.post { applySavedPosition(view) }
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        handler.postDelayed(tickRunnable, REFRESH_INTERVAL_MS)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tickRunnable)
    }

    private fun updateFromSnapshot(view: JitterMonitorView) {
        val snapshot = JitterMonitor.snapshot()
        if (snapshot != null) {
            view.update(snapshot)
        } else {
            view.showEmptyState()
        }
        applyViewVisibility(view)
    }

    private fun applyViewVisibility(view: JitterMonitorView) {
        view.visibility = if (prefConfig.enableJitterMonitor) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragging(view: JitterMonitorView) {
        view.isClickable = true
        view.isFocusable = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handleDragStart(v, event)
                MotionEvent.ACTION_MOVE -> handleDragMove(v, event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleDragEnd(v)
                else -> false
            }
        }
    }

    private fun handleDragStart(view: View, event: MotionEvent): Boolean {
        isDragging = false
        dragStartRawX = event.rawX
        dragStartRawY = event.rawY

        val lp = view.layoutParams as FrameLayout.LayoutParams
        convertGravityToMargins(view, lp)
        dragDeltaX = event.rawX - lp.leftMargin
        dragDeltaY = event.rawY - lp.topMargin
        view.parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun handleDragMove(view: View, event: MotionEvent): Boolean {
        val movedFarEnough =
            abs(event.rawX - dragStartRawX) > touchSlop ||
                    abs(event.rawY - dragStartRawY) > touchSlop
        if (movedFarEnough) {
            isDragging = true
        }
        if (isDragging) {
            moveViewWithinParent(view, event.rawX - dragDeltaX, event.rawY - dragDeltaY)
            view.alpha = DRAG_ALPHA
        }
        return true
    }

    private fun handleDragEnd(view: View): Boolean {
        view.parent?.requestDisallowInterceptTouchEvent(false)
        view.alpha = 1f
        if (isDragging) {
            savePosition(view)
        }
        isDragging = false
        return true
    }

    private fun convertGravityToMargins(view: View, lp: FrameLayout.LayoutParams) {
        if (lp.gravity == Gravity.NO_GRAVITY) return

        val viewLocation = IntArray(2)
        val parentLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        (view.parent as? View)?.getLocationInWindow(parentLocation)

        lp.leftMargin = viewLocation[0] - parentLocation[0]
        lp.topMargin = viewLocation[1] - parentLocation[1]
        lp.gravity = Gravity.NO_GRAVITY
        view.layoutParams = lp
    }

    private fun moveViewWithinParent(view: View, requestedLeft: Float, requestedTop: Float) {
        val parent = view.parent as? View ?: return
        val maxLeft = (parent.width - view.width).coerceAtLeast(0)
        val maxTop = (parent.height - view.height).coerceAtLeast(0)
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = requestedLeft.toInt().coerceIn(0, maxLeft)
        lp.topMargin = requestedTop.toInt().coerceIn(0, maxTop)
        lp.gravity = Gravity.NO_GRAVITY
        view.layoutParams = lp
    }

    private fun clampViewWithinParent(view: View) {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.gravity != Gravity.NO_GRAVITY) return
        moveViewWithinParent(view, lp.leftMargin.toFloat(), lp.topMargin.toFloat())
    }

    private fun applySavedPosition(view: View) {
        if (!overlayPrefs.getBoolean(KEY_HAS_CUSTOM_POSITION, false)) return

        moveViewWithinParent(
            view,
            overlayPrefs.getInt(KEY_LEFT_MARGIN, dp(DEFAULT_LEFT_MARGIN_DP)).toFloat(),
            overlayPrefs.getInt(KEY_TOP_MARGIN, dp(DEFAULT_TOP_MARGIN_DP)).toFloat()
        )
    }

    private fun savePosition(view: View) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        overlayPrefs.edit()
            .putBoolean(KEY_HAS_CUSTOM_POSITION, true)
            .putInt(KEY_LEFT_MARGIN, lp.leftMargin)
            .putInt(KEY_TOP_MARGIN, lp.topMargin)
            .apply()
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, activity.resources.displayMetrics
    ).toInt()

    companion object {
        private const val PREFS_NAME = "jitter_monitor_overlay"
        private const val KEY_HAS_CUSTOM_POSITION = "has_custom_position"
        private const val KEY_LEFT_MARGIN = "left_margin"
        private const val KEY_TOP_MARGIN = "top_margin"
        private const val REFRESH_INTERVAL_MS = 300L
        private const val OVERLAY_WIDTH_DP = 244f
        private const val OVERLAY_HEIGHT_DP = 178f
        private const val DEFAULT_LEFT_MARGIN_DP = 12f
        private const val DEFAULT_TOP_MARGIN_DP = 48f
        private const val OVERLAY_ELEVATION_DP = 15f
        private const val DRAG_ALPHA = 0.72f
    }
}
