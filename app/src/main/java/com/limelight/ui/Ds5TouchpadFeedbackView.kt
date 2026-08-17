package com.limelight.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import com.limelight.R
import com.limelight.nvstream.jni.MoonBridge
import kotlin.math.max

/**
 * Non-interactive visual feedback for screen-backed DualSense touch contacts.
 * Coordinates are normalized so this view stays aligned with letterboxed streams.
 */
class Ds5TouchpadFeedbackView(context: Context) : View(context) {
    private data class Contact(
        var x: Float,
        var y: Float,
        var releasedAt: Long = 0L,
    )

    private val density = resources.displayMetrics.density
    private val contacts = LinkedHashMap<Int, Contact>(2)

    private val contactFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val contactStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDC171923.toInt()
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(14f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCFFFFFFF.toInt()
        textSize = sp(11f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8AB4F8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private var introStartedAt = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        elevation = 6f * density
    }

    fun showActivatedHint() {
        introStartedAt = SystemClock.uptimeMillis()
        invalidate()
    }

    fun updateContact(eventType: Byte, pointerId: Int, normalizedX: Float, normalizedY: Float) {
        val now = SystemClock.uptimeMillis()
        when (eventType) {
            MoonBridge.LI_TOUCH_EVENT_DOWN,
            MoonBridge.LI_TOUCH_EVENT_MOVE -> {
                val contact = contacts.getOrPut(pointerId) { Contact(normalizedX, normalizedY) }
                contact.x = normalizedX
                contact.y = normalizedY
                contact.releasedAt = 0L
            }
            MoonBridge.LI_TOUCH_EVENT_UP,
            MoonBridge.LI_TOUCH_EVENT_CANCEL -> {
                val contact = contacts.getOrPut(pointerId) { Contact(normalizedX, normalizedY) }
                contact.x = normalizedX
                contact.y = normalizedY
                contact.releasedAt = now
            }
        }
        postInvalidateOnAnimation()
    }

    fun cancelAllContacts() {
        val now = SystemClock.uptimeMillis()
        contacts.values.forEach { it.releasedAt = now }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        var needsNextFrame = drawContacts(canvas, now)
        needsNextFrame = drawActivatedHint(canvas, now) || needsNextFrame
        if (needsNextFrame) postInvalidateOnAnimation()
    }

    private fun drawContacts(canvas: Canvas, now: Long): Boolean {
        val radius = 18f * density
        var animating = false
        val iterator = contacts.iterator()
        while (iterator.hasNext()) {
            val (_, contact) = iterator.next()
            val alpha = if (contact.releasedAt == 0L) {
                1f
            } else {
                val fraction = (now - contact.releasedAt).toFloat() / CONTACT_FADE_MS
                if (fraction >= 1f) {
                    iterator.remove()
                    continue
                }
                animating = true
                1f - fraction
            }
            val x = contact.x.coerceIn(0f, 1f) * width
            val y = contact.y.coerceIn(0f, 1f) * height
            contactFillPaint.alpha = (42 * alpha).toInt()
            contactStrokePaint.alpha = (220 * alpha).toInt()
            canvas.drawCircle(x, y, radius, contactFillPaint)
            canvas.drawCircle(x, y, radius, contactStrokePaint)
            canvas.drawCircle(x, y, 3.5f * density, contactFillPaint.apply {
                this.alpha = (210 * alpha).toInt()
            })
        }
        return animating
    }

    private fun drawActivatedHint(canvas: Canvas, now: Long): Boolean {
        if (introStartedAt == 0L) return false
        val elapsed = now - introStartedAt
        if (elapsed >= INTRO_DURATION_MS) {
            introStartedAt = 0L
            return false
        }

        val fade = when {
            elapsed < INTRO_FADE_MS -> elapsed.toFloat() / INTRO_FADE_MS
            elapsed > INTRO_DURATION_MS - INTRO_FADE_MS ->
                (INTRO_DURATION_MS - elapsed).toFloat() / INTRO_FADE_MS
            else -> 1f
        }.coerceIn(0f, 1f)

        val title = resources.getString(R.string.ds5_touchpad_active_title)
        val subtitle = resources.getString(R.string.ds5_touchpad_active_subtitle)
        val horizontalPadding = 18f * density
        val iconWidth = 30f * density
        val iconGap = 12f * density
        val textWidth = max(titlePaint.measureText(title), subtitlePaint.measureText(subtitle))
        val pillWidth = horizontalPadding * 2 + iconWidth + iconGap + textWidth
        val pillHeight = 58f * density
        val left = (width - pillWidth) / 2f
        val top = 24f * density

        canvas.saveLayerAlpha(null, (255 * fade).toInt())
        canvas.drawRoundRect(
            RectF(left, top, left + pillWidth, top + pillHeight),
            18f * density,
            18f * density,
            pillPaint,
        )

        val iconLeft = left + horizontalPadding
        val iconTop = top + 19f * density
        canvas.drawRoundRect(
            RectF(iconLeft, iconTop, iconLeft + iconWidth, iconTop + 20f * density),
            5f * density,
            5f * density,
            iconPaint,
        )
        canvas.drawCircle(iconLeft + 10f * density, iconTop + 10f * density, 2f * density, iconPaint)
        canvas.drawCircle(iconLeft + 20f * density, iconTop + 10f * density, 2f * density, iconPaint)

        val textLeft = iconLeft + iconWidth + iconGap
        canvas.drawText(title, textLeft, top + 24f * density, titlePaint)
        canvas.drawText(subtitle, textLeft, top + 43f * density, subtitlePaint)
        canvas.restore()
        return true
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private companion object {
        const val CONTACT_FADE_MS = 150f
        const val INTRO_DURATION_MS = 2600L
        const val INTRO_FADE_MS = 240L
    }
}
