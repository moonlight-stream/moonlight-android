package com.limelight.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GridFocusNavigatorTest {
    @Test
    fun downMovesToSameColumnOnSecondRow() {
        assertEquals(2, GridFocusNavigator.nextIndex(0, 6, 2, GridFocusDirection.DOWN))
        assertEquals(3, GridFocusNavigator.nextIndex(1, 6, 2, GridFocusDirection.DOWN))
    }

    @Test
    fun upFromFirstRowMovesToCloseAction() {
        assertEquals(
            GridFocusNavigator.CLOSE_TARGET,
            GridFocusNavigator.nextIndex(1, 6, 2, GridFocusDirection.UP)
        )
    }

    @Test
    fun horizontalMovementStaysInsideCurrentRow() {
        assertEquals(1, GridFocusNavigator.nextIndex(0, 6, 2, GridFocusDirection.RIGHT))
        assertEquals(1, GridFocusNavigator.nextIndex(1, 6, 2, GridFocusDirection.RIGHT))
        assertEquals(2, GridFocusNavigator.nextIndex(2, 6, 2, GridFocusDirection.LEFT))
    }

    @Test
    fun movementStopsAtLastRow() {
        assertEquals(4, GridFocusNavigator.nextIndex(4, 5, 2, GridFocusDirection.DOWN))
        assertEquals(4, GridFocusNavigator.nextIndex(4, 5, 2, GridFocusDirection.RIGHT))
    }
}
