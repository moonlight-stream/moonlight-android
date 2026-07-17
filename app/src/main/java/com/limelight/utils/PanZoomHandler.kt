package com.limelight.utils

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

import com.limelight.Game
import com.limelight.preferences.PreferenceConfiguration

class PanZoomHandler(
    context: Context,
    private val game: Game,
    private val streamView: View,
    private val cursorOverlay: View,
    private val prefConfig: PreferenceConfiguration
) {
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var parent: View? = null
    private var scaleFactor = 1.0f
    private var childX = 0f
    private var childY = 0f
    private var parentWidth = 0f
    private var parentHeight = 0f
    private var childWidth = 0f
    private var childHeight = 0f

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())

        // Everything gets easier with 0,0 as the pivot point
        streamView.pivotX = 0f
        streamView.pivotY = 0f
        cursorOverlay.pivotX = 0f
        cursorOverlay.pivotY = 0f
    }

    fun handleTouchEvent(motionEvent: MotionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent)
        gestureDetector.onTouchEvent(motionEvent)
    }

    private fun updateDimensions() {
        childHeight = streamView.height * scaleFactor
        childWidth = streamView.width * scaleFactor
        parentWidth = parent!!.width.toFloat()
        parentHeight = parent!!.height.toFloat()
    }

    private fun constrainToBounds() {
        updateDimensions()

        if (parentWidth >= childWidth) {
            childX = (parentWidth - childWidth) / 2
        } else {
            val boundaryX = parentWidth - childWidth
            childX = maxOf(boundaryX, minOf(childX, 0f))
        }

        if (parentHeight >= childHeight) {
            childY = (parentHeight - childHeight) / 2
        } else {
            val boundaryY = parentHeight - childHeight
            childY = maxOf(boundaryY, minOf(childY, 0f))
        }

        applyTransform()
    }

    private fun applyTransform() {
        streamView.scaleX = scaleFactor
        streamView.scaleY = scaleFactor
        streamView.x = childX
        streamView.y = childY

        cursorOverlay.scaleX = scaleFactor
        cursorOverlay.scaleY = scaleFactor
        cursorOverlay.x = childX
        cursorOverlay.y = childY
    }

    fun handleSurfaceChange() {
        if (childWidth == 0f || parent == null) {
            // Retrieve parent, should handle both built-in display and external display
            parent = streamView.parent as? View
            return
        }

        val prevChildWidth = childWidth
        val prevChildHeight = childHeight
        val prevParentWidth = parentWidth
        val prevParentHeight = parentHeight

        updateDimensions()

        val viewScaleX = childWidth / prevChildWidth
        val viewScaleY = childHeight / prevChildHeight

        val dPivotX1 = childX - prevParentWidth / 2
        val dPivotY1 = childY - prevParentHeight / 2

        val dPivotX2 = dPivotX1 * viewScaleX
        val dPivotY2 = dPivotY1 * viewScaleY

        childX = dPivotX2 + parentWidth / 2
        childY = dPivotY2 + parentHeight / 2

        constrainToBounds()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var newScaleFactor = scaleFactor * detector.scaleFactor
            newScaleFactor = maxOf(1f, minOf(newScaleFactor, MAX_SCALE)) // Apply minimum scale

            // Calculate pivot point
            val focusX = detector.focusX
            val focusY = detector.focusY

            val dPivotX = (childX - focusX) / scaleFactor * newScaleFactor
            val dPivotY = (childY - focusY) / scaleFactor * newScaleFactor

            childX = focusX + dPivotX
            childY = focusY + dPivotY

            scaleFactor = newScaleFactor

            constrainToBounds()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            game.updatePipAutoEnter()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            childX = streamView.x - distanceX
            childY = streamView.y - distanceY

            constrainToBounds()
            return true
        }
    }

    companion object {
        private const val MAX_SCALE = 10.0f
    }
}
