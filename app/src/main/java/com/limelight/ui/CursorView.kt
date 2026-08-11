package com.limelight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.limelight.R

class CursorView : View {

    // 网络接收到的光标
    private var cursorBitmap: Bitmap? = null
    private var pivotX = 0f
    private var pivotY = 0f

    // === 兜底方案 (默认光标) ===
    private var defaultCursorBitmap: Bitmap? = null
    private var defaultPivotX = 0f
    private var defaultPivotY = 0f

    // 状态
    private var cursorX = -100f
    private var cursorY = -100f
    private var inputWantsCursor = false
    private var hostCursorVisible = false
    private val paint = Paint()

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    private fun init(context: Context) {
        elevation = 100f
        setWillNotDraw(false)

        // === 加载本地 SVG 作为兜底 ===
        val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.arrow)
        if (vectorDrawable != null) {
            // 将 VectorDrawable 转为 Bitmap
            defaultCursorBitmap = Bitmap.createBitmap(DEFAULT_SIZE, DEFAULT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(defaultCursorBitmap!!)
            vectorDrawable.setBounds(0, 0, DEFAULT_SIZE, DEFAULT_SIZE)
            vectorDrawable.draw(canvas)

            // 设置默认热点 (箭头尖端: 6/24, 3/24)
            defaultPivotX = DEFAULT_SIZE * (6f / 24f)
            defaultPivotY = DEFAULT_SIZE * (3f / 24f)
        }
    }

    /**
     * 设置网络光标 (收到 UDP 包时调用)
     */
    fun setCursorBitmap(bitmap: Bitmap?, hotX: Int, hotY: Int) {
        this.cursorBitmap = bitmap
        this.pivotX = hotX.toFloat()
        this.pivotY = hotY.toFloat()
        invalidate()
    }

    /**
     * 重置为默认光标 (断连或初始化时调用)
     */
    fun resetToDefault() {
        this.cursorBitmap = null // 清空网络图片，触发 onDraw 里的回退逻辑
        invalidate()
    }

    fun updateCursorPosition(x: Float, y: Float) {
        this.cursorX = x
        this.cursorY = y
        invalidate()
    }

    fun show() {
        inputWantsCursor = true
        updateVisibility()
    }

    fun hide() {
        inputWantsCursor = false
        updateVisibility()
    }

    fun setHostCursorVisible(visible: Boolean) {
        hostCursorVisible = visible
        updateVisibility()
        invalidate()
    }

    private fun updateVisibility() {
        visibility = if (inputWantsCursor && hostCursorVisible) VISIBLE else GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!inputWantsCursor || !hostCursorVisible) return

        val bmpToDraw: Bitmap?
        val pX: Float
        val pY: Float

        // === 核心逻辑：优先用网络图，没有就用兜底图 ===
        if (cursorBitmap != null) {
            bmpToDraw = cursorBitmap
            pX = pivotX
            pY = pivotY
        } else {
            // 兜底方案
            bmpToDraw = defaultCursorBitmap
            pX = defaultPivotX
            pY = defaultPivotY
        }

        if (bmpToDraw != null) {
            val left = cursorX - pX
            val top = cursorY - pY
            canvas.drawBitmap(bmpToDraw, left, top, paint)
        }
    }

    companion object {
        // 默认光标大小 (像素)
        private const val DEFAULT_SIZE = 24
    }
}
