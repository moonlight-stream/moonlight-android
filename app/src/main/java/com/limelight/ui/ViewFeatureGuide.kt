package com.limelight.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.limelight.R
import kotlin.math.max
import kotlin.math.min

data class ViewFeatureGuideStep(
    val targetProvider: () -> View?,
    val title: String,
    val body: String
) {
    constructor(target: View, title: String, body: String) : this({ target }, title, body)
}

object ViewFeatureGuide {
    private const val OVERLAY_TAG = "moonlight_view_feature_guide"
    private const val DEFAULT_READY_TIMEOUT_MS = 5_000L
    private const val READY_RETRY_MS = 120L

    fun show(
        activity: Activity,
        spec: FeatureGuideSpec,
        steps: List<ViewFeatureGuideStep>
    ): Boolean {
        if (steps.isEmpty() || activity.isFinishing || activity.isDestroyed) return false
        val store = FeatureGuideStore(activity)
        if (!store.shouldShow(spec)) return false

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        if (content.findViewWithTag<View>(OVERLAY_TAG) != null) return false

        val visibleSteps = steps.filter {
            val target = it.targetProvider()
            target != null && target.isShown && target.width > 0 && target.height > 0
        }
        if (visibleSteps.isEmpty()) return false

        val overlay = FeatureGuideOverlay(
            activity = activity,
            steps = visibleSteps,
            onCompleted = { store.markCompleted(spec) }
        ).apply { tag = OVERLAY_TAG }
        content.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return true
    }

    /** Wait for a real visible target instead of relying on a startup delay. */
    fun showWhenReady(
        activity: Activity,
        spec: FeatureGuideSpec,
        timeoutMillis: Long = DEFAULT_READY_TIMEOUT_MS,
        stepsProvider: () -> List<ViewFeatureGuideStep>
    ) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val store = FeatureGuideStore(activity)
        if (!store.shouldShow(spec)) return

        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        val attempt = object : Runnable, View.OnAttachStateChangeListener {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed || !store.shouldShow(spec)) {
                    stop()
                    return
                }
                if (activity.hasWindowFocus() && show(activity, spec, stepsProvider())) {
                    stop()
                    return
                }
                if (SystemClock.uptimeMillis() < deadline) {
                    content.postDelayed(this, READY_RETRY_MS)
                } else {
                    stop()
                }
            }

            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) = stop()

            private fun stop() {
                content.removeCallbacks(this)
                content.removeOnAttachStateChangeListener(this)
            }
        }
        content.addOnAttachStateChangeListener(attempt)
        content.post(attempt)
    }
}

/**
 * Only the hand-drawn decoration lives on Canvas. Text, scrolling and actions are
 * ordinary Android views so sizing, keyboard focus and accessibility stay native.
 */
