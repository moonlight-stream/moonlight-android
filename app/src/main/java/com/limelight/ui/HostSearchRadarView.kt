package com.limelight.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.limelight.R
import kotlin.math.min

/**
 * A small, self-contained discovery indicator for the empty PC screen.
 *
 * The expanding rings deliberately use only opacity and scale so the animation remains cheap on
 * older devices. The view also stops its animator whenever it is detached or not visible.
 */
class HostSearchRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.add_pc_accent)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_space_moon_light)
        style = Paint.Style.FILL
    }
    private val coreHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_space_moon_highlight)
        style = Paint.Style.FILL
    }
    private val moonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_space_moon_shadow)
        style = Paint.Style.FILL
    }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_space_moon_crater)
        style = Paint.Style.FILL
    }
    private val moonOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.add_pc_accent)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val moonClipPath = Path()

    private var phase = 0f
    private var animator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val coreRadius = size * 0.22f
        val maxRingRadius = size * 0.47f

        repeat(3) { index ->
            val progress = (phase + index / 3f) % 1f
            val radius = coreRadius + (maxRingRadius - coreRadius) * progress
            ringPaint.alpha = (105 * (1f - progress)).toInt().coerceIn(0, 105)
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }

        // The complete discovery core is the moon itself. A clipped offset shadow produces the
        // vintage lunar terminator while keeping a crisp circular silhouette.
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
        moonClipPath.reset()
        moonClipPath.addCircle(cx, cy, coreRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(moonClipPath)
        canvas.drawCircle(cx - coreRadius * 0.78f, cy, coreRadius * 1.12f, moonShadowPaint)
        coreHighlightPaint.alpha = 72
        canvas.drawCircle(cx + coreRadius * 0.34f, cy - coreRadius * 0.38f, coreRadius * 0.70f, coreHighlightPaint)

        craterPaint.alpha = 150
        canvas.drawCircle(cx + coreRadius * 0.28f, cy - coreRadius * 0.18f, coreRadius * 0.14f, craterPaint)
        canvas.drawCircle(cx + coreRadius * 0.10f, cy + coreRadius * 0.34f, coreRadius * 0.09f, craterPaint)
        canvas.drawCircle(cx + coreRadius * 0.48f, cy + coreRadius * 0.22f, coreRadius * 0.06f, craterPaint)
        canvas.restore()
        canvas.drawCircle(cx, cy, coreRadius, moonOutlinePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimationState()
    }

    private fun updateAnimationState() {
        if (isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }
}
