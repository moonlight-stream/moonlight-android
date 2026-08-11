package com.limelight.binding.input.touch

import android.os.Handler
import android.os.Looper
import com.limelight.ui.CursorView

class LocalCursorRenderer(
    private var cursorView: CursorView?,
    viewWidth: Int,
    viewHeight: Int
) {
    private var viewWidth = maxOf(1, viewWidth)
    private var viewHeight = maxOf(1, viewHeight)
    private var cursorX = this.viewWidth / 2f
    private var cursorY = this.viewHeight / 2f
    private val uiHandler = Handler(Looper.getMainLooper())

    init {
        postPosition()
    }

    fun updateCursorPosition(deltaX: Float, deltaY: Float) {
        cursorX = (cursorX + deltaX).coerceIn(0f, (viewWidth - 1).toFloat())
        cursorY = (cursorY + deltaY).coerceIn(0f, (viewHeight - 1).toFloat())
        postPosition()
    }

    fun setViewDimensions(width: Int, height: Int) {
        val newWidth = maxOf(1, width)
        val newHeight = maxOf(1, height)

        cursorX = if (viewWidth > 1) {
            cursorX * (newWidth - 1) / (viewWidth - 1)
        } else {
            newWidth / 2f
        }
        cursorY = if (viewHeight > 1) {
            cursorY * (newHeight - 1) / (viewHeight - 1)
        } else {
            newHeight / 2f
        }

        viewWidth = newWidth
        viewHeight = newHeight
        cursorX = cursorX.coerceIn(0f, (viewWidth - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (viewHeight - 1).toFloat())
        postPosition()
    }

    fun show() {
        uiHandler.post {
            cursorView?.let {
                it.show()
                it.updateCursorPosition(cursorX, cursorY)
            }
        }
    }

    fun hide() {
        uiHandler.post { cursorView?.hide() }
    }

    fun destroy() {
        val view = cursorView
        cursorView = null
        uiHandler.post { view?.hide() }
    }

    fun getCursorAbsolutePosition(): FloatArray = floatArrayOf(cursorX, cursorY)

    private fun postPosition() {
        uiHandler.post { cursorView?.updateCursorPosition(cursorX, cursorY) }
    }
}
