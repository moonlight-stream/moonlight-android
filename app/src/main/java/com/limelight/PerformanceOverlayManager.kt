package com.limelight

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit

import com.limelight.binding.video.PerformanceInfo
import com.limelight.preferences.PerfOverlayDisplayItemsPreference
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.StreamView
import com.limelight.utils.AppDialogStyler
import com.limelight.utils.MoonPhaseUtils
import com.limelight.utils.NetHelper
import com.limelight.utils.UiHelper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumMap
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class PerformanceOverlayManager(
    private val activity: Activity,
    private val prefConfig: PreferenceConfiguration,
    private val onJitterMonitorChanged: (Boolean) -> Unit = {}
) {

    private var performanceOverlayView: LinearLayout? = null
    private lateinit var streamView: StreamView

    private val overlayPrefs: SharedPreferences by lazy {
        activity.getSharedPreferences("performance_overlay", Activity.MODE_PRIVATE)
    }

    private var requestedPerformanceOverlayVisibility = View.GONE
    private var hasShownPerfOverlay = false

    // 性能覆盖层拖动相关
    private var isDraggingPerfOverlay = false
    private var perfOverlayStartX = 0f
    private var perfOverlayStartY = 0f
    private var perfOverlayDeltaX = 0f
    private var perfOverlayDeltaY = 0f

    // 点击检测相关
    private var clickStartTime = 0L
    private var clickStartX = 0f
    private var clickStartY = 0f
    private var lastClickTime = 0L
    private var isDoubleClickHandled = false

    // 8个吸附位置的枚举
    private enum class SnapPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    // 计算带宽用
    private var previousTimeMillis = 0L
    private var previousRxBytes = 0L
    private var lastValidBandwidth = "N/A"

    // 月相缓存
    private var currentMoonPhaseIcon = "🌙"
    private var lastCalculatedDay = -1

    // 当前性能信息缓存
    private var currentPerformanceInfo: PerformanceInfo? = null

    // 实际设备刷新率（从 Game 传递）
    private var actualDisplayRefreshRate = 0.0f

    // 电量更新相关
    private var lastBatteryUpdateTime = 0L
    private var hasDisplayableBattery = false

    // 串流电量统计
    private var streamStartBatteryLevel = -1
    private var streamStartTime = -1L

    /**
     * 性能项目枚举 - 统一管理所有性能指标
     */
    private enum class PerformanceItem(val viewId: Int, val preferenceKey: String) {
        RESOLUTION(R.id.perfRes, "resolution"),
        DECODER(R.id.perfDecoder, "decoder"),
        RENDER_FPS(R.id.perfRenderFps, "render_fps"),
        PACKET_LOSS(R.id.perfPacketLoss, "packet_loss"),
        NETWORK_LATENCY(R.id.perfNetworkLatency, "network_latency"),
        DECODE_LATENCY(R.id.perfDecodeLatency, "decode_latency"),
        HOST_LATENCY(R.id.perfHostLatency, "host_latency"),
        BATTERY(R.id.perfBattery, "battery"),
        ONE_PERCENT_LOW(R.id.perfOnePercentLow, "one_percent_low")
    }

    /**
     * 性能项目信息类 - 包含View引用和相关信息
     */
    private class PerformanceItemInfo(
        val item: PerformanceItem,
        val view: TextView?,
        val infoMethod: Runnable
    ) {
        val isVisible: Boolean
            get() = view != null && view.visibility == View.VISIBLE
    }

    // 性能项目信息数组
    private lateinit var performanceItems: Array<PerformanceItemInfo>
    // 枚举 → 项信息 快查索引，避免频繁 O(n) 遍历
    private lateinit var performanceItemMap: EnumMap<PerformanceItem, PerformanceItemInfo>

    // 解码器类型信息类
    private class DecoderTypeInfo(val fullName: String, val shortName: String)

    /**
     * 初始化性能覆盖层
     */
    fun initialize() {
        performanceOverlayView = activity.findViewById(R.id.performanceOverlay)
        streamView = activity.findViewById(R.id.surfaceView)

        initializePerformanceItems()
        loadLayoutOrientation()

        if (prefConfig.enablePerfOverlay) {
            requestedPerformanceOverlayVisibility = View.VISIBLE
            performanceOverlayView?.apply {
                visibility = View.GONE
                alpha = 0.0f
            }
        }
        configurePerformanceOverlay()
        setupPerformanceOverlayDragging()
        recordStreamStart()
    }

    private fun initializePerformanceItems() {
        performanceItems = Array(PerformanceItem.entries.size) { i ->
            val item = PerformanceItem.entries[i]
            val view: TextView? = activity.findViewById(item.viewId)
            val infoMethod = getInfoMethodForItem(item)
            PerformanceItemInfo(item, view, infoMethod)
        }
        performanceItemMap = EnumMap(PerformanceItem::class.java)
        for (info in performanceItems) {
            performanceItemMap[info.item] = info
        }
    }

    private fun getInfoMethodForItem(item: PerformanceItem): Runnable {
        return when (item) {
            PerformanceItem.RESOLUTION -> Runnable { showResolutionInfo() }
            PerformanceItem.DECODER -> Runnable { showDecoderInfo() }
            PerformanceItem.RENDER_FPS -> Runnable { showFpsInfo() }
            PerformanceItem.PACKET_LOSS -> Runnable { showPacketLossInfo() }
            PerformanceItem.NETWORK_LATENCY -> Runnable { showNetworkLatencyInfo() }
            PerformanceItem.DECODE_LATENCY -> Runnable { showDecodeLatencyInfo() }
            PerformanceItem.HOST_LATENCY -> Runnable { showHostLatencyInfo() }
            PerformanceItem.BATTERY -> Runnable { showBatteryInfo() }
            PerformanceItem.ONE_PERCENT_LOW -> Runnable { showOnePercentLowInfo() }
        }
    }

    fun hideOverlayImmediate() {
        performanceOverlayView?.visibility = View.GONE
    }

    fun applyRequestedVisibility() {
        performanceOverlayView?.visibility = requestedPerformanceOverlayVisibility
    }

    fun isPerfOverlayVisible(): Boolean {
        return requestedPerformanceOverlayVisibility == View.VISIBLE
    }

    fun togglePerformanceOverlay() {
        val overlay = performanceOverlayView ?: return

        if (requestedPerformanceOverlayVisibility == View.VISIBLE) {
            requestedPerformanceOverlayVisibility = View.GONE
            hasShownPerfOverlay = false
            fadeOutAndHide(overlay)
        } else {
            requestedPerformanceOverlayVisibility = View.VISIBLE
            hasShownPerfOverlay = true
            overlay.visibility = View.VISIBLE
            overlay.alpha = 1.0f
        }
    }

    fun applyOverlayState() {
        val overlay = performanceOverlayView ?: return

        if (!prefConfig.enablePerfOverlay) {
            requestedPerformanceOverlayVisibility = View.GONE
            hasShownPerfOverlay = false
            fadeOutAndHide(overlay)
            return
        }

        requestedPerformanceOverlayVisibility = View.VISIBLE

        if (overlay.visibility != View.VISIBLE || overlay.alpha < 1.0f) {
            overlay.visibility = View.VISIBLE
            overlay.alpha = 1.0f
            hasShownPerfOverlay = true
        }

        setupPerformanceOverlayDragging()
    }

    private fun fadeOutAndHide(overlay: View) {
        val anim = AnimationUtils.loadAnimation(activity, R.anim.perf_overlay_fadeout)
        overlay.startAnimation(anim)
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                overlay.visibility = View.GONE
                overlay.alpha = 0.0f
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    fun refreshPerformanceOverlayConfig() {
        if (performanceOverlayView != null && requestedPerformanceOverlayVisibility == View.VISIBLE) {
            configureDisplayItems()
            configureTextAlignment()
        }
    }

    fun onConfigurationChanged() {
        if (performanceOverlayView != null) {
            configurePerformanceOverlay()
        }
    }

    fun updatePerformanceInfo(performanceInfo: PerformanceInfo) {
        currentPerformanceInfo = performanceInfo
        updateBandwidthInfo(performanceInfo)

        activity.runOnUiThread {
            showOverlayIfNeeded()
            updatePerformanceViewsWithStyledText(performanceInfo)
            updateBatteryDisplayIfNeeded()
        }
    }

    fun setActualDisplayRefreshRate(refreshRate: Float) {
        this.actualDisplayRefreshRate = refreshRate
    }

    fun recordStreamStart() {
        hasDisplayableBattery = UiHelper.hasDisplayableBattery(activity)
        streamStartBatteryLevel = if (hasDisplayableBattery) {
            UiHelper.getBatteryLevel(activity)
        } else {
            -1
        }
        streamStartTime = System.currentTimeMillis()
        lastBatteryUpdateTime = streamStartTime
        activity.runOnUiThread { updateBatteryViewIfVisible() }
    }

    private fun updateBatteryDisplayIfNeeded() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBatteryUpdateTime >= BATTERY_UPDATE_INTERVAL_MS) {
            lastBatteryUpdateTime = currentTime
            updateBatteryViewIfVisible()
        }
    }

    private fun updateBatteryViewIfVisible() {
        val batteryView = getPerformanceItemView(PerformanceItem.BATTERY)
        if (batteryView != null && batteryView.visibility == View.VISIBLE) {
            updateBatteryText(batteryView)
        }
    }

    private fun updateBandwidthInfo(performanceInfo: PerformanceInfo) {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val timeMillis = System.currentTimeMillis()
        val timeMillisInterval = timeMillis - previousTimeMillis

        val calculatedBandwidth = NetHelper.calculateBandwidth(currentRxBytes, previousRxBytes, timeMillisInterval)

        if (timeMillisInterval > 5000) {
            performanceInfo.bandWidth = lastValidBandwidth
            previousTimeMillis = timeMillis
            previousRxBytes = currentRxBytes
            return
        }

        if (calculatedBandwidth != "0 K/s") {
            performanceInfo.bandWidth = calculatedBandwidth
            lastValidBandwidth = calculatedBandwidth
            previousTimeMillis = timeMillis
        } else {
            performanceInfo.bandWidth = lastValidBandwidth
        }

        previousRxBytes = currentRxBytes
    }

    private fun buildDecoderInfo(performanceInfo: PerformanceInfo): String {
        val decoderTypeInfo = getDecoderTypeInfo(performanceInfo.decoder)
        // NBSP (\u00A0) 防止 TextView 在 "H265 HDR" 的空格处断行
        return if (performanceInfo.isHdrActive) "${decoderTypeInfo.shortName}\u00A0HDR"
               else decoderTypeInfo.shortName
    }

    private fun getCurrentMoonPhaseIcon(): String {
        val now = Calendar.getInstance(TimeZone.getDefault())
        val currentDay = now.get(Calendar.DAY_OF_YEAR)

        if (currentDay == lastCalculatedDay) {
            return currentMoonPhaseIcon
        }

        currentMoonPhaseIcon = MoonPhaseUtils.getMoonPhaseIcon(MoonPhaseUtils.getCurrentMoonPhase())
        lastCalculatedDay = currentDay

        return currentMoonPhaseIcon
    }

    private fun showOverlayIfNeeded() {
        if (prefConfig.enablePerfOverlay && !hasShownPerfOverlay && performanceOverlayView != null) {
            performanceOverlayView?.visibility = View.VISIBLE
            performanceOverlayView?.alpha = 1.0f
            hasShownPerfOverlay = true
            setupPerformanceOverlayDragging()
        }
    }

    /**
     * 构造带图标 + 数值 + 单位的样式化文本。
     *
     * 优先级：[iconRes] > [iconEmoji]。当指定 [iconRes] 时使用 Phosphor 矢量图标
     * （与 HarmonyOS 版本视觉一致），通过 ImageSpan 嵌入到行内；图标颜色默认跟随
     * [valueColor]（未提供时为白色）。
     */
    private fun createStyledText(
        iconRes: Int?,
        value: String?,
        unit: String?,
        valueColor: Int?,
        iconEmoji: String? = null,
        textSizePx: Float? = null
    ): SpannableString {
        val builder = SpannableStringBuilder()

        if (iconRes != null) {
            val drawable = ContextCompat.getDrawable(activity, iconRes)?.mutate()
            if (drawable != null) {
                // 图标尺寸跟随当前行字号；缺省时回退到 14sp
                val basePx = textSizePx ?: TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 14f, activity.resources.displayMetrics
                )
                val sizePx = (basePx * 1.05f).toInt()
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.setTint(valueColor ?: Color.WHITE)
                val placeholder = builder.length
                builder.append("\u00A0")
                builder.setSpan(
                    CenterAlignedImageSpan(drawable),
                    placeholder, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append("\u00A0")
            }
        } else if (!iconEmoji.isNullOrEmpty()) {
            val iconStart = builder.length
            builder.append(iconEmoji)
            builder.setSpan(StyleSpan(Typeface.BOLD), iconStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(1.1f), iconStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.append("\u00A0")
        }

        if (!value.isNullOrEmpty()) {
            val valueStart = builder.length
            builder.append(value)
            builder.setSpan(TypefaceSpan("sans-serif-medium"), valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(1.0f), valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (valueColor != null) {
                builder.setSpan(ForegroundColorSpan(valueColor), valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (!unit.isNullOrEmpty()) {
            builder.append("\u00A0")
            val unitStart = builder.length
            builder.append(unit)
            builder.setSpan(TypefaceSpan("sans-serif-light"), unitStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(0.9f), unitStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(0xCCFFFFFF.toInt()), unitStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return SpannableString(builder)
    }

    private fun updatePerformanceViewsWithStyledText(performanceInfo: PerformanceInfo) {
        for (itemInfo in performanceItems) {
            if (itemInfo.isVisible) {
                updatePerformanceItemText(itemInfo, performanceInfo)
            }
        }
        configureTextAlignment()
    }

    private fun updatePerformanceItemText(itemInfo: PerformanceItemInfo, performanceInfo: PerformanceInfo) {
        when (itemInfo.item) {
            PerformanceItem.RESOLUTION -> updateResolutionText(itemInfo.view!!, performanceInfo)
            PerformanceItem.DECODER -> updateDecoderText(itemInfo.view!!, performanceInfo)
            PerformanceItem.RENDER_FPS -> updateRenderFpsText(itemInfo.view!!, performanceInfo)
            PerformanceItem.PACKET_LOSS -> updatePacketLossText(itemInfo.view!!, performanceInfo)
            PerformanceItem.NETWORK_LATENCY -> updateNetworkLatencyText(itemInfo.view!!, performanceInfo)
            PerformanceItem.DECODE_LATENCY -> updateDecodeLatencyText(itemInfo.view!!, performanceInfo)
            PerformanceItem.HOST_LATENCY -> updateHostLatencyText(itemInfo.view!!, performanceInfo)
            PerformanceItem.BATTERY -> Unit
            PerformanceItem.ONE_PERCENT_LOW -> updateOnePercentLowText(itemInfo.view!!, performanceInfo)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateResolutionText(view: TextView, performanceInfo: PerformanceInfo) {
        val resValue = String.format("%dx%d@%.0f",
            performanceInfo.initialWidth, performanceInfo.initialHeight, performanceInfo.totalFps)
        val moonIcon = getCurrentMoonPhaseIcon()
        // 月相位置保持与原版一致：用 emoji 作为前缀图标
        view.text = createStyledText(null, resValue, "", null, moonIcon, textSizePx = view.textSize)
    }

    private fun updateDecoderText(view: TextView, performanceInfo: PerformanceInfo) {
        val decoderInfo = buildDecoderInfo(performanceInfo)
        // 原版本解码器行不带图标
        view.text = createStyledText(null, decoderInfo, "", null, textSizePx = view.textSize)
        view.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    @SuppressLint("DefaultLocale")
    private fun updateRenderFpsText(view: TextView, performanceInfo: PerformanceInfo) {
        // NBSP + Word Joiner 围绕 / 防止 TextView 在 "Rx 60 / Rd 60" 任意空格或斜杠处断行
        val fpsValue = if (performanceInfo.framegenFps > 0.5f) {
            String.format("Rx\u00A0%.0f\u00A0\u2060/\u2060\u00A0Rd\u00A0%.0f\u00A0\u2060/\u2060\u00A0FG\u00A0%.0f",
                performanceInfo.receivedFps, performanceInfo.renderedFps, performanceInfo.framegenFps)
        } else {
            String.format("Rx\u00A0%.0f\u00A0\u2060/\u2060\u00A0Rd\u00A0%.0f",
                performanceInfo.receivedFps, performanceInfo.renderedFps)
        }
        // 原版本本行无图标
        view.text = createStyledText(null, fpsValue, "FPS", 0xFF0DDAF4.toInt(), textSizePx = view.textSize)
    }

    @SuppressLint("DefaultLocale")
    private fun updatePacketLossText(view: TextView, performanceInfo: PerformanceInfo) {
        val lossValue = String.format("%.2f", performanceInfo.lostFrameRate)
        val lossColor = if (performanceInfo.lostFrameRate < 5.0f) 0xFF7D9D7D.toInt() else 0xFFB57D7D.toInt()
        view.text = createStyledText(R.drawable.phc_perf_signal, lossValue, "%", lossColor, textSizePx = view.textSize)
    }

    @SuppressLint("DefaultLocale")
    private fun updateNetworkLatencyText(view: TextView, performanceInfo: PerformanceInfo) {
        // 带宽用 gauge 仪表盘图标更直观，始终显示
        val iconRes: Int = R.drawable.phc_perf_gauge
        val bandwidthAndLatency = String.format("%s\u00A0\u00A0\u00A0%d\u00A0\u00B1\u00A0%d",
            performanceInfo.bandWidth,
            (performanceInfo.rttInfo shr 32).toInt(),
            performanceInfo.rttInfo.toInt())
        view.text = createStyledText(iconRes, bandwidthAndLatency, "ms", 0xFFBCEDD3.toInt(), textSizePx = view.textSize)
    }

    @SuppressLint("DefaultLocale")
    private fun updateDecodeLatencyText(view: TextView, performanceInfo: PerformanceInfo) {
        val isHot = performanceInfo.decodeTimeMs >= 15
        val iconRes: Int? = if (isHot) null else R.drawable.phc_perf_timer
        val iconEmoji: String? = if (isHot) "🥵" else null
        val latencyValue = String.format("%.2f", performanceInfo.decodeTimeMs)
        view.text = createStyledText(iconRes, latencyValue, "ms", 0xFFD597E3.toInt(), iconEmoji, textSizePx = view.textSize)
    }

    @SuppressLint("DefaultLocale")
    private fun updateHostLatencyText(view: TextView, performanceInfo: PerformanceInfo) {
        if (performanceInfo.framesWithHostProcessingLatency > 0) {
            val latencyValue = String.format("%.1f", performanceInfo.aveHostProcessingLatency)
            view.text = createStyledText(R.drawable.phc_perf_monitor, latencyValue, "ms", 0xFF009688.toInt(), textSizePx = view.textSize)
        } else {
            // 空置时保持原版本的 🧋 emoji
            view.text = createStyledText(null, "Ver.V+", "", 0xFF009688.toInt(), iconEmoji = "🧋", textSizePx = view.textSize)
        }
    }

    private fun updateBatteryText(view: TextView) {
        if (!hasDisplayableBattery) {
            view.visibility = View.GONE
            return
        }

        val batteryLevel = UiHelper.getBatteryLevel(activity)
        val isCharging = UiHelper.isCharging(activity)
        val batteryColor = when {
            batteryLevel > 50 -> 0xFF90EE90.toInt()
            batteryLevel > 20 -> 0xFFFFA500.toInt()
            else -> 0xFFFF6B6B.toInt()
        }
        // 与鸿蒙一致：根据充电/电量分档选择不同电池图标
        val iconRes = when {
            isCharging          -> R.drawable.phc_perf_charging
            batteryLevel > 80   -> R.drawable.phc_perf_battery_full
            batteryLevel > 50   -> R.drawable.phc_perf_battery_mid
            batteryLevel > 20   -> R.drawable.phc_perf_battery_low
            batteryLevel > 0    -> R.drawable.phc_perf_battery_empty
            else                -> R.drawable.phc_perf_battery
        }
        view.text = createStyledText(iconRes, batteryLevel.toString(), "%", batteryColor, textSizePx = view.textSize)
    }

    @SuppressLint("DefaultLocale")
    private fun updateOnePercentLowText(view: TextView, performanceInfo: PerformanceInfo) {
        val lowFps = performanceInfo.onePercentLowFps
        if (lowFps <= 0) {
            view.text = createStyledText(null, "1%Low\u00A0—", "FPS", 0xFFFF7043.toInt(), textSizePx = view.textSize)
            return
        }
        val value = String.format("1%%Low\u00A0%.1f", lowFps)
        val color = when {
            lowFps >= performanceInfo.renderedFps * 0.9f -> 0xFF90EE90.toInt()
            lowFps >= performanceInfo.renderedFps * 0.7f -> 0xFFFFD740.toInt()
            else -> 0xFFFF7043.toInt()
        }
        view.text = createStyledText(null, value, "FPS", color, textSizePx = view.textSize)
    }

    private fun showBatteryInfo() {
        if (!hasDisplayableBattery) {
            return
        }

        val batteryLevel = UiHelper.getBatteryLevel(activity)
        val isCharging = UiHelper.isCharging(activity)
        val status = activity.getString(
            when {
                batteryLevel > 50 -> R.string.perf_battery_status_sufficient
                batteryLevel > 20 -> R.string.perf_battery_status_low
                else -> R.string.perf_battery_status_critical
            }
        )

        val info = StringBuilder()
        info.append(activity.getString(R.string.perf_battery_info_content, batteryLevel, status))

        val hasStreamData = streamStartBatteryLevel >= 0 && streamStartTime > 0
        val streamDurationSeconds = if (hasStreamData) (System.currentTimeMillis() - streamStartTime) / 1000 else 0L

        if (isCharging) {
            info.append("\n\n⚡ ").append(activity.getString(R.string.perf_battery_charging))
            if (hasStreamData) {
                info.append("\n")
                    .append(activity.getString(R.string.perf_stream_duration, formatDuration(streamDurationSeconds)))
            }
        } else if (hasStreamData) {
            val batteryConsumed = streamStartBatteryLevel - batteryLevel
            info.append("\n\n")
                .append(activity.getString(
                    R.string.perf_battery_consumed,
                    if (batteryConsumed > 0) "${batteryConsumed}%" else "0%"
                ))
                .append("\n")
                .append(activity.getString(R.string.perf_stream_duration, formatDuration(streamDurationSeconds)))

            if (batteryConsumed > 0 && streamDurationSeconds > 0) {
                val consumedPerMinute = batteryConsumed.toDouble() / (streamDurationSeconds / 60.0)
                if (consumedPerMinute > 0) {
                    val remainingMinutes = (batteryLevel / consumedPerMinute).toLong()
                    info.append("\n")
                        .append(activity.getString(
                            R.string.perf_battery_remaining_estimate,
                            formatDuration(remainingMinutes * 60)
                        ))
                }
            }
        }

        showInfoDialog(activity.getString(R.string.perf_battery_info_title), info.toString())
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds < 60) {
            return activity.getString(R.string.perf_duration_seconds, seconds)
        }
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return when {
            hours > 0 -> if (minutes > 0) {
                activity.getString(R.string.perf_duration_hours_minutes, hours, minutes)
            } else {
                activity.getString(R.string.perf_duration_hours, hours)
            }
            remainingSeconds > 0 -> activity.getString(R.string.perf_duration_minutes_seconds, minutes, remainingSeconds)
            else -> activity.getString(R.string.perf_duration_minutes, minutes)
        }
    }

    private fun getPerformanceItemView(item: PerformanceItem): TextView? {
        return performanceItemMap[item]?.view
    }

    private fun isEffectiveVerticalLayout(): Boolean {
        val isPortrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) return true
        return prefConfig.perfOverlayOrientation == PreferenceConfiguration.PerfOverlayOrientation.VERTICAL
    }

    @SuppressLint("RtlHardcoded")
    private fun configurePerformanceOverlay() {
        val overlay = performanceOverlayView ?: return

        val layoutParams = overlay.layoutParams as FrameLayout.LayoutParams

        val isPortrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val isVertical = isEffectiveVerticalLayout()
        if (isVertical) {
            overlay.orientation = LinearLayout.VERTICAL
        } else {
            overlay.orientation = LinearLayout.HORIZONTAL
        }
        // 同步每个性能 TextView 的宽度模式：
        // - 垂直布局保留 match_parent 让 gravity 右对齐生效
        // - 水平布局必须 wrap_content，否则 LinearLayout 二次测量会让子项保留之前的最大宽度，
        //   数字位数缩短后无法回退，导致 item 间距残留。
        val targetWidth = if (isVertical)
            LinearLayout.LayoutParams.MATCH_PARENT
        else
            LinearLayout.LayoutParams.WRAP_CONTENT
        for (itemInfo in performanceItems) {
            val view = itemInfo.view ?: continue
            val lp = view.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (lp.width != targetWidth) {
                lp.width = targetWidth
                view.layoutParams = lp
            }
        }
        overlay.setBackgroundColor(getOverlayBackgroundColor())

        configureDisplayItems()

        val prefs = overlayPrefs
        val hasCustomPosition = prefs.getBoolean("has_custom_position", false)

        if (isPortrait) {
            layoutParams.gravity = Gravity.RIGHT or Gravity.TOP
            layoutParams.leftMargin = 0
            layoutParams.topMargin = 0
        } else if (hasCustomPosition) {
            layoutParams.gravity = Gravity.NO_GRAVITY
            layoutParams.leftMargin = prefs.getInt("left_margin", 0)
            layoutParams.topMargin = prefs.getInt("top_margin", 0)
        } else {
            when (prefConfig.perfOverlayPosition) {
                PreferenceConfiguration.PerfOverlayPosition.TOP ->
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                PreferenceConfiguration.PerfOverlayPosition.BOTTOM ->
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                PreferenceConfiguration.PerfOverlayPosition.TOP_LEFT ->
                    layoutParams.gravity = Gravity.LEFT or Gravity.TOP
                PreferenceConfiguration.PerfOverlayPosition.TOP_RIGHT ->
                    layoutParams.gravity = Gravity.RIGHT or Gravity.TOP
                PreferenceConfiguration.PerfOverlayPosition.BOTTOM_LEFT ->
                    layoutParams.gravity = Gravity.LEFT or Gravity.BOTTOM
                PreferenceConfiguration.PerfOverlayPosition.BOTTOM_RIGHT ->
                    layoutParams.gravity = Gravity.RIGHT or Gravity.BOTTOM
            }
            layoutParams.leftMargin = 0
            layoutParams.topMargin = 0
        }
        layoutParams.rightMargin = 0
        layoutParams.bottomMargin = 0

        overlay.layoutParams = layoutParams
        overlay.post { configureTextAlignment() }
        setupPerformanceOverlayDragging()
    }

    private fun configureDisplayItems() {
        for (itemInfo in performanceItems) {
            if (itemInfo.view != null) {
                val isEnabled = PerfOverlayDisplayItemsPreference.isItemEnabled(activity, itemInfo.item.preferenceKey)
                itemInfo.view.visibility = if (isEnabled) View.VISIBLE else View.GONE
            }
        }
    }

    private fun getOverlayBackgroundColor(): Int {
        val alpha = prefConfig.perfOverlayBgOpacity.coerceIn(0, 100) * 255 / 100
        return Color.argb(alpha, OVERLAY_BACKGROUND_RGB, OVERLAY_BACKGROUND_RGB, OVERLAY_BACKGROUND_RGB)
    }

    private fun configureTextAlignment() {

        val isVertical = isEffectiveVerticalLayout()
        val isRightSide = determineRightSidePosition(isVertical)

        val gravity = if (isVertical && isRightSide)
            Gravity.CENTER_VERTICAL or Gravity.END
        else
            Gravity.CENTER_VERTICAL or Gravity.START

        for (itemInfo in performanceItems) {
            if (itemInfo.isVisible) {
                configureTextViewStyle(itemInfo.view!!, gravity, isVertical)
            }
        }
    }

    private fun determineRightSidePosition(isVertical: Boolean): Boolean {
        val prefs = overlayPrefs
        val hasCustomPosition = prefs.getBoolean("has_custom_position", false)

        return if (hasCustomPosition) {
            val viewDimensions = getViewDimensions(performanceOverlayView!!)
            val viewWidth = viewDimensions[0]
            val leftMargin = prefs.getInt("left_margin", 0)
            (leftMargin + viewWidth) > (streamView.width * 2 / 3)
        } else {
            prefConfig.perfOverlayPosition == PreferenceConfiguration.PerfOverlayPosition.TOP_RIGHT ||
                prefConfig.perfOverlayPosition == PreferenceConfiguration.PerfOverlayPosition.BOTTOM_RIGHT
        }
    }

    private fun configureTextViewStyle(textView: TextView, gravity: Int, isVertical: Boolean) {
        textView.gravity = gravity
        textView.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textView.letterSpacing = 0.02f
        textView.includeFontPadding = false

        if (isVertical) {
            textView.setShadowLayer(2.5f, 1.0f, 1.0f, 0x80000000.toInt())
        } else {
            textView.setShadowLayer(1.5f, 0.5f, 0.5f, 0x60000000)
        }

        val titleSize = getAdaptiveTextSizePx(11f)
        val bodySize = getAdaptiveTextSizePx(10f)

        val viewId = textView.id
        when (viewId) {
            R.id.perfRes -> {
                textView.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize)
            }
            R.id.perfDecoder -> {
                textView.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodySize)
            }
            R.id.perfRenderFps -> {
                textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodySize)
            }
            R.id.perfPacketLoss, R.id.perfNetworkLatency, R.id.perfDecodeLatency,
            R.id.perfHostLatency, R.id.perfBattery, R.id.perfOnePercentLow -> {
                textView.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodySize)
            }
        }
    }

    private fun getAdaptiveTextSizePx(baseSizeSp: Float): Float {
        val dm: DisplayMetrics = activity.resources.displayMetrics
        val shortSide = minOf(dm.widthPixels, dm.heightPixels)
        val shortSideDp = shortSide / dm.density
        val referenceDp = 411f
        val scaleFactor = sqrt((shortSideDp / referenceDp).toDouble()).toFloat()
        val targetPx = baseSizeSp * dm.density * scaleFactor
        return targetPx.coerceIn(8f, 40f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPerformanceOverlayDragging() {
        val overlay = performanceOverlayView ?: return

        if (prefConfig.perfOverlayLocked) {
            overlay.setOnTouchListener(null)
            overlay.isClickable = false
            overlay.isFocusable = false
            overlay.isLongClickable = false
        } else {
            overlay.isClickable = true
            overlay.isFocusable = true
            overlay.isLongClickable = true

            overlay.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> handleActionDown(v, event)
                    MotionEvent.ACTION_MOVE -> handleActionMove(v, event)
                    MotionEvent.ACTION_UP -> handleActionUp(v, event)
                    else -> false
                }
            }
        }
    }

    private fun handleActionDown(v: View, event: MotionEvent): Boolean {
        isDraggingPerfOverlay = true
        perfOverlayStartX = event.rawX
        perfOverlayStartY = event.rawY
        clickStartTime = System.currentTimeMillis()
        clickStartX = event.x
        clickStartY = event.y
        isDoubleClickHandled = false

        val layoutParams = v.layoutParams as FrameLayout.LayoutParams

        if (layoutParams.gravity != Gravity.NO_GRAVITY) {
            convertGravityToMargins(v, layoutParams)
        }

        perfOverlayDeltaX = perfOverlayStartX - layoutParams.leftMargin
        perfOverlayDeltaY = perfOverlayStartY - layoutParams.topMargin

        applyDraggingVisualFeedback(v, true)
        return true
    }

    private fun handleActionMove(v: View, event: MotionEvent): Boolean {
        if (!isDraggingPerfOverlay) return false

        val parentDimensions = getParentDimensions(v)
        val viewDimensions = getViewDimensions(v)
        val parentWidth = parentDimensions[0]
        val parentHeight = parentDimensions[1]
        val viewWidth = viewDimensions[0]
        val viewHeight = viewDimensions[1]

        val layoutParams = v.layoutParams as FrameLayout.LayoutParams
        var newLeftMargin = (event.rawX - perfOverlayDeltaX).toInt()
        var newTopMargin = (event.rawY - perfOverlayDeltaY).toInt()

        newLeftMargin = newLeftMargin.coerceIn(0, parentWidth - viewWidth)
        newTopMargin = newTopMargin.coerceIn(0, parentHeight - viewHeight)

        layoutParams.leftMargin = newLeftMargin
        layoutParams.topMargin = newTopMargin
        layoutParams.gravity = Gravity.NO_GRAVITY
        v.layoutParams = layoutParams

        configureTextAlignment()
        return true
    }

    private fun handleActionUp(v: View, event: MotionEvent): Boolean {
        if (!isDraggingPerfOverlay) return false

        isDraggingPerfOverlay = false
        applyDraggingVisualFeedback(v, false)

        if (isClick(event)) {
            handleClickEvent()
        } else {
            snapToNearestPosition(v)
        }

        return true
    }

    private fun convertGravityToMargins(v: View, layoutParams: FrameLayout.LayoutParams) {
        val viewLocation = IntArray(2)
        val parentLocation = IntArray(2)
        v.getLocationInWindow(viewLocation)
        (v.parent as View).getLocationInWindow(parentLocation)

        layoutParams.leftMargin = viewLocation[0] - parentLocation[0]
        layoutParams.topMargin = viewLocation[1] - parentLocation[1]
        layoutParams.gravity = Gravity.NO_GRAVITY
        v.layoutParams = layoutParams
    }

    private fun applyDraggingVisualFeedback(v: View, isDragging: Boolean) {
        val targetAlpha = if (isDragging) 0.7f else 1.0f
        val targetScale = if (isDragging) 1.05f else 1.0f
        v.alpha = targetAlpha
        v.scaleX = targetScale
        v.scaleY = targetScale
    }

    private fun handleClickEvent() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastClick = currentTime - lastClickTime

        if (timeSinceLastClick < DOUBLE_CLICK_TIMEOUT && lastClickTime > 0L) {
            toggleLayoutOrientation()
            lastClickTime = 0
            isDoubleClickHandled = true
        } else {
            lastClickTime = currentTime
            isDoubleClickHandled = false
            performanceOverlayView?.postDelayed({
                if (!isDoubleClickHandled && lastClickTime > 0L) {
                    showClickedItemInfo()
                }
            }, DOUBLE_CLICK_TIMEOUT.toLong())
        }
    }

    private fun showClickedItemInfo() {
        if (isEffectiveVerticalLayout()) {
            showClickedItemInfoVertical()
        } else {
            showClickedItemInfoHorizontal()
        }
    }

    private fun showClickedItemInfoVertical() {
        val overlayHeight = performanceOverlayView?.height ?: return
        if (overlayHeight == 0) return

        val visibleItemCount = getVisibleItemCount()
        if (visibleItemCount == 0) {
            showMoonPhaseInfo()
            return
        }

        val itemHeight = overlayHeight / visibleItemCount
        val clickedItemIndex = (clickStartY / itemHeight).toInt()

        showInfoByIndex(clickedItemIndex)
    }

    private fun showClickedItemInfoHorizontal() {
        val overlayWidth = performanceOverlayView?.width ?: return
        if (overlayWidth == 0) return

        val visibleItemCount = getVisibleItemCount()
        if (visibleItemCount == 0) {
            showMoonPhaseInfo()
            return
        }

        val clickedItemIndex = findClickedItemByBoundaries()
        showInfoByIndex(clickedItemIndex)
    }

    private fun findClickedItemByBoundaries(): Int {
        var currentIndex = 0
        for (itemInfo in performanceItems) {
            if (itemInfo.isVisible) {
                val viewLocation = IntArray(2)
                itemInfo.view?.getLocationInWindow(viewLocation)

                val overlayLocation = IntArray(2)
                performanceOverlayView?.getLocationInWindow(overlayLocation)

                val viewLeft = viewLocation[0] - overlayLocation[0]
                val viewRight = viewLeft + (itemInfo.view?.width ?: 0)

                if (clickStartX >= viewLeft && clickStartX <= viewRight) {
                    return currentIndex
                }
                currentIndex++
            }
        }
        return -1
    }

    private fun getVisibleItemCount(): Int = performanceItems.count { it.isVisible }

    private fun showInfoByIndex(index: Int) {
        var currentIndex = 0
        for (itemInfo in performanceItems) {
            if (itemInfo.isVisible) {
                if (currentIndex == index) {
                    itemInfo.infoMethod.run()
                    return
                }
                currentIndex++
            }
        }
        showMoonPhaseInfo()
    }

    private fun showMoonPhaseInfo() {
        val moonPhaseInfo = MoonPhaseUtils.getCurrentMoonPhaseInfo()
        val moonPhase = MoonPhaseUtils.getCurrentMoonPhase()
        val phasePercentage = MoonPhaseUtils.getMoonPhasePercentage(moonPhase)
        val daysInCycle = MoonPhaseUtils.getDaysInMoonCycle(moonPhase)

        val dateFormat = SimpleDateFormat(activity.getString(R.string.perf_moon_date_format), Locale.getDefault())
        val currentDate = dateFormat.format(Calendar.getInstance(TimeZone.getDefault()).time)

        val moonInfo = String.format(
            activity.getString(R.string.perf_moon_phase_info),
            moonPhaseInfo.icon, moonPhaseInfo.name, phasePercentage, daysInCycle, currentDate, moonPhaseInfo.description
        )

        showMoonPhaseDialog(moonPhaseInfo.poeticTitle, moonInfo)
    }

    private fun showMoonPhaseDialog(title: String, message: String) {
        AlertDialog.Builder(activity, R.style.AppDialogStyle)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok", null)
            .setCancelable(true)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun showResolutionInfo() {
        val perfInfo = currentPerformanceInfo
        if (perfInfo == null) {
            showMoonPhaseInfo()
            return
        }

        val scalePercent = prefConfig.resolutionScale
        val scaleFactor = scalePercent / 100.0f
        val hostWidth = (perfInfo.initialWidth * scaleFactor).toInt()
        val hostHeight = (perfInfo.initialHeight * scaleFactor).toInt()

        val resolutionInfo = StringBuilder()
        resolutionInfo.append("Client Resolution: ").append(perfInfo.initialWidth)
            .append(" × ").append(perfInfo.initialHeight).append("\n")
        resolutionInfo.append("Host Resolution: ").append(hostWidth)
            .append(" × ").append(hostHeight).append("\n")
        resolutionInfo.append("Scale Factor: ").append(String.format("%.2f", scaleFactor))
            .append(" (").append(scalePercent).append("%)\n")

        val deviceRefreshRate = UiHelper.getDeviceRefreshRate(activity)
        resolutionInfo.append("Target FPS: ").append(prefConfig.fps).append(" FPS\n")
        resolutionInfo.append("Current FPS: ").append(String.format("%.0f", perfInfo.totalFps)).append(" FPS\n")
        resolutionInfo.append("Device Refresh Rate: ").append(String.format("%.0f", deviceRefreshRate)).append(" Hz\n")

        if (actualDisplayRefreshRate > 0) {
            resolutionInfo.append("Actual Display Refresh Rate: ").append(String.format("%.2f", actualDisplayRefreshRate)).append(" Hz\n")
        }

        showInfoDialog("📱 Resolution Information", resolutionInfo.toString())
    }

    private fun showDecoderInfo() {
        val fullDecoderInfo = getCurrentDecoderInfo()
        showInfoDialog(activity.getString(R.string.perf_decoder_title), fullDecoderInfo)
    }

    private fun getCurrentDecoderInfo(): String {
        val decoderInfo = StringBuilder()
        val perfInfo = currentPerformanceInfo
        if (perfInfo != null) {
            decoderInfo.append("Codec: ").append(perfInfo.decoder).append("\n\n")
            val decoderTypeInfo = getDecoderTypeInfo(perfInfo.decoder)
            decoderInfo.append("Type: ").append(decoderTypeInfo.fullName).append("\n")
            decoderInfo.append("HDR: ").append(if (perfInfo.isHdrActive) "Enabled" else "Disabled").append("\n")
        }
        decoderInfo.append(activity.getString(R.string.perf_decoder_info))
        return decoderInfo.toString()
    }

    private fun getDecoderTypeInfo(fullDecoderName: String?): DecoderTypeInfo {
        if (fullDecoderName == null) {
            return DecoderTypeInfo("Unknown", "Unknown")
        }

        val lowerName = fullDecoderName.lowercase()

        for ((key, value) in DECODER_TYPE_MAP) {
            if (lowerName.contains(key)) {
                return value
            }
        }

        val parts = fullDecoderName.split(".")
        if (parts.isNotEmpty()) {
            val extractedName = parts.last()
            return DecoderTypeInfo(fullDecoderName, extractedName.uppercase())
        }

        return DecoderTypeInfo(fullDecoderName, fullDecoderName)
    }

    private fun showPerformanceInfo(titleResId: Int, infoResId: Int) {
        showInfoDialog(activity.getString(titleResId), activity.getString(infoResId))
    }

    private fun showFpsInfo() {
        AlertDialog.Builder(activity, R.style.AppDialogStyle)
            .setTitle(R.string.perf_fps_title)
            .setView(createFpsInfoContent())
            .setPositiveButton(activity.getString(R.string.yes), null)
            .setCancelable(true)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun createFpsInfoContent(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), 0)

            addView(createJitterMonitorCheckboxRow(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            addView(createFpsInfoScrollView(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun createFpsInfoScrollView(): View {
        return ScrollView(activity).apply {
            addView(TextView(activity).apply {
                text = activity.getString(R.string.perf_fps_info)
                setTextColor(ContextCompat.getColor(activity, R.color.app_dialog_title_color))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.08f)
                setPadding(0, dp(14f), 0, 0)
            })
        }
    }

    private fun createJitterMonitorCheckboxRow(): View {
        val jitterCheckbox = CheckBox(activity).apply {
            isChecked = prefConfig.enableJitterMonitor
            text = ""
            minWidth = dp(48f)
            minHeight = dp(48f)
            buttonTintList = createJitterCheckboxTint()
            setOnCheckedChangeListener { _, isChecked ->
                setJitterMonitorEnabled(isChecked)
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = dp(58f)
            setPadding(0, dp(4f), 0, dp(6f))
            setOnClickListener { jitterCheckbox.isChecked = !jitterCheckbox.isChecked }

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.title_enable_jitter_monitor)
                    setTextColor(ContextCompat.getColor(activity, R.color.app_dialog_title_color))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    includeFontPadding = false
                })
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.summary_enable_jitter_monitor)
                    setTextColor(ContextCompat.getColor(activity, R.color.app_dialog_subtitle_color))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setLineSpacing(0f, 1.05f)
                    setPadding(0, dp(4f), dp(12f), 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(jitterCheckbox, LinearLayout.LayoutParams(dp(56f), dp(48f)))
        }
    }

    private fun createJitterCheckboxTint(): ColorStateList {
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val defaultState = intArrayOf()
        val states = arrayOf(checkedState, defaultState)
        val accent = ContextCompat.getColor(activity, R.color.app_dialog_accent_color)
        val secondary = ContextCompat.getColor(activity, R.color.app_dialog_subtitle_color)

        return ColorStateList(
            states,
            intArrayOf(accent, colorWithAlpha(secondary, 180))
        )
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun setJitterMonitorEnabled(enabled: Boolean) {
        if (prefConfig.enableJitterMonitor == enabled) return
        prefConfig.enableJitterMonitor = enabled
        prefConfig.writePreferences(activity)
        onJitterMonitorChanged(enabled)
    }

    private fun showPacketLossInfo() {
        showPerformanceInfo(R.string.perf_packet_loss_title, R.string.perf_packet_loss_info)
    }

    private fun showNetworkLatencyInfo() {
        showPerformanceInfo(R.string.perf_network_latency_title, R.string.perf_network_latency_info)
    }

    private fun showDecodeLatencyInfo() {
        showPerformanceInfo(R.string.perf_decode_latency_title, R.string.perf_decode_latency_info)
    }

    private fun showHostLatencyInfo() {
        showPerformanceInfo(R.string.perf_host_latency_title, R.string.perf_host_latency_info)
    }

    private fun showOnePercentLowInfo() {
        showPerformanceInfo(R.string.perf_one_percent_low_title, R.string.perf_one_percent_low_info)
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(activity, R.style.AppDialogStyle)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.yes), null)
            .setCancelable(true)
            .show()
            .also { AppDialogStyler.installDismissKeys(it) }
    }

    private fun toggleLayoutOrientation() {
        prefConfig.perfOverlayOrientation = if (prefConfig.perfOverlayOrientation == PreferenceConfiguration.PerfOverlayOrientation.VERTICAL) {
            PreferenceConfiguration.PerfOverlayOrientation.HORIZONTAL
        } else {
            PreferenceConfiguration.PerfOverlayOrientation.VERTICAL
        }
        saveLayoutOrientation()
        configurePerformanceOverlay()
    }

    private fun saveLayoutOrientation() {
        val prefs = overlayPrefs
        prefs.edit {
            putString("layout_orientation", prefConfig.perfOverlayOrientation.name)
        }
    }

    private fun loadLayoutOrientation() {
        val prefs = overlayPrefs
        val savedOrientation = prefs.getString("layout_orientation", null)

        if (savedOrientation != null) {
            try {
                prefConfig.perfOverlayOrientation = PreferenceConfiguration.PerfOverlayOrientation.valueOf(savedOrientation)
            } catch (_: IllegalArgumentException) {
                prefConfig.perfOverlayOrientation = PreferenceConfiguration.PerfOverlayOrientation.VERTICAL
            }
        }
    }

    private fun isClick(event: MotionEvent): Boolean {
        val deltaX = abs(event.rawX - perfOverlayStartX)
        val deltaY = abs(event.rawY - perfOverlayStartY)
        val deltaTime = System.currentTimeMillis() - clickStartTime
        return deltaX < CLICK_THRESHOLD && deltaY < CLICK_THRESHOLD && deltaTime < 500
    }

    private fun snapToNearestPosition(view: View) {
        val parentDimensions = getParentDimensions(view)
        val viewDimensions = getViewDimensions(view)
        val screenWidth = parentDimensions[0]
        val screenHeight = parentDimensions[1]
        val viewWidth = viewDimensions[0]
        val viewHeight = viewDimensions[1]

        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
        val currentX = layoutParams.leftMargin + viewWidth / 2
        val currentY = layoutParams.topMargin + viewHeight / 2

        val snapPositions = arrayOf(
            intArrayOf(viewWidth / 2, viewHeight / 2),
            intArrayOf(screenWidth / 2, viewHeight / 2),
            intArrayOf(screenWidth - viewWidth / 2, viewHeight / 2),
            intArrayOf(viewWidth / 2, screenHeight / 2),
            intArrayOf(screenWidth - viewWidth / 2, screenHeight / 2),
            intArrayOf(viewWidth / 2, screenHeight - viewHeight / 2),
            intArrayOf(screenWidth / 2, screenHeight - viewHeight / 2),
            intArrayOf(screenWidth - viewWidth / 2, screenHeight - viewHeight / 2)
        )

        val positions = SnapPosition.entries.toTypedArray()
        var nearestPosition = SnapPosition.TOP_CENTER
        var minDistance = Double.MAX_VALUE

        for (i in snapPositions.indices) {
            val distance = sqrt(
                (currentX - snapPositions[i][0]).toDouble().pow(2.0) +
                        (currentY - snapPositions[i][1]).toDouble().pow(2.0)
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestPosition = positions[i]
            }
        }

        animateToSnapPosition(view, nearestPosition, screenWidth, screenHeight)
    }

    private fun animateToSnapPosition(view: View, position: SnapPosition, screenWidth: Int, screenHeight: Int) {
        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
        val viewDimensions = getViewDimensions(view)
        val viewWidth = viewDimensions[0]
        val viewHeight = viewDimensions[1]

        val (targetX, targetY) = when (position) {
            SnapPosition.TOP_LEFT -> 0 to 0
            SnapPosition.TOP_CENTER -> (screenWidth - viewWidth) / 2 to 0
            SnapPosition.TOP_RIGHT -> screenWidth - viewWidth to 0
            SnapPosition.CENTER_LEFT -> 0 to (screenHeight - viewHeight) / 2
            SnapPosition.CENTER_RIGHT -> screenWidth - viewWidth to (screenHeight - viewHeight) / 2
            SnapPosition.BOTTOM_LEFT -> 0 to screenHeight - viewHeight
            SnapPosition.BOTTOM_CENTER -> (screenWidth - viewWidth) / 2 to screenHeight - viewHeight
            SnapPosition.BOTTOM_RIGHT -> screenWidth - viewWidth to screenHeight - viewHeight
        }

        view.animate()
            .translationX((targetX - layoutParams.leftMargin).toFloat())
            .translationY((targetY - layoutParams.topMargin).toFloat())
            .setDuration(200)
            .withEndAction {
                layoutParams.leftMargin = targetX
                layoutParams.topMargin = targetY
                view.translationX = 0f
                view.translationY = 0f
                view.layoutParams = layoutParams
                savePerformanceOverlayPosition(targetX, targetY)
                configureTextAlignment()
            }
            .start()
    }

    private fun savePerformanceOverlayPosition(x: Int, y: Int) {
        val prefs = overlayPrefs
        prefs.edit {
            putBoolean("has_custom_position", true)
                .putInt("left_margin", x)
                .putInt("top_margin", y)
        }
    }

    private fun getViewDimensions(view: View): IntArray {
        var width = view.width
        var height = view.height
        if (width == 0) width = 300
        if (height == 0) height = 50
        return intArrayOf(width, height)
    }

    private fun getParentDimensions(view: View): IntArray {
        val parent = view.parent as View
        return intArrayOf(parent.width, parent.height)
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        activity.resources.displayMetrics
    ).toInt()

    companion object {
        private const val CLICK_THRESHOLD = 10
        private const val DOUBLE_CLICK_TIMEOUT = 300
        private const val BATTERY_UPDATE_INTERVAL_MS = 15000L
        private const val OVERLAY_BACKGROUND_RGB = 0x16

        private val DECODER_TYPE_MAP = mapOf(
            "avc" to DecoderTypeInfo("H.264/AVC", "AVC"),
            "h264" to DecoderTypeInfo("H.264/AVC", "AVC"),
            "hevc" to DecoderTypeInfo("H.265/HEVC", "HEVC"),
            "h265" to DecoderTypeInfo("H.265/HEVC", "HEVC"),
            "av1" to DecoderTypeInfo("AV1", "AV1"),
            "vp9" to DecoderTypeInfo("VP9", "VP9"),
            "vp8" to DecoderTypeInfo("VP8", "VP8")
        )
    }
}

/**
 * 居中对齐的 ImageSpan：基于字体度量将图标垂直居中到文字中线，
 * 解决默认 ALIGN_BOTTOM/ALIGN_BASELINE 偏低、与中文字符不齐的问题。
 */
private class CenterAlignedImageSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_BASELINE) {

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val d = drawable
        val fm = paint.fontMetricsInt
        // 文本内容垂直中线（不含 leading）
        val textCenter = y + (fm.descent + fm.ascent) / 2
        val transY = textCenter - d.bounds.height() / 2
        canvas.save()
        canvas.translate(x, transY.toFloat())
        d.draw(canvas)
        canvas.restore()
    }
}
