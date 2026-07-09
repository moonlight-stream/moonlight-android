package com.limelight.binding.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.limelight.R
import kotlin.math.max

/**
 * 抖动监控图表 View。绘制：
 *  - 顶部摘要：模式 / fps / judder% / jit(MAD) ms / 平均间隔
 *  - 时间线：最近若干帧的呈现间隔柱状（按偏离众数的 vsync 数着色：众数=绿，±1=黄，≥±2=红）
 *  - 直方图：各 vsync 单位的占比分布
 *
 * 由 [JitterMonitorManager] 在 UI 线程调用 [update] 传入快照后 invalidate。
 * 不做任何采样/计算，纯绘制；关闭时整个 View GONE，onDraw 不触发。
 */
class JitterMonitorView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(165, 16, 18, 22) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = Color.argb(70, 255, 255, 255)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255); strokeWidth = dp(1f)
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 100, 210, 100); strokeWidth = dp(0.9f)
    }
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255); strokeWidth = dp(0.8f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 235, 235, 240); textSize = sp(9.5f); isFakeBoldText = true
    }
    private val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = sp(12f); isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 210, 210, 214); textSize = sp(8f)
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 225, 225, 228); textSize = sp(6.5f); textAlign = Paint.Align.CENTER
    }
    private val emptyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 235, 235, 240)
        textSize = sp(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val emptyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 210, 210, 214)
        textSize = sp(8f)
        textAlign = Paint.Align.CENTER
    }
    private val bgRect = RectF()
    private val emptyTitleText = context.getString(R.string.jitter_monitor_empty_title)
    private val emptyMessageText = context.getString(R.string.jitter_monitor_empty_message)

    // 本地绘制缓冲（在 update 里从快照拷贝，onDraw 只读本地副本）
    private var count = 0
    private val unitsBuf = IntArray(JitterMonitor.CAPACITY)
    private var histTotal = 0L
    private val histBuf = LongArray(16)
    private var histBuckets = 0
    private var modeUnits = 0
    private var judderPct = 0f
    private var jitterMs = 0f
    private var avgIntervalMs = 0f
    private var fps = 0f
    private var modeLabel = "-"
    private var hasData = false

    /** UI 线程调用：拷贝快照到本地缓冲并请求重绘。 */
    fun update(s: JitterMonitor.Snapshot) {
        count = s.count
        System.arraycopy(s.units, 0, unitsBuf, 0, s.count)
        histBuckets = s.histogram.size
        System.arraycopy(s.histogram, 0, histBuf, 0, s.histogram.size)
        histTotal = s.histTotal
        modeUnits = s.modeUnits
        judderPct = s.judderPct
        jitterMs = s.jitterMs
        avgIntervalMs = s.avgIntervalMs
        fps = s.fps
        modeLabel = s.modeLabel
        hasData = s.count > 0
        invalidate()
    }

    fun showEmptyState() {
        val hadVisibleData = hasData || count != 0 || histTotal != 0L || histBuckets != 0
        count = 0
        histTotal = 0L
        histBuckets = 0
        hasData = false
        if (hadVisibleData) {
            invalidate()
        }
    }

    private fun colorForUnits(u: Int): Int {
        val d = kotlin.math.abs(u - modeUnits)
        return when (d) {
            0 -> Color.rgb(0x4C, 0xC2, 0x4C)   // 众数：绿（平滑）
            1 -> Color.rgb(0xE6, 0xB0, 0x2E)   // ±1：黄（轻微错拍）
            else -> Color.rgb(0xD9, 0x43, 0x3B) // ≥±2：红（明显判抖/丢帧）
        }
    }

    private fun drawEmptyState(canvas: Canvas, width: Float, height: Float) {
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        canvas.drawText(
            emptyTitleText,
            centerX,
            centerY - dp(5f),
            emptyTitlePaint
        )
        canvas.drawText(
            emptyMessageText,
            centerX,
            centerY + dp(15f),
            emptyLabelPaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(7f)
        val inset = dp(0.5f)
        bgRect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(bgRect, r, r, bgPaint)
        canvas.drawRoundRect(bgRect, r, r, borderPaint)
        if (!hasData) {
            drawEmptyState(canvas, w, h)
            return
        }

        val padX = dp(10f)
        val right = w - padX
        var y = dp(4f)

        // ---- 标题 ----
        y += titlePaint.textSize
        canvas.drawText("画面流畅度 · ${modeLabel}", padX, y, titlePaint)

        // ---- 主指标（卡顿率大字，随健康度着色）----
        val judderColor = when {
            judderPct < 8f -> Color.rgb(0x63, 0xD0, 0x6B)
            judderPct < 20f -> Color.rgb(0xE6, 0xC0, 0x4E)
            else -> Color.rgb(0xE0, 0x6C, 0x60)
        }
        y += dp(6f) + metricPaint.textSize
        metricPaint.color = judderColor
        canvas.drawText(String.format("卡顿 %.1f%%", judderPct), padX, y, metricPaint)
        metricPaint.color = Color.WHITE
        val fpsText = String.format("%.0f 帧/秒", fps)
        canvas.drawText(fpsText, right - metricPaint.measureText(fpsText), y, metricPaint)

        // ---- 次级指标 ----
        y += dp(4f) + labelPaint.textSize
        canvas.drawText(
            String.format(
                "抖动 %.2fms · 平均每帧 %.2fms · 采样 %d",
                jitterMs, avgIntervalMs, histTotal
            ),
            padX, y, labelPaint
        )

        // ---- 分隔线 ----
        y += dp(7f)
        canvas.drawLine(padX, y, right, y, sepPaint)
        y += dp(8f)

        // ---- 时间线（呈现间隔柱状）----
        val timelineTop = y
        val timelineH = dp(40f)
        val timelineBottom = timelineTop + timelineH
        val plotW = right - padX
        val maxUnitForScale = max(modeUnits + 3, 4)

        // 众数基准线（"理想节拍"高度）
        val modeFrac = (modeUnits.toFloat() / maxUnitForScale).coerceIn(0.05f, 1f)
        val baselineY = timelineBottom - timelineH * modeFrac
        canvas.drawLine(padX, baselineY, right, baselineY, baselinePaint)
        // 底轴
        canvas.drawLine(padX, timelineBottom, right, timelineBottom, gridPaint)

        if (count > 0) {
            val barW = max(dp(1f), plotW / count)
            for (i in 0 until count) {
                val u = unitsBuf[i]
                val frac = (u.toFloat() / maxUnitForScale).coerceIn(0.04f, 1f)
                val barH = timelineH * frac
                val left = padX + i * barW
                barPaint.color = colorForUnits(u)
                canvas.drawRect(left, timelineBottom - barH, left + barW * 0.85f, timelineBottom, barPaint)
            }
        }
        y = timelineBottom + labelPaint.textSize + dp(3f)
        canvas.drawText("每帧间隔（绿=流畅 · 黄/红=卡顿）", padX, y, labelPaint)
        y += dp(8f)

        // ---- 直方图 ----
        val histTop = y
        val histH = dp(26f)
        val histBottom = histTop + histH
        var maxCount = 1L
        for (i in 0 until histBuckets) if (histBuf[i] > maxCount) maxCount = histBuf[i]
        canvas.drawLine(padX, histBottom, right, histBottom, gridPaint)
        val slotW = plotW / histBuckets
        for (u in 0 until histBuckets) {
            val c = histBuf[u]
            val barH = if (maxCount > 0) histH * (c.toFloat() / maxCount) else 0f
            val left = padX + u * slotW
            val cx = left + slotW * 0.5f
            barPaint.color = colorForUnits(u)
            canvas.drawRect(
                left + slotW * 0.18f, histBottom - barH,
                left + slotW * 0.82f, histBottom, barPaint
            )
            // 占比标签（>=1%时显示）
            if (histTotal > 0) {
                val pct = 100f * c / histTotal
                if (pct >= 1f) {
                    canvas.drawText(String.format("%.0f", pct), cx, histBottom - barH - dp(2f), countPaint)
                }
            }
            // 单位刻度
            canvas.drawText("$u", cx, histBottom + labelPaint.textSize + dp(1f), countPaint)
        }
        y = histBottom + labelPaint.textSize + dp(3f)
        canvas.drawText("横轴：每帧占几个屏幕刷新（集中一处=稳）", padX, y + labelPaint.textSize + dp(1f), labelPaint)
    }
}