@SuppressLint("ViewConstructor")
private class FeatureGuideOverlay(
    private val activity: Activity,
    private val steps: List<ViewFeatureGuideStep>,
    private val onCompleted: () -> Unit
) : FrameLayout(activity) {
    private val density = resources.displayMetrics.density
    private val accent = ContextCompat.getColor(activity, R.color.game_menu_accent)
    private val ink = Color.rgb(76, 67, 70)
    private val mutedInk = Color.rgb(108, 96, 99)
    private val paper = Color.rgb(255, 248, 232)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(184, 0, 0, 0) }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val leaderPaint = Paint(borderPaint).apply {
        strokeWidth = dp(2.1f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
    private val paperBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(216, 202, 188)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
    }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 151, 143)
        alpha = 232
    }
    private val tapeStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
        strokeWidth = dp(1f)
    }
    private val highlightRect = RectF()
    private val highlightEchoRect = RectF()
    private val cardRect = RectF()
    private var currentIndex = 0
    private var dismissScheduled = false

    private val card = FrameLayout(activity).apply {
        isClickable = true
        isFocusable = false
    }
    private val contentColumn = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpInt(24f), dpInt(12f), dpInt(24f), dpInt(8f))
    }
    private val eyebrow = label(11f, accent, true).apply {
        letterSpacing = 0.06f
    }
    private val title = label(21f, ink, false).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.025f
        setLineSpacing(dp(1f), 1.04f)
    }
    private val body = label(16f, mutedInk, false).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.018f
        setLineSpacing(dp(2f), 1.12f)
    }
    private val scroll = ScrollView(activity).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            contentColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }
    private val skip = actionLabel(mutedInk) { dismiss(completed = false) }
    private val action = actionLabel(accent) { performPrimaryAction() }
    private val actions = LinearLayout(activity).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        setPadding(dpInt(12f), 0, dpInt(12f), dpInt(5f))
        addView(skip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(View(activity).apply { setBackgroundColor(Color.rgb(216, 202, 188)) },
            LinearLayout.LayoutParams(dpInt(1f), dpInt(24f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dpInt(2f)
                marginEnd = dpInt(2f)
            })
        addView(action, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        contentColumn.addView(eyebrow)
        contentColumn.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpInt(3f)
        })
        contentColumn.addView(HandDrawnUnderline(activity, accent),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpInt(13f)))
        contentColumn.addView(body)

        card.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            bottomMargin = dpInt(ACTION_HEIGHT_DP)
        })
        card.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, dpInt(ACTION_HEIGHT_DP), Gravity.BOTTOM))
        addView(card)
        updateContent()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val overlayWidth = MeasureSpec.getSize(widthMeasureSpec)
        val overlayHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(overlayWidth, overlayHeight)

        val edge = dp(16f)
        val cardWidth = min(dp(316f), overlayWidth - edge * 2f).toInt().coerceAtLeast(1)
        contentColumn.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val naturalHeight = contentColumn.measuredHeight + dpInt(ACTION_HEIGHT_DP)
        val availableHeight = (overlayHeight - edge * 2f).toInt().coerceAtLeast(1)
        val cardHeight = min(naturalHeight, availableHeight).coerceAtLeast(min(dpInt(150f), availableHeight))
        card.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!updateTargetRect()) {
            scheduleDismiss()
            return
        }
        val edge = dp(16f)
        val cardWidth = card.measuredWidth.toFloat()
        val cardHeight = card.measuredHeight.toFloat()
        val cardLeft = (highlightRect.centerX() - cardWidth / 2f)
            .coerceIn(edge, max(edge, width - edge - cardWidth))
        val belowTop = highlightRect.bottom + dp(64f)
        val aboveTop = highlightRect.top - dp(64f) - cardHeight
        val preferredTop = when {
            belowTop + cardHeight <= height - edge -> belowTop
            aboveTop >= edge -> aboveTop
            else -> ((height - cardHeight) / 2f).coerceAtLeast(edge)
        }
        val cardTop = preferredTop.coerceIn(edge, max(edge, height - edge - cardHeight))
        card.layout(
            cardLeft.toInt(),
            cardTop.toInt(),
            (cardLeft + cardWidth).toInt(),
            (cardTop + cardHeight).toInt()
        )
        cardRect.set(card.left.toFloat(), card.top.toFloat(), card.right.toFloat(), card.bottom.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!updateTargetRect()) {
            scheduleDismiss()
            return
        }

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRoundRect(highlightRect, dp(14f), dp(14f), clearPaint)
        canvas.restoreToCount(layer)

        borderPaint.alpha = 225
        canvas.drawRoundRect(highlightRect, dp(14f), dp(14f), borderPaint)
        highlightEchoRect.set(highlightRect)
        highlightEchoRect.inset(-dp(3f), -dp(2f))
        borderPaint.alpha = 90
        canvas.drawRoundRect(highlightEchoRect, dp(16f), dp(16f), borderPaint)
        borderPaint.alpha = 255

        drawLeader(canvas)
        val paperPath = paperPath(cardRect)
        paperPaint.setShadowLayer(dp(5f), 0f, dp(2f), Color.argb(72, 0, 0, 0))
        canvas.drawPath(paperPath, paperPaint)
        paperPaint.clearShadowLayer()
        canvas.drawPath(paperPath, paperBorderPaint)
        drawTape(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateTargetRect(): Boolean {
        val target = steps.getOrNull(currentIndex)?.targetProvider?.invoke() ?: return false
        if (!target.isShown) return false
        val targetBounds = Rect()
        val ownBounds = Rect()
        if (!target.getGlobalVisibleRect(targetBounds) || !getGlobalVisibleRect(ownBounds)) return false
        val margin = dp(8f)
        highlightRect.set(
            targetBounds.left - ownBounds.left - margin,
            targetBounds.top - ownBounds.top - margin,
            targetBounds.right - ownBounds.left + margin,
            targetBounds.bottom - ownBounds.top + margin
        )
        highlightRect.left = highlightRect.left.coerceAtLeast(dp(6f))
        highlightRect.top = highlightRect.top.coerceAtLeast(dp(6f))
        highlightRect.right = highlightRect.right.coerceAtMost(width - dp(6f))
        highlightRect.bottom = highlightRect.bottom.coerceAtMost(height - dp(6f))
        return true
    }

    private fun performPrimaryAction() {
        if (currentIndex == steps.lastIndex) {
            dismiss(completed = true)
        } else {
            currentIndex++
            scroll.scrollTo(0, 0)
            updateContent()
            requestLayout()
            invalidate()
        }
    }

    private fun updateContent() {
        val step = steps[currentIndex]
        eyebrow.text = activity.getString(R.string.feature_guide_step, currentIndex + 1, steps.size)
        title.text = step.title
        body.text = step.body
        skip.text = activity.getString(R.string.feature_guide_skip)
        action.text = activity.getString(
            if (currentIndex == steps.lastIndex) R.string.feature_guide_done else R.string.feature_guide_next
        )
    }

    private fun drawLeader(canvas: Canvas) {
        val cardBelow = cardRect.top > highlightRect.bottom
        val startX = highlightRect.centerX()
        val startY = if (cardBelow) highlightRect.bottom + dp(3f) else highlightRect.top - dp(3f)
        val endX = cardRect.left + dp(76f)
        val endY = if (cardBelow) cardRect.top - dp(7f) else cardRect.bottom + dp(7f)
        val direction = if (cardBelow) 1f else -1f
        val verticalDistance = kotlin.math.abs(endY - startY)
        val path = Path().apply {
            moveTo(startX, startY)
            cubicTo(
                startX, startY + max(dp(28f), verticalDistance * 0.58f) * direction,
                endX + dp(42f), endY - dp(22f) * direction,
                endX, endY
            )
        }
        val pathMeasure = PathMeasure(path, false)
        val visibleLeader = Path()
        val sourceGap = dp(12f)
        val arrowGap = dp(21f)
        if (pathMeasure.length > sourceGap + arrowGap) {
            pathMeasure.getSegment(sourceGap, pathMeasure.length - arrowGap, visibleLeader, true)
        }
        leaderPaint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
        leaderPaint.style = Paint.Style.STROKE
        canvas.drawPath(visibleLeader, leaderPaint)
        leaderPaint.pathEffect = null

        val backX = 0.886f
        val backY = -0.464f * direction
        val sideX = 0.464f * direction
        val sideY = 0.886f
        val wingLength = dp(13f)
        val wingSpread = dp(5.5f)
        val arrow = Path().apply {
            moveTo(endX + backX * wingLength + sideX * wingSpread, endY + backY * wingLength + sideY * wingSpread)
            lineTo(endX, endY)
            lineTo(endX + backX * wingLength - sideX * wingSpread, endY + backY * wingLength - sideY * wingSpread)
        }
        canvas.drawPath(arrow, leaderPaint)
    }

    private fun paperPath(rect: RectF): Path {
        val wobble = dp(2f)
        return Path().apply {
            moveTo(rect.left + dp(7f), rect.top + wobble)
            lineTo(rect.left + rect.width() * 0.22f, rect.top)
            lineTo(rect.left + rect.width() * 0.48f, rect.top + wobble)
            lineTo(rect.left + rect.width() * 0.74f, rect.top - dp(1f))
            lineTo(rect.right - dp(7f), rect.top + wobble)
            quadTo(rect.right + dp(1f), rect.top + dp(8f), rect.right - dp(1f), rect.top + dp(16f))
            lineTo(rect.right + dp(1f), rect.bottom - dp(9f))
            quadTo(rect.right - dp(2f), rect.bottom + dp(2f), rect.right - dp(12f), rect.bottom)
            lineTo(rect.left + rect.width() * 0.72f, rect.bottom + dp(1f))
            lineTo(rect.left + rect.width() * 0.46f, rect.bottom - dp(1f))
            lineTo(rect.left + rect.width() * 0.20f, rect.bottom + dp(2f))
            lineTo(rect.left + dp(7f), rect.bottom)
            quadTo(rect.left - dp(1f), rect.bottom - dp(6f), rect.left + dp(1f), rect.bottom - dp(15f))
            lineTo(rect.left - dp(1f), rect.top + dp(11f))
            quadTo(rect.left + dp(1f), rect.top + dp(4f), rect.left + dp(7f), rect.top + wobble)
            close()
        }
    }

    private fun drawTape(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-9f, cardRect.left + dp(28f), cardRect.top + dp(5f))
        val tapeRect = RectF(
            cardRect.left + dp(4f), cardRect.top - dp(7f),
            cardRect.left + dp(54f), cardRect.top + dp(11f)
        )
        canvas.drawRoundRect(tapeRect, dp(2f), dp(2f), tapePaint)
        var x = tapeRect.left + dp(5f)
        while (x < tapeRect.right) {
            canvas.drawLine(x, tapeRect.top + dp(3f), x + dp(5f), tapeRect.bottom - dp(3f), tapeStripePaint)
            x += dp(8f)
        }
        canvas.restore()
    }

    private fun dismiss(completed: Boolean) {
        if (completed) onCompleted()
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun scheduleDismiss() {
        if (dismissScheduled) return
        dismissScheduled = true
        post { dismiss(completed = false) }
    }

    private fun label(sp: Float, color: Int, bold: Boolean) = TextView(activity).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        typeface = Typeface.create("sans-serif-rounded", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun actionLabel(color: Int, onClick: () -> Unit) = label(15f, color, true).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.035f
        gravity = Gravity.CENTER
        minHeight = dpInt(45f)
        setPadding(dpInt(12f), 0, dpInt(12f), 0)
        isClickable = true
        isFocusable = true
        background = selectableBackground()
        setOnClickListener { onClick() }
    }

    private fun selectableBackground(): Drawable? {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return ContextCompat.getDrawable(activity, value.resourceId)
    }

    private fun dp(value: Float): Float = value * density
    private fun dpInt(value: Float): Int = dp(value).toInt()

    private companion object {
        const val ACTION_HEIGHT_DP = 50f
    }
}

@SuppressLint("ViewConstructor")
private class HandDrawnUnderline(context: Context, color: Int) : View(context) {
    private val density = resources.displayMetrics.density
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        alpha = 210
    }

    override fun onDraw(canvas: Canvas) {
        val y = height * 0.45f
        val right = width.toFloat()
        path.reset()
        path.moveTo(0f, y)
        path.cubicTo(right * 0.22f, y - 2f * density, right * 0.45f, y + 2f * density, right * 0.66f, y)
        path.cubicTo(right * 0.79f, y - 1.5f * density, right * 0.91f, y + 1.5f * density, right, y - 0.5f * density)
        canvas.drawPath(path, paint)
    }
}
