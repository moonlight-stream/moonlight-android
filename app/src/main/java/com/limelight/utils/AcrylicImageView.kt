package com.limelight.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** ImageView that composites the acrylic foreground at draw time without a bitmap copy. */
class AcrylicImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var acrylicDrawable: Drawable? = null
    private var acrylicAlpha = 255
    private var acrylicEnabled = false
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setAcrylicDrawable(drawable: Drawable?, alpha: Int) {
        acrylicDrawable?.callback = null
        acrylicDrawable = drawable?.constantState?.newDrawable(resources)?.mutate() ?: drawable
        acrylicDrawable?.callback = this
        acrylicAlpha = alpha.coerceIn(0, 255)
        acrylicEnabled = true
        super.setImageDrawable(null)
        invalidate()
    }

    fun setAcrylicBitmap(bitmap: Bitmap, alpha: Int) {
        setAcrylicDrawable(BitmapDrawable(resources, bitmap), alpha)
    }

    fun clearAcrylicMode() {
        acrylicDrawable?.callback = null
        acrylicDrawable = null
        acrylicEnabled = false
        super.setImageDrawable(null)
        invalidate()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (acrylicEnabled) {
            clearAcrylicMode()
        }
        super.setImageDrawable(drawable)
    }

    override fun onDraw(canvas: Canvas) {
        if (!acrylicEnabled) {
            super.onDraw(canvas)
            return
        }

        val drawable = acrylicDrawable ?: return
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val scale = min(
            availableWidth.toFloat() / intrinsicWidth,
            availableHeight.toFloat() / intrinsicHeight
        )
        val drawWidth = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val drawHeight = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        val left = paddingLeft + (availableWidth - drawWidth) / 2
        val top = paddingTop + (availableHeight - drawHeight) / 2

        val saveCount = canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        canvas.clipRect(0, 0, drawWidth, drawHeight)

        backgroundPaint.color = BACKGROUND_COLOR
        backgroundPaint.alpha = 255
        canvas.drawRect(0f, 0f, drawWidth.toFloat(), drawHeight.toFloat(), backgroundPaint)

        val oldAlpha = drawable.alpha
        drawable.setBounds(0, 0, drawWidth, drawHeight)
        drawable.alpha = acrylicAlpha
        drawable.draw(canvas)
        drawable.alpha = oldAlpha

        canvas.restoreToCount(saveCount)
    }

    companion object {
        private const val BACKGROUND_COLOR = 0xFF4D464A.toInt()
    }
}
