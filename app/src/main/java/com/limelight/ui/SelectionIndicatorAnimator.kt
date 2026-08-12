package com.limelight.ui

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.limelight.R
import com.limelight.grid.GenericGridAdapter

/**
 * Selection Indicator Animator
 * Manages the animation and position calculation of the selection indicator
 */
class SelectionIndicatorAnimator(
    private val selectionIndicator: View,
    private var recyclerView: RecyclerView?,
    private var adapter: GenericGridAdapter<*>?,
    private val rootView: View
) {

    private var positionProvider: PositionProvider? = null

    fun interface PositionProvider {
        fun getCurrentPosition(): Int
    }

    fun setPositionProvider(provider: PositionProvider) {
        this.positionProvider = provider
    }

    fun updateReferences(recyclerView: RecyclerView, adapter: GenericGridAdapter<*>) {
        this.recyclerView = recyclerView
        this.adapter = adapter
    }

    fun moveToPosition(position: Int, isFirstFocus: Boolean = false) {
        if (!isValidPosition(position)) return
        val rv = recyclerView ?: return

        val viewHolder = rv.findViewHolderForAdapterPosition(position)
        if (viewHolder != null) {
            if (isFirstFocus) {
                setIndicatorPosition(viewHolder.itemView, withAnimation = false)
            } else {
                animateToView(viewHolder.itemView)
            }
        } else {
            scrollToPositionAndAnimate(position)
        }
    }

    fun updatePosition(position: Int): Boolean {
        if (!isValidPosition(position)) return false
        val rv = recyclerView ?: return false

        val viewHolder = rv.findViewHolderForAdapterPosition(position)
        if (viewHolder != null) {
            setIndicatorPositionFast(viewHolder.itemView)
            return true
        }
        return false
    }

    fun hideIndicator() {
        selectionIndicator.visibility = View.INVISIBLE
    }

    fun showIndicator() {
        selectionIndicator.visibility = View.VISIBLE
    }

    private fun setIndicatorPositionFast(targetView: View) {
        updateCornerRadius(targetView)

        val targetLocation = IntArray(2)
        targetView.getLocationInWindow(targetLocation)

        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)

        val targetX = (targetLocation[0] - rootLocation[0]).toFloat()
        val targetY = (targetLocation[1] - rootLocation[1]).toFloat()

        selectionIndicator.translationX = targetX
        selectionIndicator.translationY = targetY
        selectionIndicator.visibility = View.VISIBLE
    }

    private fun isValidPosition(position: Int): Boolean =
        position >= 0 && (adapter?.let { position < it.count } ?: false)

    private fun animateToView(targetView: View) {
        val rv = recyclerView ?: return
        if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            rv.postDelayed({
                val viewHolder = rv.findViewHolderForAdapterPosition(getCurrentPosition())
                if (viewHolder != null) {
                    animateToView(viewHolder.itemView)
                }
            }, RETRY_DELAY.toLong())
        } else {
            setIndicatorPosition(targetView, withAnimation = true)
        }
    }

    private fun scrollToPositionAndAnimate(position: Int) {
        val rv = recyclerView ?: return
        selectionIndicator.visibility = View.INVISIBLE
        rv.smoothScrollToPosition(position)

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    recyclerView.removeOnScrollListener(this)

                    recyclerView.postDelayed({
                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (viewHolder != null) {
                            setIndicatorPosition(viewHolder.itemView, withAnimation = true)
                            addScaleAnimation()
                        }
                    }, SCROLL_WAIT_DELAY.toLong())
                }
            }
        }

        rv.addOnScrollListener(scrollListener)
    }

    private fun setIndicatorPosition(targetView: View, withAnimation: Boolean) {
        updateCornerRadius(targetView)

        val targetLocation = IntArray(2)
        targetView.getLocationInWindow(targetLocation)

        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)

        val targetX = (targetLocation[0] - rootLocation[0]).toFloat()
        val targetY = (targetLocation[1] - rootLocation[1]).toFloat()

        val targetWidth = targetView.width
        val targetHeight = targetView.height

        val params = selectionIndicator.layoutParams
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            selectionIndicator.layoutParams = params
        }

        selectionIndicator.visibility = View.VISIBLE

        if (withAnimation) {
            selectionIndicator.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(NORMAL_ANIMATION_DURATION.toLong().coerceAtMost(120))
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        } else {
            selectionIndicator.translationX = targetX
            selectionIndicator.translationY = targetY
        }
    }

    private fun addScaleAnimation() {
        selectionIndicator.scaleX = 0.8f
        selectionIndicator.scaleY = 0.8f
        selectionIndicator.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(SCALE_ANIMATION_DURATION.toLong())
            .start()
    }

    private fun updateCornerRadius(targetView: View) {
        // The indicator follows the item container, while the artwork is inset by
        // the container padding. Adding that inset keeps both corner arcs concentric.
        val artworkRadius = rootView.resources.getDimension(R.dimen.corner_radius_small)
        val outerRadius = artworkRadius + targetView.paddingLeft
        val background = selectionIndicator.background.mutate() as? LayerDrawable ?: return

        for (index in 0 until background.numberOfLayers) {
            (background.getDrawable(index) as? GradientDrawable)?.cornerRadius = outerRadius
        }
    }

    private fun getCurrentPosition(): Int = positionProvider?.getCurrentPosition() ?: -1

    companion object {
        private const val NORMAL_ANIMATION_DURATION = 200
        private const val SCALE_ANIMATION_DURATION = 150
        private const val SCROLL_WAIT_DELAY = 50
        private const val RETRY_DELAY = 100
    }
}
