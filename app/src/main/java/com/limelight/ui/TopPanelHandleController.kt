package com.limelight.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.limelight.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Owns the top-panel handle's rendering and transition state. */
internal class TopPanelHandleController(
    private val toggle: TextView
) {
    private val context = toggle.context
    private val density = toggle.resources.displayMetrics.density
    private val background = GradientDrawable()
    private val backgroundColor = color(R.color.settings_drawer_background)
    private val primaryColor = color(R.color.ui_shell_text_primary)
    private val secondaryColor = withAlpha(color(R.color.ui_shell_text_secondary), 0.78f)
    private val accentColor = color(R.color.game_menu_accent)
    private val outlineColor = color(R.color.ui_shell_outline)
    private val focusColor = color(R.color.ui_shell_accent)
    private val cornerRadii = FloatArray(8)
    private val moonWheel = MoonWheelDrawable(
        density = density,
        graphiteColor = primaryColor,
        accentColor = accentColor
    )

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val useStaticFallback = Build.VERSION.SDK_INT < Build.VERSION_CODES.M

    init {
        toggle.text = null
        if (useStaticFallback) {
            toggle.text = "—"
            toggle.gravity = Gravity.CENTER
            toggle.setTextColor(primaryColor)
            toggle.textSize = 24f
        } else {
            toggle.foreground = moonWheel
            toggle.foregroundGravity = Gravity.CENTER
        }
        background.shape = GradientDrawable.RECTANGLE
        toggle.background = background
        toggle.setOnFocusChangeListener { _, _ -> updateAppearance(progress) }
        updateAppearance(0f)
    }

    fun animate(expanded: Boolean, onPanelReveal: (() -> Unit)? = null) {
        if (useStaticFallback) {
            if (expanded) onPanelReveal?.invoke()
            return
        }
        val target = if (expanded) 1f else 0f
        toggle.animate().cancel()
        toggle.alpha = 1f
        animator?.cancel()

        val start = progress
        var panelRevealed = false
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = (240L * abs(target - start)).toLong().coerceAtLeast(80L)
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { valueAnimator ->
                val value = valueAnimator.animatedValue as Float
                updateAppearance(value)
                if (expanded && !panelRevealed && value >= 0.38f) {
                    panelRevealed = true
                    onPanelReveal?.invoke()
                }
            }
            start()
        }
    }

    fun release() {
        animator?.removeAllUpdateListeners()
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        toggle.animate().cancel()
        toggle.onFocusChangeListener = null
        if (!useStaticFallback) toggle.foreground = null
    }

    private fun updateAppearance(value: Float) {
        val fraction = value.coerceIn(0f, 1f)
        progress = fraction

        // Render-thread scaling avoids remeasuring the parent layout each frame.
        val handleWidth = toggle.width.takeIf { it > 0 }
            ?: toggle.layoutParams.width.takeIf { it > 0 }
            ?: (84f * density).roundToInt()
        toggle.scaleX = 1f - ((16f * density) / handleWidth) * fraction

        val topRadius = (18f - 4f * fraction) * density
        val bottomRadius = (18f - 16f * fraction) * density
        val chromeAlpha = ((1f - fraction) / 0.6f).coerceIn(0f, 1f)
        background.setColor(withAlpha(backgroundColor, chromeAlpha))
        cornerRadii[0] = topRadius
        cornerRadii[1] = topRadius
        cornerRadii[2] = topRadius
        cornerRadii[3] = topRadius
        cornerRadii[4] = bottomRadius
        cornerRadii[5] = bottomRadius
        cornerRadii[6] = bottomRadius
        cornerRadii[7] = bottomRadius
        background.cornerRadii = cornerRadii

        val showFocusRing = toggle.hasFocus() && fraction < 0.5f
        background.setStroke(
            ((if (showFocusRing) 2f else 1f) * density).roundToInt().coerceAtLeast(1),
            if (showFocusRing) {
                focusColor
            } else {
                withAlpha(outlineColor, chromeAlpha)
            }
        )

        moonWheel.progress = fraction
        moonWheel.graphiteColor = ColorUtils.blendARGB(
            primaryColor,
            secondaryColor,
            fraction
        )
        moonWheel.accentColor = accentColor
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(context, resourceId)

    private fun withAlpha(color: Int, multiplier: Float): Int = Color.argb(
        (Color.alpha(color) * multiplier.coerceIn(0f, 1f)).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}

@Suppress("OVERRIDE_DEPRECATION")
private class MoonWheelDrawable(
    private val density: Float,
    graphiteColor: Int,
    accentColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val wheelBounds = RectF()
    private var drawableAlpha = 255

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var graphiteColor: Int = graphiteColor
        set(value) {
            field = value
            invalidateSelf()
        }

    var accentColor: Int = accentColor
        set(value) {
            field = value
            invalidateSelf()
        }

    init {
        setBounds(0, 0, (24f * density).roundToInt(), (24f * density).roundToInt())
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val wheelAlpha = 1f - progress
        val wheelGraphite = multipliedAlpha(graphiteColor, wheelAlpha)
        val wheelAccent = multipliedAlpha(accentColor, wheelAlpha)

        val radius = 9f * density
        paint.color = wheelGraphite
        paint.strokeWidth = 1.8f * density
        wheelBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(wheelBounds, 112.5f, 315f, false, paint)

        val spokeLength = 6.5f * density
        val darkBottomLength = 4.2f * density
        for (index in 0 until 8) {
            val angle = Math.toRadians((-90f + index * 45f).toDouble())
            val xDirection = cos(angle).toFloat()
            val yDirection = sin(angle).toFloat()
            val length = if (index == 4) darkBottomLength else spokeLength
            val becomesBar = index == 2 || index == 6
            val animatedLength = if (becomesBar) {
                (6.5f + 0.5f * progress) * density
            } else {
                length
            }
            paint.color = if (becomesBar) graphiteColor else wheelGraphite
            paint.strokeWidth = (if (becomesBar) 1.55f + 0.45f * progress else 1.55f) * density
            canvas.drawLine(
                cx,
                cy,
                cx + xDirection * animatedLength,
                cy + yDirection * animatedLength,
                paint
            )

            if (index == 4) {
                paint.color = wheelAccent
                paint.strokeWidth = 1.7f * density
                canvas.drawLine(
                    cx + xDirection * darkBottomLength,
                    cy + yDirection * darkBottomLength,
                    cx + xDirection * (8.1f * density),
                    cy + yDirection * (8.1f * density),
                    paint
                )
            }
        }
    }

    private fun multipliedAlpha(color: Int, multiplier: Float): Int {
        val alpha = (Color.alpha(color) * multiplier * drawableAlpha / 255f)
            .roundToInt()
            .coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun getIntrinsicWidth(): Int = (24f * density).roundToInt()
    override fun getIntrinsicHeight(): Int = (24f * density).roundToInt()
    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
