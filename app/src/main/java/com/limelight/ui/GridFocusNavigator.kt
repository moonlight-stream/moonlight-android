package com.limelight.ui

internal enum class GridFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

internal object GridFocusNavigator {
    const val CLOSE_TARGET = -1

    fun nextIndex(
        currentIndex: Int,
        itemCount: Int,
        columnCount: Int,
        direction: GridFocusDirection
    ): Int {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount) {
            return CLOSE_TARGET
        }

        val columns = columnCount.coerceAtLeast(1)
        return when (direction) {
            GridFocusDirection.UP -> {
                val target = currentIndex - columns
                if (target >= 0) target else CLOSE_TARGET
            }
            GridFocusDirection.DOWN -> {
                val target = currentIndex + columns
                if (target < itemCount) target else currentIndex
            }
            GridFocusDirection.LEFT -> {
                if (currentIndex % columns > 0) currentIndex - 1 else currentIndex
            }
            GridFocusDirection.RIGHT -> {
                val target = currentIndex + 1
                if (target < itemCount && target / columns == currentIndex / columns) {
                    target
                } else {
                    currentIndex
                }
            }
        }
    }
}
