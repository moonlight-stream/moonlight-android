package com.limelight.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.limelight.R

/**
 * Views counterpart of the controller diagnostics scrollbar.
 *
 * It uses the same 4dp track, accent thumb, and bounded thumb proportion while
 * relying on the bound ScrollView for touch, wheel, D-pad, and controller scrolling.
 */
class DialogSideScrollbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var scrollView: ScrollView? = null
    private var contentView: View? = null
    private var listening = false

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        refresh()
    }
    private val layoutChangedListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        refresh()
    }

    init {
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bindTo(scrollView: ScrollView) {
        stopListening()
        this.scrollView = scrollView
        contentView = scrollView.getChildAt(0)
        startListening()
        post(::refresh)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startListening()
    }

    override fun onDetachedFromWindow() {
        stopListening()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scroll = scrollView ?: return
        val content = contentView ?: return
        val viewportHeight = scroll.height - scroll.paddingTop - scroll.paddingBottom
        if (viewportHeight <= 0 || content.height <= viewportHeight) return

        val contentHeight = content.height.toFloat()
        val scrollRange = (content.height - viewportHeight).coerceAtLeast(1)
        val progress = (scroll.scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
        val visibleFraction = (viewportHeight / contentHeight).coerceIn(0.18f, 0.72f)
        val radius = width / 2f

        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = ContextCompat.getColor(context, R.color.game_menu_button_border)
        paint.alpha = (255 * 0.35f).toInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)

        val thumbHeight = height * visibleFraction
        val thumbTop = (height - thumbHeight) * progress
        bounds.set(0f, thumbTop, width.toFloat(), thumbTop + thumbHeight)
        paint.color = ContextCompat.getColor(context, R.color.app_dialog_accent_color)
        paint.alpha = (255 * 0.82f).toInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private fun startListening() {
        val scroll = scrollView ?: return
        if (listening || !isAttachedToWindow) return
        scroll.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        scroll.addOnLayoutChangeListener(layoutChangedListener)
        contentView?.addOnLayoutChangeListener(layoutChangedListener)
        listening = true
    }

    private fun stopListening() {
        val scroll = scrollView
        if (!listening || scroll == null) return
        if (scroll.viewTreeObserver.isAlive) {
            scroll.viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
        }
        scroll.removeOnLayoutChangeListener(layoutChangedListener)
        contentView?.removeOnLayoutChangeListener(layoutChangedListener)
        listening = false
    }

    private fun refresh() {
        val scroll = scrollView
        val content = contentView
        if (scroll != null) {
            (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val targetHeight = (
                    scroll.height - params.topMargin - params.bottomMargin
                ).coerceAtLeast(0)
                if (params.height != targetHeight) {
                    params.height = targetHeight
                    layoutParams = params
                }
            }
        }
        val viewportHeight = if (scroll == null) 0 else {
            scroll.height - scroll.paddingTop - scroll.paddingBottom
        }
        visibility = if (content != null && content.height > viewportHeight && viewportHeight > 0) {
            VISIBLE
        } else {
            INVISIBLE
        }
        invalidate()
    }
}
